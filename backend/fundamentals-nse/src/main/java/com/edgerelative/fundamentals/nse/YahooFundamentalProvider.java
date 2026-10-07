package com.edgerelative.fundamentals.nse;

import com.edgerelative.fundamentals.api.error.FundamentalAuthenticationException;
import com.edgerelative.fundamentals.api.error.FundamentalException;
import com.edgerelative.fundamentals.api.error.FundamentalNotFoundException;
import com.edgerelative.fundamentals.api.error.FundamentalProtocolException;
import com.edgerelative.fundamentals.api.error.FundamentalUnavailableException;
import com.edgerelative.fundamentals.api.model.Filing;
import com.edgerelative.fundamentals.api.model.FinancialPeriod;
import com.edgerelative.fundamentals.api.model.FundamentalMetric;
import com.edgerelative.fundamentals.api.model.FundamentalRequest;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;
import com.edgerelative.fundamentals.api.model.PeriodType;
import com.edgerelative.fundamentals.api.model.ReportingBasis;
import com.edgerelative.fundamentals.api.model.StatementLine;
import com.edgerelative.fundamentals.api.port.FundamentalProvider;
import com.edgerelative.fundamentals.nse.config.YahooFinanceProperties;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Yahoo Finance fundamental provider for NSE/BSE tickers ({@code .NS}/{@code .BO}).
 *
 * <p>Yahoo is an <em>observation-time</em> source: it returns current values and does not expose a
 * filing timestamp. To stay point-in-time correct, the snapshot records the request {@code asOf}
 * instant as the filing/observation time, so the system never claims it knew a value earlier than it
 * observed it. NSE/BSE filings/XBRL will later refine the true filing timestamp behind the same port.
 *
 * <p>Yahoo gates quote-summary behind a cookie + crumb and aggressively rate-limits non-browser
 * clients. This adapter sends a browser User-Agent, caches the cookie/crumb session, spaces out
 * requests, falls back to {@code query2}, and maps HTTP 429 to a clear "unavailable" error.
 */
public class YahooFundamentalProvider implements FundamentalProvider {

    static final String PROVIDER = "yahoo-nse";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String YAHOO = "Yahoo";

    private final YahooFinanceProperties properties;
    private final HttpClient httpClient;
    private final JsonMapper mapper;

    private final Object crumbLock = new Object();
    private final Object throttleLock = new Object();
    private volatile String cachedCrumb;
    private volatile Instant crumbFetchedAt;
    private long lastRequestAtMillis;

    public YahooFundamentalProvider(
            YahooFinanceProperties properties, HttpClient httpClient, JsonMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public FundamentalSnapshot fetch(FundamentalRequest request) {
        String yahooSymbol = yahooSymbol(request);
        JsonNode result = quoteSummaryWithCrumb(yahooSymbol);
        return map(request, yahooSymbol, result);
    }

    private String yahooSymbol(FundamentalRequest request) {
        String symbol = request.symbol().trim().toUpperCase();
        boolean bse = "BSE".equalsIgnoreCase(request.exchange());
        return symbol + (bse ? properties.getSuffixBse() : properties.getSuffixNse());
    }

    private JsonNode quoteSummaryWithCrumb(String yahooSymbol) {
        String crumb = crumb();
        try {
            return quoteSummary(yahooSymbol, crumb);
        } catch (FundamentalAuthenticationException e) {
            // The crumb/cookie session likely expired; refresh once and retry.
            invalidateCrumb();
            return quoteSummary(yahooSymbol, crumb());
        }
    }

    /** Returns a cached crumb when fresh, otherwise fetches one under a single-flight lock. */
    private String crumb() {
        if (isCrumbFresh()) {
            return cachedCrumb;
        }
        synchronized (crumbLock) {
            if (isCrumbFresh()) {
                return cachedCrumb;
            }
            String fetched = fetchCrumb();
            cachedCrumb = fetched;
            crumbFetchedAt = Instant.now();
            return fetched;
        }
    }

    private boolean isCrumbFresh() {
        Instant fetchedAt = crumbFetchedAt;
        return cachedCrumb != null
                && fetchedAt != null
                && fetchedAt.plus(properties.getCrumbTtl()).isAfter(Instant.now());
    }

    private void invalidateCrumb() {
        synchronized (crumbLock) {
            cachedCrumb = null;
            crumbFetchedAt = null;
        }
    }

    private String fetchCrumb() {
        // Prime the cookie jar; fc.yahoo.com returns 404 but sets the session cookies. A transport
        // failure here is tolerated: the crumb request below is the authority on usability.
        try {
            send(builder(URI.create(properties.getCookieBaseUrl() + "/")).GET().build());
        } catch (FundamentalException ignored) {
            // continue
        }
        HttpResponse<String> response = getWithFallback(properties.getCrumbPath());
        if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
            throw mapHttpError(response.statusCode(), "crumb", properties.getCrumbPath());
        }
        return response.body().trim();
    }

    private JsonNode quoteSummary(String yahooSymbol, String crumb) {
        String encoded = URLEncoder.encode(yahooSymbol, StandardCharsets.UTF_8);
        String query = "?modules=" + properties.getModules() + "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);
        String path = properties.getQuoteSummaryPath() + encoded + query;
        HttpResponse<String> response = getWithFallback(path);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw mapHttpError(response.statusCode(), "quoteSummary", properties.getQuoteSummaryPath());
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode result = root.path("quoteSummary").path("result");
            if (!result.isArray() || result.isEmpty()) {
                throw new FundamentalNotFoundException(
                        "Yahoo has no fundamentals for " + yahooSymbol,
                        PROVIDER,
                        "quoteSummary",
                        properties.getQuoteSummaryPath(),
                        null);
            }
            return result.path(0);
        } catch (FundamentalException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FundamentalProtocolException(
                    "Malformed Yahoo quoteSummary response", PROVIDER, "quoteSummary", properties.getQuoteSummaryPath(), e);
        }
    }

    private FundamentalSnapshot map(FundamentalRequest request, String yahooSymbol, JsonNode result) {
        JsonNode financial = result.path("financialData");
        JsonNode keyStats = result.path("defaultKeyStatistics");
        JsonNode summary = result.path("summaryDetail");

        LocalDate periodEnd = periodEnd(request, result);
        FinancialPeriod period = new FinancialPeriod(
                fiscalYearLabel(periodEnd),
                0,
                PeriodType.ANNUAL,
                request.basis() == null ? ReportingBasis.CONSOLIDATED : request.basis(),
                periodEnd);

        List<StatementLine> statements = statements(result);
        List<FundamentalMetric> metrics = metrics(financial, keyStats, summary);

        String revision = request.asOf().toString();
        Filing filing = new Filing(request.asOf(), YAHOO, yahooSymbol, revision);
        return new FundamentalSnapshot(
                request.exchange(),
                request.symbol(),
                PROVIDER,
                revision,
                request.asOf(),
                filing,
                period,
                statements,
                metrics);
    }

    private LocalDate periodEnd(FundamentalRequest request, JsonNode result) {
        JsonNode endDate = result
                .path("incomeStatementHistory")
                .path("incomeStatementHistory")
                .path(0)
                .path("endDate")
                .path("raw");
        if (!endDate.isMissingNode() && !endDate.isNull() && endDate.isNumber()) {
            return LocalDate.ofInstant(Instant.ofEpochSecond(endDate.asLong()), IST);
        }
        return LocalDate.ofInstant(request.asOf(), IST);
    }

    private static String fiscalYearLabel(LocalDate periodEnd) {
        int fiscalYear = periodEnd.getMonthValue() >= 4 ? periodEnd.getYear() + 1 : periodEnd.getYear();
        return "FY" + fiscalYear;
    }

    private List<StatementLine> statements(JsonNode result) {
        JsonNode latest = result
                .path("incomeStatementHistory")
                .path("incomeStatementHistory")
                .path(0);
        List<StatementLine> lines = new ArrayList<>();
        addLine(lines, latest, "total_revenue", "totalRevenue", "Total revenue");
        addLine(lines, latest, "gross_profit", "grossProfit", "Gross profit");
        addLine(lines, latest, "operating_income", "operatingIncome", "Operating income");
        addLine(lines, latest, "net_income", "netIncome", "Net income");
        return lines;
    }

    private void addLine(List<StatementLine> lines, JsonNode node, String code, String field, String label) {
        BigDecimal value = raw(node, field);
        if (value != null) {
            lines.add(new StatementLine(code, label, value, "INR", "unit"));
        }
    }

    private List<FundamentalMetric> metrics(JsonNode financial, JsonNode keyStats, JsonNode summary) {
        List<FundamentalMetric> metrics = new ArrayList<>();
        addMetric(metrics, summary, "trailingPE", "trailing_pe", "ratio");
        addMetric(metrics, summary, "dividendYield", "dividend_yield", "ratio");
        addMetric(metrics, summary, "marketCap", "market_cap", "INR");
        addMetric(metrics, keyStats, "priceToBook", "price_to_book", "ratio");
        addMetric(metrics, keyStats, "forwardPE", "forward_pe", "ratio");
        addMetric(metrics, keyStats, "trailingEps", "trailing_eps", "INR");
        addMetric(metrics, financial, "returnOnEquity", "return_on_equity", "ratio");
        addMetric(metrics, financial, "debtToEquity", "debt_to_equity", "ratio");
        addMetric(metrics, financial, "revenueGrowth", "revenue_growth", "ratio");
        addMetric(metrics, financial, "earningsGrowth", "earnings_growth", "ratio");
        addMetric(metrics, financial, "profitMargins", "profit_margin", "ratio");
        addMetric(metrics, financial, "targetMeanPrice", "target_mean_price", "INR");
        return metrics;
    }

    private void addMetric(List<FundamentalMetric> metrics, JsonNode node, String field, String code, String unit) {
        BigDecimal value = raw(node, field);
        if (value != null) {
            metrics.add(new FundamentalMetric(code, value, unit, null));
        }
    }

    private static BigDecimal raw(JsonNode node, String field) {
        JsonNode raw = node.path(field).path("raw");
        if (raw.isMissingNode() || raw.isNull() || !raw.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(raw.asDouble());
    }

    private FundamentalException mapHttpError(int status, String operation, String endpoint) {
        if (status == 401 || status == 403) {
            return new FundamentalAuthenticationException(
                    "Yahoo rejected the request", PROVIDER, operation, endpoint, null);
        }
        if (status == 404) {
            return new FundamentalNotFoundException(
                    "Yahoo has no such instrument", PROVIDER, operation, endpoint, null);
        }
        if (status == 429) {
            return new FundamentalUnavailableException(
                    "Yahoo rate-limited the request; try again later",
                    PROVIDER,
                    operation,
                    endpoint,
                    429,
                    null);
        }
        return new FundamentalUnavailableException(
                "Yahoo is unavailable", PROVIDER, operation, endpoint, status, null);
    }

    private HttpRequest.Builder builder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .header("User-Agent", properties.getUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(properties.getRequestTimeout());
    }

    /** Try the primary host, then the fallback host on HTTP 429. */
    private HttpResponse<String> getWithFallback(String path) {
        HttpResponse<String> response = send(builder(URI.create(properties.getQueryBaseUrl() + path)).GET().build());
        String fallback = properties.getFallbackQueryBaseUrl();
        if (response.statusCode() == 429 && fallback != null && !fallback.isBlank()) {
            return send(builder(URI.create(fallback + path)).GET().build());
        }
        return response;
    }

    private HttpResponse<String> send(HttpRequest request) {
        throttle();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new FundamentalUnavailableException(
                    "Yahoo request timed out", PROVIDER, "http", request.uri().getPath(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FundamentalUnavailableException(
                    "Yahoo request interrupted", PROVIDER, "http", request.uri().getPath(), null, e);
        } catch (IOException e) {
            throw new FundamentalUnavailableException(
                    "Yahoo request failed", PROVIDER, "http", request.uri().getPath(), null, e);
        }
    }

    /** Enforce a minimum spacing between outbound requests to stay polite to Yahoo. */
    private void throttle() {
        long min = properties.getMinRequestInterval() == null ? 0 : properties.getMinRequestInterval().toMillis();
        if (min <= 0) {
            return;
        }
        synchronized (throttleLock) {
            long wait = lastRequestAtMillis + min - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAtMillis = System.currentTimeMillis();
        }
    }
}

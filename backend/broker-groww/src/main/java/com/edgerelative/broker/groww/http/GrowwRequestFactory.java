package com.edgerelative.broker.groww.http;

import com.edgerelative.broker.groww.config.GrowwProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpRequest;

/**
 * Builds Groww HTTP requests. All URL, header and JSON-body construction lives here so it is never
 * duplicated across capability clients.
 */
public class GrowwRequestFactory {

    private final GrowwProperties properties;
    private final JsonMapper mapper;

    public GrowwRequestFactory(GrowwProperties properties, JsonMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public URI uri(String path, Map<String, String> query) {
        StringBuilder sb = new StringBuilder(properties.getBaseUrl()).append(path);
        if (query != null && !query.isEmpty()) {
            sb.append('?');
            sb.append(query.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&")));
        }
        return URI.create(sb.toString());
    }

    public HttpRequest get(String path, Map<String, String> query, String bearerToken) {
        return base(uri(path, query), bearerToken).GET().build();
    }

    public HttpRequest getAbsolute(URI uri) {
        return HttpRequest.newBuilder(uri)
                .header("Accept", "text/csv, text/plain, */*")
                .timeout(properties.getRequestTimeout())
                .GET()
                .build();
    }

    public HttpRequest postJson(String path, Map<String, String> query, Object body, String bearerToken) {
        return base(uri(path, query), bearerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
    }

    public HttpRequest putJson(String path, Map<String, String> query, Object body, String bearerToken) {
        return base(uri(path, query), bearerToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
    }

    /** Token generation uses the API key as the bearer and does not send X-API-VERSION. */
    public HttpRequest authRequest(Object body, String apiKey) {
        return HttpRequest.newBuilder(uri("/v1/token/api/access", Map.of()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-API-VERSION", properties.getApiVersion())
                .header("Authorization", "Bearer " + apiKey)
                .timeout(properties.getRequestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
    }

    private HttpRequest.Builder base(URI uri, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("X-API-VERSION", properties.getApiVersion())
                .timeout(properties.getRequestTimeout());
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return builder;
    }

    private String toJson(Object body) {
        return mapper.writeValueAsString(body);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

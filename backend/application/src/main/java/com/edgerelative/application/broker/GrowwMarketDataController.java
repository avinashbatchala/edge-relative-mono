package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerLtp;
import com.edgerelative.broker.api.model.BrokerOhlc;
import com.edgerelative.broker.api.model.BrokerOptionChain;
import com.edgerelative.broker.api.model.BrokerOptionGreeks;
import com.edgerelative.broker.api.model.BrokerQuote;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.port.MarketDataBroker;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Groww market data.
 */
@RestController
@RequestMapping("/api/v1/brokers/groww/market-data")
public class GrowwMarketDataController {

    private final MarketDataBroker marketData;

    public GrowwMarketDataController(MarketDataBroker marketData) {
        this.marketData = marketData;
    }

    @GetMapping("/quote")
    public BrokerQuote quote(
            @RequestParam BrokerExchange exchange,
            @RequestParam BrokerSegment segment,
            @RequestParam String tradingSymbol) {
        return marketData.quote(exchange, segment, tradingSymbol);
    }

    @GetMapping("/ltp")
    public List<BrokerLtp> ltp(
            @RequestParam BrokerSegment segment, @RequestParam List<String> exchangeSymbols) {
        return marketData.lastTradedPrices(segment, exchangeSymbols);
    }

    @GetMapping("/ohlc")
    public Map<String, BrokerOhlc> ohlc(
            @RequestParam BrokerSegment segment, @RequestParam List<String> exchangeSymbols) {
        return marketData.ohlc(segment, exchangeSymbols);
    }

    @GetMapping("/option-chain")
    public BrokerOptionChain optionChain(
            @RequestParam BrokerExchange exchange,
            @RequestParam String underlying,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiry) {
        return marketData.optionChain(exchange, underlying, expiry);
    }

    @GetMapping("/greeks")
    public BrokerOptionGreeks greeks(
            @RequestParam BrokerExchange exchange,
            @RequestParam String underlying,
            @RequestParam String tradingSymbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiry) {
        return marketData.greeks(exchange, underlying, tradingSymbol, expiry);
    }
}

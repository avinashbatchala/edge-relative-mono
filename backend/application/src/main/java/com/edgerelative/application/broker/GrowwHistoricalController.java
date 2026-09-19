package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerCandleInterval;
import com.edgerelative.broker.api.model.BrokerCandleSeries;
import com.edgerelative.broker.api.model.BrokerContract;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerExpiry;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.HistoricalCandleRequest;
import com.edgerelative.broker.api.port.HistoricalDataBroker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only Groww historical data. */
@RestController
@RequestMapping("/api/v1/brokers/groww/historical")
public class GrowwHistoricalController {

    private final HistoricalDataBroker historicalData;

    public GrowwHistoricalController(HistoricalDataBroker historicalData) {
        this.historicalData = historicalData;
    }

    @GetMapping("/candles")
    public BrokerCandleSeries candles(
            @RequestParam BrokerExchange exchange,
            @RequestParam BrokerSegment segment,
            @RequestParam String growwSymbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam BrokerCandleInterval interval) {
        return historicalData.candles(
                new HistoricalCandleRequest(exchange, segment, growwSymbol, start, end, interval));
    }

    @GetMapping("/expiries")
    public List<BrokerExpiry> expiries(
            @RequestParam BrokerExchange exchange,
            @RequestParam String underlying,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return historicalData.expiries(exchange, underlying, year, month);
    }

    @GetMapping("/contracts")
    public List<BrokerContract> contracts(
            @RequestParam BrokerExchange exchange,
            @RequestParam String underlying,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        return historicalData.contracts(exchange, underlying, expiryDate);
    }
}

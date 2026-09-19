package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerCandleSeries;
import com.edgerelative.broker.api.model.BrokerContract;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerExpiry;
import com.edgerelative.broker.api.model.HistoricalCandleRequest;
import java.time.LocalDate;
import java.util.List;

/** Historical candles and derivative reference data. */
public interface HistoricalDataBroker {

    /** Returns a deterministic, ascending-by-time series, splitting ranges when the broker requires it. */
    BrokerCandleSeries candles(HistoricalCandleRequest request);

    List<BrokerExpiry> expiries(BrokerExchange exchange, String underlyingSymbol, Integer year, Integer month);

    List<BrokerContract> contracts(BrokerExchange exchange, String underlyingSymbol, LocalDate expiryDate);
}

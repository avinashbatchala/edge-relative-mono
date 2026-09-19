package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerLtp;
import com.edgerelative.broker.api.model.BrokerOhlc;
import com.edgerelative.broker.api.model.BrokerOptionChain;
import com.edgerelative.broker.api.model.BrokerOptionGreeks;
import com.edgerelative.broker.api.model.BrokerQuote;
import com.edgerelative.broker.api.model.BrokerSegment;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Live (snapshot) market data. */
public interface MarketDataBroker {

    BrokerQuote quote(BrokerExchange exchange, BrokerSegment segment, String tradingSymbol);

    List<BrokerLtp> lastTradedPrices(BrokerSegment segment, List<String> exchangeSymbols);

    Map<String, BrokerOhlc> ohlc(BrokerSegment segment, List<String> exchangeSymbols);

    BrokerOptionChain optionChain(BrokerExchange exchange, String underlying, LocalDate expiryDate);

    BrokerOptionGreeks greeks(
            BrokerExchange exchange, String underlying, String tradingSymbol, LocalDate expiryDate);
}

package com.edgerelative.broker.groww.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import com.edgerelative.broker.groww.support.GrowwPropertiesBuilder;
import com.edgerelative.broker.groww.support.MutableClock;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GrowwInstrumentClientTest {

    private static final String CSV =
            "exchange,exchange_token,trading_symbol,groww_symbol,name,instrument_type,segment,series,isin,"
                    + "underlying_symbol,underlying_exchange_token,lot_size,expiry_date,strike_price,tick_size,"
                    + "freeze_quantity,is_reserved,buy_allowed,sell_allowed\n"
                    + "NSE,2885,RELIANCE,NSE-RELIANCE,Reliance,EQ,CASH,EQ,INE002A01018,,,1,,,0.05,,false,true,true";

    private final GrowwCallExecutor callExecutor = mock(GrowwCallExecutor.class);
    private final GrowwHttpClient http = mock(GrowwHttpClient.class);
    private final GrowwRequestFactory requests = mock(GrowwRequestFactory.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void cachesTheMasterUntilTheTtlExpires() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.setInstrumentMasterCacheTtl(Duration.ofMinutes(30));

        when(requests.getAbsolute(any(URI.class)))
                .thenReturn(HttpRequest.newBuilder(URI.create("https://example.test/instruments.csv"))
                        .GET()
                        .build());
        when(http.getText(any(HttpRequest.class), any(GrowwOperation.class))).thenReturn(CSV);
        when(callExecutor.execute(any(GrowwOperation.class), any(GrowwCallPriority.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());

        GrowwInstrumentClient client = new GrowwInstrumentClient(
                properties,
                callExecutor,
                http,
                requests,
                new GrowwInstrumentCsvParser(new GrowwMapper(JsonMapper.builder().build())),
                clock);

        List<BrokerInstrument> first = client.downloadInstrumentMaster();
        List<BrokerInstrument> second = client.downloadInstrumentMaster();
        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first);
        verify(http, times(1)).getText(any(HttpRequest.class), any(GrowwOperation.class));

        // Within the TTL the cache serves without touching Groww.
        clock.advance(Duration.ofMinutes(29));
        client.downloadInstrumentMaster();
        verify(http, times(1)).getText(any(HttpRequest.class), any(GrowwOperation.class));

        // Past the TTL it refreshes.
        clock.advance(Duration.ofMinutes(2));
        client.downloadInstrumentMaster();
        verify(http, times(2)).getText(any(HttpRequest.class), any(GrowwOperation.class));
    }
}

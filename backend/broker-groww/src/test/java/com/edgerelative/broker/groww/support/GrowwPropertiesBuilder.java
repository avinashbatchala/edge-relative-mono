package com.edgerelative.broker.groww.support;

import com.edgerelative.broker.groww.config.GrowwProperties;

import java.time.Duration;
import java.util.List;

/**
 * Builds validated test configuration without Spring binding.
 */
public final class GrowwPropertiesBuilder {

    private GrowwPropertiesBuilder() {
    }

    public static GrowwProperties defaults() {
        GrowwProperties properties = new GrowwProperties();
        properties.setBaseUrl("https://api.groww.in");
        properties.setInstrumentMasterUrl("https://growwapi-assets.groww.in/instruments/instrument.csv");
        properties.setApiVersion("1.0");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setOperationTimeout(Duration.ofSeconds(30));
        properties.setMaxInFlight(64);
        properties.setBulkMaxConcurrency(4);
        properties.getCredentials().setMode(GrowwProperties.AuthMode.ACCESS_TOKEN);
        properties.getCredentials().setAccessToken("test-token");
        return properties;
    }

    public static GrowwProperties withRateLimits(
            List<GrowwProperties.Window> authentication,
            List<GrowwProperties.Window> orders,
            List<GrowwProperties.Window> liveData,
            List<GrowwProperties.Window> nonTrading) {
        GrowwProperties properties = defaults();
        properties.getRateLimits().setAuthentication(authentication);
        properties.getRateLimits().setOrders(orders);
        properties.getRateLimits().setLiveData(liveData);
        properties.getRateLimits().setNonTrading(nonTrading);
        return properties;
    }
}

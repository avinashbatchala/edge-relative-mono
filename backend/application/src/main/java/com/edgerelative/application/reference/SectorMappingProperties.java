package com.edgerelative.application.reference;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Point-in-time sector membership for canonical instruments: {@code market.sector-map.<SYMBOL> = <SECTOR_CODE>}.
 *
 * <p>Only sectors with a verified NSE index benchmark should appear here. Instruments with no
 * applicable NSE sector index stay unmapped so sector context is reported unavailable rather than
 * guessed.
 */
@ConfigurationProperties(prefix = "market")
public class SectorMappingProperties {

    private Map<String, String> sectorMap = new LinkedHashMap<>();

    public Map<String, String> getSectorMap() {
        return sectorMap;
    }

    public void setSectorMap(Map<String, String> sectorMap) {
        this.sectorMap = sectorMap;
    }
}

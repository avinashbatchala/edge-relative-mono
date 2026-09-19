package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * {@code GET /v1/historical/candles} payload.
 *
 * <p>Each candle is an ordered array: timestamp, open, high, low, close, volume, open interest
 * (FNO only). The client validates element count and parseability explicitly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwCandleRangeResponse(
        @JsonProperty("candles") List<List<JsonNode>> candles,
        @JsonProperty("closing_price") JsonNode closingPrice,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        @JsonProperty("interval_in_minutes") Integer intervalInMinutes) {
}

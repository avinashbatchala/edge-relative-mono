package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** Available account margin across cash and F&O. All amounts in rupees. */
public record BrokerMargin(
        BigDecimal clearCash,
        BigDecimal netMarginUsed,
        BigDecimal brokerageAndCharges,
        BigDecimal collateralUsed,
        BigDecimal collateralAvailable,
        BigDecimal adhocMargin,
        FnoMarginDetails fno,
        EquityMarginDetails equity) {

    public record FnoMarginDetails(
            BigDecimal netMarginUsed,
            BigDecimal spanMarginUsed,
            BigDecimal exposureMarginUsed,
            BigDecimal futureBalanceAvailable,
            BigDecimal optionBuyBalanceAvailable,
            BigDecimal optionSellBalanceAvailable) {
    }

    public record EquityMarginDetails(
            BigDecimal netEquityMarginUsed,
            BigDecimal cncMarginUsed,
            BigDecimal misMarginUsed,
            BigDecimal cncBalanceAvailable,
            BigDecimal misBalanceAvailable) {
    }
}

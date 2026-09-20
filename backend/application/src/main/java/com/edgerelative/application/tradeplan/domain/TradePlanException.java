package com.edgerelative.application.tradeplan.domain;

/** Domain failure while creating a trade plan from a risk decision. */
public class TradePlanException extends RuntimeException {

    public static final String INELIGIBLE_DECISION = "INELIGIBLE_DECISION";
    public static final String INVALID_GEOMETRY = "INVALID_GEOMETRY";
    public static final String CEILING_EXCEEDED = "CEILING_EXCEEDED";
    public static final String PLAN_INPUTS_UNAVAILABLE = "PLAN_INPUTS_UNAVAILABLE";

    private final String code;

    public TradePlanException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

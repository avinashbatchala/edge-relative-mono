package com.edgerelative.application.tradeplan.application;

import com.edgerelative.application.tradeplan.domain.TradePlanPolicy;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Versioned trade-plan validity policy. Every bound is optional: leaving one unset creates a plan
 * without inventing a value (no universal TTL). Values must be promoted from research before live use.
 */
@ConfigurationProperties(prefix = "strategy.trade-plan")
public class TradePlanProperties {

    private String code = "ER_TRADE_PLAN_V1";
    private int version = 1;
    private String reference;
    private Integer validityMinutes;
    private Integer entryCutoffMinutesBeforeClose;
    private BigDecimal noChaseTicks;
    private String entryMethod;
    private String targetMethod;
    private BigDecimal targetR;
    private String stopBufferMethod;

    public TradePlanPolicy toPolicy() {
        return new TradePlanPolicy(
                code, version, reference, validityMinutes, entryCutoffMinutesBeforeClose, noChaseTicks,
                entryMethod, targetMethod, targetR, stopBufferMethod);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Integer getValidityMinutes() {
        return validityMinutes;
    }

    public void setValidityMinutes(Integer validityMinutes) {
        this.validityMinutes = validityMinutes;
    }

    public Integer getEntryCutoffMinutesBeforeClose() {
        return entryCutoffMinutesBeforeClose;
    }

    public void setEntryCutoffMinutesBeforeClose(Integer entryCutoffMinutesBeforeClose) {
        this.entryCutoffMinutesBeforeClose = entryCutoffMinutesBeforeClose;
    }

    public BigDecimal getNoChaseTicks() {
        return noChaseTicks;
    }

    public void setNoChaseTicks(BigDecimal noChaseTicks) {
        this.noChaseTicks = noChaseTicks;
    }

    public String getEntryMethod() {
        return entryMethod;
    }

    public void setEntryMethod(String entryMethod) {
        this.entryMethod = entryMethod;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    public BigDecimal getTargetR() {
        return targetR;
    }

    public void setTargetR(BigDecimal targetR) {
        this.targetR = targetR;
    }

    public String getStopBufferMethod() {
        return stopBufferMethod;
    }

    public void setStopBufferMethod(String stopBufferMethod) {
        this.stopBufferMethod = stopBufferMethod;
    }
}

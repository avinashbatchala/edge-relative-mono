package com.edgerelative.fundamentals.api.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A fiscal reporting period for an Indian company (financial year begins in April).
 *
 * @param fiscalYear Indian fiscal year label, for example {@code FY2025}
 * @param fiscalQuarter quarter 1-4 for quarterly periods, {@code 0} for annual
 * @param type annual or quarterly
 * @param basis consolidated or standalone
 * @param periodEnd the date the period ended
 */
public record FinancialPeriod(
        String fiscalYear,
        int fiscalQuarter,
        PeriodType type,
        ReportingBasis basis,
        LocalDate periodEnd) {

    public FinancialPeriod {
        if (fiscalYear == null || fiscalYear.isBlank()) {
            throw new IllegalArgumentException("fiscalYear must not be blank");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (type == PeriodType.QUARTERLY && (fiscalQuarter < 1 || fiscalQuarter > 4)) {
            throw new IllegalArgumentException("quarterly fiscalQuarter must be within [1, 4]");
        }
        if (type == PeriodType.ANNUAL && fiscalQuarter != 0) {
            throw new IllegalArgumentException("annual fiscalQuarter must be 0");
        }
    }
}

package com.edgerelative.application.broker;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.model.BrokerInstrumentType;
import com.edgerelative.broker.api.model.BrokerSegment;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InstrumentSearchTest {

    private static BrokerInstrument instrument(
            String symbol, String name, String isin, String underlying, BrokerInstrumentType type) {
        return new BrokerInstrument(
                BrokerExchange.NSE,
                "1",
                symbol,
                "NSE-" + symbol,
                name,
                type,
                BrokerSegment.CASH,
                "EQ",
                isin,
                underlying,
                null,
                1,
                null,
                null,
                null,
                null,
                false,
                true,
                true);
    }

    private static final List<BrokerInstrument> MASTER = List.of(
            instrument("RELIANCE", "Reliance Industries Ltd", "INE002A01018", null, BrokerInstrumentType.EQ),
            instrument("TCS", "Tata Consultancy Services", "INE467B01029", null, BrokerInstrumentType.EQ),
            instrument("INFY", "Infosys Ltd", "INE009A01021", null, BrokerInstrumentType.EQ),
            instrument(
                    "NIFTY25O1425100CE",
                    null,
                    null,
                    "NIFTY",
                    BrokerInstrumentType.CE));

    @Test
    void matchesSymbolNameIsinAndUnderlyingCaseInsensitively() {
        assertThat(InstrumentSearch.filter(MASTER, "reliance", null))
                .extracting(BrokerInstrument::tradingSymbol)
                .containsExactly("RELIANCE");
        assertThat(InstrumentSearch.filter(MASTER, "Infosys", null))
                .extracting(BrokerInstrument::tradingSymbol)
                .containsExactly("INFY");
        assertThat(InstrumentSearch.filter(MASTER, "ine467b", null))
                .extracting(BrokerInstrument::tradingSymbol)
                .containsExactly("TCS");
        assertThat(InstrumentSearch.filter(MASTER, "nifty", null))
                .extracting(BrokerInstrument::tradingSymbol)
                .containsExactly("NIFTY25O1425100CE");
    }

    @Test
    void boundsResultsAndCapsTheLimit() {
        List<BrokerInstrument> many = IntStream.range(0, 400)
                .mapToObj(i -> instrument("SYM" + i, "Name " + i, null, null, BrokerInstrumentType.EQ))
                .toList();

        assertThat(InstrumentSearch.filter(many, null, null)).hasSize(InstrumentSearch.DEFAULT_LIMIT);
        assertThat(InstrumentSearch.filter(many, null, 500)).hasSize(InstrumentSearch.MAX_LIMIT);
        assertThat(InstrumentSearch.filter(many, "SYM", 10)).hasSize(10);
    }

    @Test
    void blankQueryReturnsTheFirstBoundedSlice() {
        assertThat(InstrumentSearch.filter(MASTER, "  ", 2))
                .extracting(BrokerInstrument::tradingSymbol)
                .containsExactly("RELIANCE", "TCS");
    }
}

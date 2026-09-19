package com.edgerelative.broker.groww.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.model.BrokerInstrumentType;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GrowwInstrumentCsvParserTest {

    private static final String HEADER =
            "exchange,exchange_token,trading_symbol,groww_symbol,name,instrument_type,segment,series,isin,"
                    + "underlying_symbol,underlying_exchange_token,lot_size,expiry_date,strike_price,tick_size,"
                    + "freeze_quantity,is_reserved,buy_allowed,sell_allowed";

    private final GrowwInstrumentCsvParser parser =
            new GrowwInstrumentCsvParser(new GrowwMapper(JsonMapper.builder().build()));

    @Test
    void parsesEquityAndDerivativeRowsIncludingNaN() {
        String csv = HEADER + "\n"
                + "NSE,2885,RELIANCE,NSE-RELIANCE,Reliance Industries,EQ,CASH,EQ,INE002A01018,,,1,,,0.05,,false,true,true\n"
                + "NSE,1234,NIFTY25O1425100CE,NSE-NIFTY-14Oct25-25100-CE,NaN,CE,FNO,NaN,NaN,NIFTY,2885,50,2025-10-14,25100,0.05,1800,false,true,true";

        List<BrokerInstrument> instruments = parser.parse(csv);

        assertThat(instruments).hasSize(2);
        BrokerInstrument equity = instruments.get(0);
        assertThat(equity.exchange().name()).isEqualTo("NSE");
        assertThat(equity.tradingSymbol()).isEqualTo("RELIANCE");
        assertThat(equity.instrumentType()).isEqualTo(BrokerInstrumentType.EQ);
        assertThat(equity.segment()).isEqualTo(BrokerSegment.CASH);
        assertThat(equity.isin()).isEqualTo("INE002A01018");
        assertThat(equity.lotSize()).isEqualTo(1);
        assertThat(equity.tickSize()).isEqualByComparingTo("0.05");
        assertThat(equity.name()).isEqualTo("Reliance Industries");

        BrokerInstrument option = instruments.get(1);
        assertThat(option.instrumentType()).isEqualTo(BrokerInstrumentType.CE);
        assertThat(option.segment()).isEqualTo(BrokerSegment.FNO);
        assertThat(option.expiryDate()).isEqualTo(LocalDate.of(2025, 10, 14));
        assertThat(option.strikePrice()).isEqualByComparingTo("25100");
        assertThat(option.underlyingSymbol()).isEqualTo("NIFTY");
        assertThat(option.freezeQuantity()).isEqualTo(1800L);
    }

    @Test
    void malformedRowFailsWithLineNumber() {
        String csv = HEADER + "\n"
                + "NYSE,2885,RELIANCE,NSE-RELIANCE,Reliance,EQ,CASH,EQ,INE002A01018,,,,1,,,0.05,,false,true,true";
        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(BrokerProtocolException.class)
                .hasMessageContaining("row 2");
    }

    @Test
    void missingRequiredColumnIsRejected() {
        String csv = "exchange,foo\nNSE,bar";
        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(BrokerProtocolException.class)
                .hasMessageContaining("trading_symbol");
    }
}

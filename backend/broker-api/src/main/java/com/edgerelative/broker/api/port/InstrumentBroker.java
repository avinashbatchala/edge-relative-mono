package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerInstrument;
import java.util.List;

/** Broker instrument master download. */
public interface InstrumentBroker {

    List<BrokerInstrument> downloadInstrumentMaster();
}

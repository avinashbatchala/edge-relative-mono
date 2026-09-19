package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.port.InstrumentBroker;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.net.URI;
import java.util.List;

/** Downloads and parses the Groww instrument master. */
public class GrowwInstrumentClient implements InstrumentBroker {

    private final GrowwProperties properties;
    private final GrowwCallExecutor callExecutor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwInstrumentCsvParser parser;

    public GrowwInstrumentClient(
            GrowwProperties properties,
            GrowwCallExecutor callExecutor,
            GrowwHttpClient http,
            GrowwRequestFactory requests,
            GrowwInstrumentCsvParser parser) {
        this.properties = properties;
        this.callExecutor = callExecutor;
        this.http = http;
        this.requests = requests;
        this.parser = parser;
    }

    @Override
    public List<BrokerInstrument> downloadInstrumentMaster() {
        String csv = callExecutor.execute(
                GrowwOperation.INSTRUMENT_MASTER,
                GrowwCallPriority.BULK,
                () -> http.getText(
                        requests.getAbsolute(URI.create(properties.getInstrumentMasterUrl())),
                        GrowwOperation.INSTRUMENT_MASTER));
        return parser.parse(csv);
    }
}

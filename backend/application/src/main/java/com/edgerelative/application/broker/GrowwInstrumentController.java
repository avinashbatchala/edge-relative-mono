package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerCapabilities;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.port.BrokerAdapter;
import com.edgerelative.broker.api.port.InstrumentBroker;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Groww instrument master download and adapter capability discovery. */
@RestController
@RequestMapping("/api/v1/brokers/groww")
public class GrowwInstrumentController {

    private final InstrumentBroker instruments;
    private final BrokerAdapter brokerAdapter;

    public GrowwInstrumentController(InstrumentBroker instruments, BrokerAdapter brokerAdapter) {
        this.instruments = instruments;
        this.brokerAdapter = brokerAdapter;
    }

    @GetMapping("/instruments")
    public List<BrokerInstrument> instrumentMaster() {
        return instruments.downloadInstrumentMaster();
    }

    @GetMapping("/capabilities")
    public BrokerCapabilities capabilities() {
        return brokerAdapter.capabilities();
    }
}

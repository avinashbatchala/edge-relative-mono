package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerCapabilities;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.port.BrokerAdapter;
import com.edgerelative.broker.api.port.InstrumentBroker;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Groww instrument master download and adapter capability discovery.
 */
@RestController
@RequestMapping("/api/v1/brokers/groww")
public class GrowwInstrumentController {

    private final InstrumentBroker instruments;
    private final BrokerAdapter brokerAdapter;

    public GrowwInstrumentController(InstrumentBroker instruments, BrokerAdapter brokerAdapter) {
        this.instruments = instruments;
        this.brokerAdapter = brokerAdapter;
    }

    /**
     * Broker instrument master.
     *
     * <p>With no parameters the full master is returned (large, intended for tooling). Supplying
     * {@code query} (and optional {@code limit}, default 50, max 200) returns a bounded slice for
     * interactive search without shipping ~140k rows to the browser.
     */
    @GetMapping("/instruments")
    public List<BrokerInstrument> instrumentMaster(
            @RequestParam(required = false) String query, @RequestParam(required = false) Integer limit) {
        List<BrokerInstrument> master = instruments.downloadInstrumentMaster();
        if ((query == null || query.isBlank()) && limit == null) {
            return master;
        }
        return InstrumentSearch.filter(master, query, limit);
    }

    @GetMapping("/capabilities")
    public BrokerCapabilities capabilities() {
        return brokerAdapter.capabilities();
    }
}

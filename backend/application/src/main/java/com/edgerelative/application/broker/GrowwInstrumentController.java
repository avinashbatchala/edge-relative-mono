package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerCapabilities;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.port.BrokerAdapter;
import com.edgerelative.broker.api.port.InstrumentBroker;
import com.edgerelative.application.broker.api.BrokerApiEnums;

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
     * interactive search without shipping ~140k rows to the browser. {@code exchange} restricts the
     * slice to one exchange and {@code derivatives} (default false) controls whether futures/options
     * are included; filtering happens before the limit.
     */
    @GetMapping("/instruments")
    public List<BrokerInstrument> instrumentMaster(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) Boolean derivatives) {
        List<BrokerInstrument> master = instruments.downloadInstrumentMaster();
        if ((query == null || query.isBlank()) && limit == null && exchange == null && derivatives == null) {
            return master;
        }
        BrokerExchange exchangeFilter =
                exchange == null || exchange.isBlank() ? null : BrokerApiEnums.exchange(exchange);
        boolean includeDerivatives = derivatives != null && derivatives;
        return InstrumentSearch.filter(master, query, limit, exchangeFilter, includeDerivatives);
    }

    @GetMapping("/capabilities")
    public BrokerCapabilities capabilities() {
        return brokerAdapter.capabilities();
    }
}

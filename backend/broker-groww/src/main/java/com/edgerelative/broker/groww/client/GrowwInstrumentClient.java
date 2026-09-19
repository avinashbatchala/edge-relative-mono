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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Downloads and parses the Groww instrument master.
 *
 * <p>The master is large (~140k rows) and rarely changes, so it is cached with a TTL and refreshed
 * single-flight: a burst of callers triggers at most one download. Caching protects the Groww
 * Non-Trading quota and keeps instrument lookups fast.
 */
public class GrowwInstrumentClient implements InstrumentBroker {

    private final GrowwProperties properties;
    private final GrowwCallExecutor callExecutor;
    private final GrowwHttpClient http;
    private final GrowwRequestFactory requests;
    private final GrowwInstrumentCsvParser parser;
    private final Clock clock;
    private final AtomicReference<CachedMaster> cache = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    public GrowwInstrumentClient(
            GrowwProperties properties,
            GrowwCallExecutor callExecutor,
            GrowwHttpClient http,
            GrowwRequestFactory requests,
            GrowwInstrumentCsvParser parser,
            Clock clock) {
        this.properties = properties;
        this.callExecutor = callExecutor;
        this.http = http;
        this.requests = requests;
        this.parser = parser;
        this.clock = clock;
    }

    @Override
    public List<BrokerInstrument> downloadInstrumentMaster() {
        CachedMaster current = cache.get();
        if (current != null && !current.isExpired(clock)) {
            return current.instruments();
        }
        refreshLock.lock();
        try {
            current = cache.get();
            if (current != null && !current.isExpired(clock)) {
                return current.instruments();
            }
            List<BrokerInstrument> instruments = fetch();
            cache.set(new CachedMaster(instruments, clock.instant().plus(properties.getInstrumentMasterCacheTtl())));
            return instruments;
        } finally {
            refreshLock.unlock();
        }
    }

    private List<BrokerInstrument> fetch() {
        String csv = callExecutor.execute(
                GrowwOperation.INSTRUMENT_MASTER,
                GrowwCallPriority.BULK,
                () -> http.getText(
                        requests.getAbsolute(URI.create(properties.getInstrumentMasterUrl())),
                        GrowwOperation.INSTRUMENT_MASTER));
        return parser.parse(csv);
    }

    private record CachedMaster(List<BrokerInstrument> instruments, Instant expiresAt) {
        boolean isExpired(Clock clock) {
            return !clock.instant().isBefore(expiresAt);
        }
    }
}

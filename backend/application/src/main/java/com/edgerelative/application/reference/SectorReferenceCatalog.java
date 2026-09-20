package com.edgerelative.application.reference;

import java.util.List;

/**
 * NSE sector benchmark reference data.
 *
 * <p>Every benchmark code below was verified present in Groww's instrument master as an NSE index
 * ({@code instrument_type = IDX}, {@code exchange = NSE}) before being seeded. Sectors with no
 * available NSE index (for example Energy and Telecom) are deliberately absent so the resolver
 * reports sector context as unavailable rather than substituting a proxy.
 */
public final class SectorReferenceCatalog {

    private SectorReferenceCatalog() {
    }

    /** One sector and the index benchmark used to represent it. Codes match canonical index symbols. */
    public record SectorBenchmark(String sectorCode, String sectorName, String benchmarkCode, String benchmarkName) {
    }

    public static final List<SectorBenchmark> NSE_SECTOR_BENCHMARKS = List.of(
            new SectorBenchmark("BANK", "NIFTY Bank", "BANKNIFTY", "NIFTY Bank"),
            new SectorBenchmark("PRIVATE_BANK", "NIFTY Private Bank", "NIFTYPVTBANK", "NIFTY Private Bank"),
            new SectorBenchmark("PSU_BANK", "NIFTY PSU Bank", "NIFTYPSUBANK", "NIFTY PSU Bank"),
            new SectorBenchmark("IT", "NIFTY IT", "NIFTYIT", "NIFTY IT"),
            new SectorBenchmark("PHARMA", "NIFTY Pharma", "NIFTYPHARMA", "NIFTY Pharma"),
            new SectorBenchmark("AUTO", "NIFTY Auto", "NIFTYAUTO", "NIFTY Auto"),
            new SectorBenchmark("FMCG", "Nifty FMCG", "NIFTYFMCG", "Nifty FMCG"),
            new SectorBenchmark("METAL", "NIFTY Metal", "NIFTYMETAL", "NIFTY Metal"),
            new SectorBenchmark("REALTY", "NIFTY Realty", "NIFTYREALTY", "NIFTY Realty"),
            new SectorBenchmark("MEDIA", "Nifty Media", "NIFTYMEDIA", "Nifty Media"),
            new SectorBenchmark("FINANCIAL_SERVICES", "Nifty Financial Services", "FINNIFTY", "Nifty Financial Services"));
}

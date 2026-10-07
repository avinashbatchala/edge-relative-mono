package com.edgerelative.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.application.BacktestPresets;
import com.edgerelative.application.catalog.application.StrategyParametersJson;
import com.edgerelative.application.strategy.application.StrategyBindingResolver;
import com.edgerelative.application.strategy.application.StrategyParametersProvider;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.persistence.StrategyBindingRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StrategyBindingResolverTest {

    private static final LocalDate AS_OF = LocalDate.of(2025, 6, 1);
    private static final StrategyParametersJson JSON = new StrategyParametersJson(new JsonMapper());

    /** Repository stub: only the read methods the resolver uses are overridden. */
    private static final class StubRepository extends StrategyBindingRepository {
        private final StrategyBindingRepository.Binding binding;

        private StubRepository(StrategyBindingRepository.Binding binding) {
            super(null);
            this.binding = binding;
        }

        @Override
        public Optional<Binding> findEffective(long instrumentId, LocalDate asOf) {
            return Optional.ofNullable(binding);
        }

        @Override
        public boolean hasBindings() {
            return binding != null;
        }
    }

    private static StrategyParameters effectiveParams() {
        return BacktestPresets.strategy(BacktestPresets.STRATEGY_RS_RESEARCH).orElseThrow();
    }

    @Test
    void bindingIsPreferredAndCarriesItsVersion() {
        StrategyBindingRepository.Binding binding = new StrategyBindingRepository.Binding(
                JSON.write(effectiveParams()), 7L, "VALIDATED", LocalDate.of(2025, 1, 1), null);
        StrategyBindingResolver resolver = new StrategyBindingResolver(
                new StubRepository(binding), JSON, new StrategyParametersProvider(Optional.empty()));

        Optional<StrategyBindingResolver.Resolved> resolved = resolver.resolve(1L, AS_OF);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().source()).isEqualTo("BINDING");
        assertThat(resolved.get().strategyVersionId()).isEqualTo(7L);
        assertThat(resolved.get().parameters().parameterSetId()).isEqualTo(effectiveParams().parameterSetId());
        assertThat(resolver.configured()).isTrue();
    }

    @Test
    void fallsBackToGlobalWhenNoBinding() {
        StrategyBindingResolver resolver = new StrategyBindingResolver(
                new StubRepository(null), JSON, new StrategyParametersProvider(Optional.of(effectiveParams())));

        Optional<StrategyBindingResolver.Resolved> resolved = resolver.resolve(1L, AS_OF);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().source()).isEqualTo("GLOBAL");
        assertThat(resolved.get().strategyVersionId()).isNull();
        assertThat(resolver.configured()).isTrue();
    }

    @Test
    void emptyWhenNeitherBindingNorGlobalIsConfigured() {
        StrategyBindingResolver resolver = new StrategyBindingResolver(
                new StubRepository(null), JSON, new StrategyParametersProvider(Optional.empty()));

        assertThat(resolver.resolve(1L, AS_OF)).isEmpty();
        assertThat(resolver.configured()).isFalse();
    }
}

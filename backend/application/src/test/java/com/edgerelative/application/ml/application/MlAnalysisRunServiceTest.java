package com.edgerelative.application.ml.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edgerelative.application.ml.persistence.MlAnalysisRunRepository;
import com.edgerelative.application.ml.persistence.MlAnalysisRunRepository.Run;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MlAnalysisRunServiceTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final MlAnalysisRunRepository repository = mock(MlAnalysisRunRepository.class);
    private final MlAnalysisRunService service = new MlAnalysisRunService(repository, json);

    private void persisted(String key) {
        when(repository.enqueue(anyString(), anyString())).thenReturn(key);
        when(repository.find(key)).thenReturn(Optional.of(new Run(
                1L, key, "QUEUED", "operator", "{}", "{}", "{}", null, null,
                Instant.parse("2026-09-18T10:00:00Z"), null, null)));
    }

    @Test
    void acceptsAValidConfiguration() {
        persisted("run-key");
        var view = service.create(new MlAnalysisRunService.CreateRequest(
                Map.of("symbols", List.of("SBIN"), "setupTimeframe", "M5",
                        "startDate", "2023-10-01", "endDate", "2026-09-18"),
                "operator"));
        assertThat(view.key()).isEqualTo("run-key");
    }

    @Test
    void rejectsAnEmptyUniverse() {
        assertThatThrownBy(() -> service.create(new MlAnalysisRunService.CreateRequest(
                Map.of("symbols", List.of(), "setupTimeframe", "M5",
                        "startDate", "2023-10-01", "endDate", "2026-09-18"),
                "operator")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbols");
    }

    @Test
    void rejectsAnUnsupportedTimeframe() {
        assertThatThrownBy(() -> service.create(new MlAnalysisRunService.CreateRequest(
                Map.of("symbols", List.of("SBIN"), "setupTimeframe", "H1",
                        "startDate", "2023-10-01", "endDate", "2026-09-18"),
                "operator")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("setupTimeframe");
    }

    @Test
    void rejectsAnInvertedDateRange() {
        assertThatThrownBy(() -> service.create(new MlAnalysisRunService.CreateRequest(
                Map.of("symbols", List.of("SBIN"), "setupTimeframe", "M5",
                        "startDate", "2026-09-18", "endDate", "2023-10-01"),
                "operator")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDate must be before");
    }
}

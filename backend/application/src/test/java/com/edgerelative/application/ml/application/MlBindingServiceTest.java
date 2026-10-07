package com.edgerelative.application.ml.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edgerelative.application.ml.persistence.MlBindingRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MlBindingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private final MlBindingRepository repository = mock(MlBindingRepository.class);
    private final MlBindingService service = new MlBindingService(repository);

    private MlBindingService.CreateRequest request(LocalDate from) {
        return new MlBindingService.CreateRequest(1L, 2L, "RANKER", from, null, "VALIDATED", "test");
    }

    @Test
    void supersedesAnOverlappingOpenBinding() {
        when(repository.startingAfter(1L, TODAY)).thenReturn(List.of());
        when(repository.overlapping(1L, TODAY))
                .thenReturn(List.of(new MlBindingRepository.Window(5L, TODAY, null)));
        when(repository.insert(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(9L);

        long bindingId = service.create(request(TODAY));

        assertThat(bindingId).isEqualTo(9L);
        verify(repository).close(5L, TODAY);
        verify(repository).insert(1L, 2L, "RANKER", TODAY, null, "VALIDATED", "test");
    }

    @Test
    void rejectsWhenAFutureBindingWouldStillOverlap() {
        when(repository.startingAfter(1L, TODAY))
                .thenReturn(List.of(new MlBindingRepository.Window(9L, TODAY.plusDays(3), null)));

        assertThatThrownBy(() -> service.create(request(TODAY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already effective from");

        verify(repository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnInvalidAuthorityLevel() {
        assertThatThrownBy(() -> service.create(
                new MlBindingService.CreateRequest(1L, 2L, "SUPERUSER", TODAY, null, "VALIDATED", "test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorityLevel");
    }
}

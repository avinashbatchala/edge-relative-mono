package com.edgerelative.application.feature.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edgerelative.application.feature.api.FeatureDashboardRow;
import com.edgerelative.application.feature.service.FeatureDashboardService;

import java.util.List;

import org.junit.jupiter.api.Test;

class FeatureStreamProducerTest {

    @Test
    void broadcastsOnlyWhenTheAuthoritativeContentChanges() {
        FeatureDashboardService dashboard = mock(FeatureDashboardService.class);
        FeatureStreamPublisher publisher = mock(FeatureStreamPublisher.class);
        FeatureDashboardRow first = mock(FeatureDashboardRow.class);
        FeatureDashboardRow second = mock(FeatureDashboardRow.class);
        when(dashboard.rows(true)).thenReturn(List.of(first));
        FeatureStreamProducer producer = new FeatureStreamProducer(dashboard, publisher);

        producer.poll();
        verify(publisher, times(1)).broadcastUpdate(any());

        // Identical content must not advance the stream sequence.
        producer.poll();
        verify(publisher, times(1)).broadcastUpdate(any());

        when(dashboard.rows(true)).thenReturn(List.of(second));
        producer.poll();
        verify(publisher, times(2)).broadcastUpdate(any());
    }
}

package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.RejectThresholdConfig;
import my.inspectorrag.searchandreturn.domain.repository.RejectThresholdConfigRepository;
import my.inspectorrag.searchandreturn.interfaces.dto.UpdateRejectThresholdConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejectThresholdConfigApplicationServiceTest {

    @Mock
    private RejectThresholdConfigRepository repository;

    private RejectThresholdConfigApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RejectThresholdConfigApplicationService(
                repository,
                0.55,
                0.72,
                0.08,
                0.70,
                2
        );
    }

    @Test
    void getCurrentShouldFallbackToDefaultsWhenDbHasNoRow() {
        when(repository.findCurrent()).thenReturn(Optional.empty());

        var response = service.getCurrent();

        assertEquals(0.55, response.minTop1Score());
        assertEquals(0.72, response.minTop1ScoreVectorOnly());
        assertEquals(0.08, response.minTopGap());
        assertEquals(0.70, response.minConfidentScore());
        assertEquals(2, response.minEvidenceCount());
        assertEquals("default", response.source());
        assertEquals(null, response.updatedBy());
        assertEquals(null, response.updatedAt());
    }

    @Test
    void getCurrentShouldUseDbWhenPresent() {
        OffsetDateTime now = OffsetDateTime.now();
        when(repository.findCurrent()).thenReturn(Optional.of(
                new RejectThresholdConfig(0.61, 0.66, 0.05, 0.68, 1, "tester", now)
        ));

        var response = service.getCurrent();

        assertEquals(0.61, response.minTop1Score());
        assertEquals(0.66, response.minTop1ScoreVectorOnly());
        assertEquals(0.05, response.minTopGap());
        assertEquals(0.68, response.minConfidentScore());
        assertEquals(1, response.minEvidenceCount());
        assertEquals("tester", response.updatedBy());
        assertEquals(now, response.updatedAt());
        assertEquals("db", response.source());
    }

    @Test
    void updateShouldPersistAndUseAnonymousOperatorByDefault() {
        var request = new UpdateRejectThresholdConfigRequest(0.62, 0.67, 0.04, 0.69, 1);

        var response = service.update(request, "   ");

        verify(repository).upsert(
                eq(0.62),
                eq(0.67),
                eq(0.04),
                eq(0.69),
                eq(1),
                eq("anonymous"),
                any(OffsetDateTime.class)
        );
        verify(repository, never()).findCurrent();
        assertEquals("db", response.source());
        assertEquals("anonymous", response.updatedBy());
    }

    @Test
    void resolveForAskShouldFallbackToDefaultsWhenReadThrows() {
        when(repository.findCurrent()).thenThrow(new IllegalStateException("db down"));

        var config = service.resolveForAsk();

        assertEquals(0.55, config.minTop1Score());
        assertEquals(2, config.minEvidenceCount());
        verify(repository, never()).upsert(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyString(), any());
    }
}

package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaCommandMapper;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaDetailRow;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaEvidenceRow;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaQueryMapper;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.RecallCandidateRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisQaRepositoryTest {

    @Mock
    private QaQueryMapper queryMapper;

    @Mock
    private QaCommandMapper commandMapper;

    @Test
    void keywordRecallShouldReturnEmptyWhenNoValidTerms() {
        MybatisQaRepository repository = new MybatisQaRepository(queryMapper, commandMapper);
        List<RecallCandidate> result = repository.keywordRecall("a", List.of(" ", "x"), 5, QaFilters.empty(), "simple");
        assertTrue(result.isEmpty());
        verify(queryMapper, never()).keywordRecall(anyString(), anyString(), anyList(), anyInt(), anyList(), anyString(), any(), anyList());
    }

    @Test
    void keywordRecallShouldBuildQueryTermsAndMapRows() {
        MybatisQaRepository repository = new MybatisQaRepository(queryMapper, commandMapper);
        when(queryMapper.keywordRecall(anyString(), anyString(), anyList(), anyInt(), anyList(), nullable(String.class), any(), anyList())).thenReturn(List.of(
                new RecallCandidateRow(2L, "法规B", "第2条", "内容B", 0.77, 3, 3, "v2")
        ));

        QaFilters filters = new QaFilters(List.of("建筑施工"), List.of("standard"), null, null);
        List<RecallCandidate> result = repository.keywordRecall("高处作业 防护栏杆", List.of("临边防护"), 8, filters, "simple");

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).chunkId());

        ArgumentCaptor<String> keywordQueryCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryMapper).keywordRecall(
                eq("simple"),
                keywordQueryCaptor.capture(),
                anyList(),
                eq(8),
                eq(List.of("standard")),
                eq(null),
                eq(null),
                eq(List.of("建筑施工"))
        );
        String keywordQuery = keywordQueryCaptor.getValue();
        assertTrue(keywordQuery.contains("高处作业"));
        assertTrue(keywordQuery.contains("临边防护"));
    }

    @Test
    void findQaDetailShouldMapWithEvidence() {
        MybatisQaRepository repository = new MybatisQaRepository(queryMapper, commandMapper);
        OffsetDateTime now = OffsetDateTime.now();
        when(queryMapper.findQaDetail(9L)).thenReturn(new QaDetailRow(
                9L,
                100L,
                2,
                "原问题",
                "标准问题",
                "改写问题",
                "[\"改写问题\"]",
                "答案",
                "success",
                now
        ));
        when(queryMapper.findQaEvidences(9L)).thenReturn(List.of(
                new QaEvidenceRow(1, 100L, "法规A", "第1条", "引用内容", "hybrid", BigDecimal.valueOf(0.88), 8, 9, "v1")
        ));

        Optional<?> detail = repository.findQaDetail(9L);
        assertTrue(detail.isPresent());
        assertEquals(1, repository.findQaEvidences(9L).size());
        assertEquals("hybrid", repository.findQaEvidences(9L).get(0).sourceType());
    }

    @Test
    void filterChunkIdsByMetadataShouldHandleEmptyAndDistinct() {
        MybatisQaRepository repository = new MybatisQaRepository(queryMapper, commandMapper);
        assertTrue(repository.filterChunkIdsByMetadata(List.of(), QaFilters.empty()).isEmpty());

        when(queryMapper.filterChunkIdsByMetadata(anyList(), anyList(), nullable(String.class), any(), anyList()))
                .thenReturn(List.of(1L, 2L, 2L));
        Set<Long> ids = repository.filterChunkIdsByMetadata(List.of(1L, 2L, 3L), QaFilters.empty());
        assertEquals(Set.of(1L, 2L), ids);
    }

    @Test
    void insertMethodsShouldDelegateToCommandMapper() {
        MybatisQaRepository repository = new MybatisQaRepository(queryMapper, commandMapper);
        OffsetDateTime now = OffsetDateTime.now();
        RecallCandidate candidate = new RecallCandidate(10L, "法规", "第3条", "内容", 0.9, 1, 1, "v1");

        repository.insertConversation(99L, now);
        repository.insertQaRecord(1L, 99L, 1, "q", "nq", "rw", "a", 123, now);
        repository.insertRejectedQaRecord(2L, 99L, 2, "q2", "nq2", "rw2", "hint", "NO_EVIDENCE: x", 66, now);
        repository.insertRetrievalSnapshot(3L, 1L, "text-embedding-3-small", 20, 8, "{}", "关键词", "有效查询", "[\"q1\"]", now);
        repository.insertCandidate(4L, 1L, 10L, "hybrid", 0.8, 0.7, 0.75, 1, now);
        repository.insertEvidence(5L, 1L, candidate, 1, now);

        verify(commandMapper).insertConversation(99L, now);
        verify(commandMapper).insertQaRecord(1L, 99L, 1, "q", "nq", "rw", "a", 123, now);
        verify(commandMapper).insertRejectedQaRecord(2L, 99L, 2, "q2", "nq2", "rw2", "hint", "NO_EVIDENCE: x", 66, now);
        verify(commandMapper).insertRetrievalSnapshot(3L, 1L, "text-embedding-3-small", 20, 8, "{}", "关键词", "有效查询", "[\"q1\"]", now);
        verify(commandMapper).insertCandidate(4L, 1L, 10L, "hybrid", 0.8, 0.7, 0.75, 1, now);
        verify(commandMapper).insertEvidence(5L, 1L, 10L, 1, "内容", "法规", "第3条", 1, 1, "v1", now);
    }
}

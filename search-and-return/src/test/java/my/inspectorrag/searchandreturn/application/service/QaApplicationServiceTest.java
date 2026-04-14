package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;
import my.inspectorrag.searchandreturn.domain.model.ConversationMessage;
import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.RejectThresholdConfig;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.model.RewriteMode;
import my.inspectorrag.searchandreturn.domain.model.RewriteResult;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import my.inspectorrag.searchandreturn.domain.service.QuestionRewriteService;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import my.inspectorrag.searchandreturn.interfaces.dto.AskFilters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaApplicationServiceTest {

    @Mock
    private QaRepository qaRepository;
    @Mock
    private RecallService recallService;
    @Mock
    private AnswerGenerator answerGenerator;
    @Mock
    private QuestionRewriteService questionRewriteService;
    @Mock
    private RejectThresholdConfigApplicationService rejectThresholdConfigApplicationService;

    @BeforeEach
    void setUpDefaults() {
        lenient().when(qaRepository.existsConversation(anyLong())).thenReturn(false);
        lenient().when(qaRepository.nextTurnNo(anyLong())).thenReturn(2);
        lenient().when(qaRepository.findConversationContext(anyLong(), anyInt())).thenReturn(List.of());
        lenient().when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), any(RewriteMode.class)))
                .thenAnswer(invocation -> {
                    String normalized = invocation.getArgument(1, String.class);
                    return new RewriteResult(true, "default", normalized, List.of(normalized), List.of(), "default");
                });
        lenient().when(rejectThresholdConfigApplicationService.resolveForAsk())
                .thenReturn(new RejectThresholdConfig(0.55, 0.72, 0.08, 0.70, 2, null, null));
    }

    @Test
    void askShouldCreateConversationAndPersistSuccess() {
        QaApplicationService service = buildService(0.10, 0.01, 0.90, 1);
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.91, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("  高处作业防护  ", null, null);

        assertNotNull(response.qaId());
        assertNotNull(response.conversationId());
        assertEquals(1, response.turnNo());
        assertEquals("success", response.answerStatus());
        verify(qaRepository).insertConversation(anyLong(), any());
        verify(qaRepository).insertQaRecord(anyLong(), anyLong(), eq(1), anyString(), anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void askShouldThrowWhenNoEvidence() {
        QaApplicationService service = buildDefaultService();
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of());
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.ask("问题", null, null));
        assertEquals("没有在数据库中找到合适的法律法规", ex.getMessage());
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyLong(), eq(1), anyString(), anyString(), anyString(), eq(null), anyString(), anyInt(), any());
    }

    @Test
    void askShouldReturnRejectGuidanceWhenLowConfidence() {
        QaApplicationService service = buildDefaultService();
        when(answerGenerator.generateLowConfidenceGuidance(anyString(), anyString(), anyList())).thenReturn("建议补充作业场景后重试");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.2, 1, 1, "v1"),
                new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.1, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("临边防护栏杆要求", null, null);

        assertEquals("reject", response.answerStatus());
        assertEquals("建议补充作业场景后重试", response.answer());
        verify(answerGenerator).generateLowConfidenceGuidance(anyString(), anyString(), anyList());
        verify(qaRepository).insertRejectedQaRecord(anyLong(), anyLong(), eq(1), anyString(), anyString(), anyString(), eq("建议补充作业场景后重试"), anyString(), anyInt(), any());
    }

    @Test
    void askShouldUseExistingConversationAndContext() {
        QaApplicationService service = buildService(0.10, 0.01, 0.90, 1);
        when(qaRepository.existsConversation(100L)).thenReturn(true);
        when(qaRepository.nextTurnNo(100L)).thenReturn(3);
        when(qaRepository.findConversationContext(100L, 6)).thenReturn(List.of(
                new ConversationContextTurn("上一问", "改写上一问", "上一答", "success")
        ));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.91, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("继续提问", "100", null);

        assertEquals("100", response.conversationId());
        assertEquals(3, response.turnNo());
        verify(questionRewriteService).rewrite(eq("继续提问"), eq("继续提问"), anyList(), eq(RewriteMode.AUTO));
        verify(answerGenerator).generate(eq("继续提问"), eq("继续提问"), anyList(), anyList());
    }

    @Test
    void askShouldFallbackWhenRewriteFailed() {
        QaApplicationService service = buildService(0.10, 0.01, 0.90, 1);
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), any(RewriteMode.class)))
                .thenThrow(new IllegalArgumentException("rewrite failed"));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.95, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("  临边防护栏杆要求 ", null, null);

        assertEquals(null, response.rewrittenQuestion());
        assertEquals(List.of("临边防护栏杆要求"), response.rewriteQueries());
        verify(questionRewriteService, times(3)).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO));
    }

    @Test
    void askShouldAlwaysIncludeNormalizedQuestionInRecallQueries() {
        QaApplicationService service = buildService(0.10, 0.01, 0.90, 1);
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO)))
                .thenReturn(new RewriteResult(
                        true,
                        "need-rewrite",
                        "施工单位在安全生产责任制中未明确岗位职责的法规依据是什么",
                        List.of(
                                "施工单位岗位职责未明确的法规责任",
                                "施工单位安全生产责任制岗位职责要求",
                                "施工单位安全管理职责细化规范"
                        ),
                        List.of("施工单位", "岗位职责"),
                        "mock"
                ));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.95, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        service.ask("施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？", null, null);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(recallService, atLeastOnce()).recall(queryCaptor.capture(), anyInt(), any(), anyString());
        assertEquals(
                "施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？",
                queryCaptor.getAllValues().getFirst()
        );
    }

    @Test
    void askShouldUseSingleNormalizedQueryWhenRewriteNotNeeded() {
        QaApplicationService service = buildService(0.10, 0.01, 0.90, 1);
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO)))
                .thenReturn(new RewriteResult(
                        false,
                        "already-fit",
                        null,
                        List.of("不应使用"),
                        List.of("施工单位", "安全生产责任制"),
                        "mock"
                ));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.95, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？", null, null);

        assertEquals(null, response.rewrittenQuestion());
        assertEquals(List.of("施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？"), response.rewriteQueries());
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO));
    }

    @Test
    void askShouldTriggerForceRewriteWhenAutoNoRewriteAndLowConfidence() {
        QaApplicationService service = buildService(0.55, 0.08, 0.70, 1);
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO)))
                .thenReturn(new RewriteResult(
                        false,
                        "already-fit",
                        null,
                        List.of(),
                        List.of(),
                        "mock"
                ));
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.FORCE)))
                .thenReturn(new RewriteResult(
                        true,
                        "force-fallback",
                        "施工单位在安全生产责任制中应当明确各岗位职责的依据是什么？",
                        List.of("施工单位在安全生产责任制中应当明确各岗位职责的依据是什么？"),
                        List.of("施工单位", "岗位职责"),
                        "mock"
                ));
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString()))
                .thenReturn(List.of(
                        new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.20, 1, 1, "v1"),
                        new RecallCandidate(2L, "法规B", "第2条", "内容B", 0.19, 1, 1, "v1")
                ))
                .thenReturn(List.of(
                        new RecallCandidate(10L, "法规X", "第10条", "内容X", 0.95, 1, 1, "v1")
                ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？", null, null);

        assertEquals("success", response.answerStatus());
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO));
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.FORCE));
    }

    @Test
    void askShouldPreferHigherTop1RoundWhenBothRoundsReject() {
        QaApplicationService service = buildService(0.90, 0.08, 0.95, 1);
        String question = "施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？";
        String forceRewrite = "施工单位在安全生产责任制中应当明确各岗位职责的依据是什么？";

        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO)))
                .thenReturn(new RewriteResult(
                        false,
                        "already-fit",
                        null,
                        List.of(),
                        List.of(),
                        "mock"
                ));
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.FORCE)))
                .thenReturn(new RewriteResult(
                        true,
                        "force-fallback",
                        forceRewrite,
                        List.of(forceRewrite),
                        List.of("施工单位", "岗位职责"),
                        "mock"
                ));
        when(answerGenerator.generateLowConfidenceGuidance(anyString(), anyString(), anyList()))
                .thenReturn("建议补充法规关键字后重试");
        when(recallService.recall(anyString(), anyInt(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0, String.class);
                    if (query.contains("应当明确")) {
                        return List.of(new RecallCandidate(10L, "法规X", "第10条", "内容X", 0.40, 1, 1, "v1"));
                    }
                    return List.of(new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.20, 1, 1, "v1"));
                });
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask(question, null, null);

        assertEquals("reject", response.answerStatus());
        assertEquals(forceRewrite, response.rewrittenQuestion());
        verify(answerGenerator).generateLowConfidenceGuidance(eq(question), eq(forceRewrite), anyList());
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO));
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.FORCE));
    }

    @Test
    void askShouldPreferAutoRoundWhenTop1TieAndBothRoundsReject() {
        QaApplicationService service = buildService(0.90, 0.08, 0.95, 1);
        String question = "施工单位未在安全生产责任制中明确各岗位的职责，依据是什么？";
        String forceRewrite = "施工单位在安全生产责任制中应当明确各岗位职责的依据是什么？";

        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO)))
                .thenReturn(new RewriteResult(
                        false,
                        "already-fit",
                        null,
                        List.of(),
                        List.of(),
                        "mock"
                ));
        when(questionRewriteService.rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.FORCE)))
                .thenReturn(new RewriteResult(
                        true,
                        "force-fallback",
                        forceRewrite,
                        List.of(forceRewrite),
                        List.of("施工单位", "岗位职责"),
                        "mock"
                ));
        when(answerGenerator.generateLowConfidenceGuidance(anyString(), anyString(), anyList()))
                .thenReturn("建议补充法规关键字后重试");
        when(recallService.recall(anyString(), anyInt(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0, String.class);
                    if (query.contains("应当明确")) {
                        return List.of(new RecallCandidate(10L, "法规X", "第10条", "内容X", 0.20, 1, 1, "v1"));
                    }
                    return List.of(new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.20, 1, 1, "v1"));
                });
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask(question, null, null);

        assertEquals("reject", response.answerStatus());
        assertEquals(null, response.rewrittenQuestion());
        assertEquals(List.of(question), response.rewriteQueries());
        verify(answerGenerator).generateLowConfidenceGuidance(eq(question), eq(question), anyList());
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.AUTO));
        verify(questionRewriteService).rewrite(anyString(), anyString(), anyList(), eq(RewriteMode.FORCE));
    }

    @Test
    void getQaShouldReturnConversationFields() {
        QaApplicationService service = buildDefaultService();
        OffsetDateTime now = OffsetDateTime.now();
        var evidence = new QaEvidence(1, 1L, "法规A", "第1条", "内容A", "vector", 0.9, 1, 1, "v1");
        when(qaRepository.findQaDetail(9L)).thenReturn(Optional.of(
                new QaDetail(9L, 100L, 2, "原问题", "标准问题", "改写问题", List.of("改写问题"), "答案", "success", now, List.of(evidence))
        ));

        var detail = service.getQa(9L);
        assertEquals("9", detail.qaId());
        assertEquals("100", detail.conversationId());
        assertEquals(2, detail.turnNo());
    }

    @Test
    void getConversationMessagesShouldMapFromRepository() {
        QaApplicationService service = buildDefaultService();
        OffsetDateTime now = OffsetDateTime.now();
        var evidence = new QaEvidence(1, 1L, "法规A", "第1条", "内容A", "vector", 0.9, 1, 1, "v1");
        when(qaRepository.findConversationMessages(100L)).thenReturn(List.of(
                new ConversationMessage(9L, 1, "问题", "标准问题", "改写问题", List.of("改写问题"), "答案", "success", now, List.of(evidence))
        ));

        var messages = service.getConversationMessages(100L);
        assertEquals(1, messages.size());
        assertEquals("9", messages.get(0).qaId());
        assertEquals(1, messages.get(0).turnNo());
        assertEquals(1, messages.get(0).evidences().size());
    }

    @Test
    void askShouldNotCreateConversationWhenExistingConversationIsValid() {
        QaApplicationService service = buildService(0.10, 0.01, 0.90, 1);
        when(qaRepository.existsConversation(555L)).thenReturn(true);
        when(qaRepository.nextTurnNo(555L)).thenReturn(8);
        when(answerGenerator.generate(anyString(), anyString(), anyList(), anyList())).thenReturn("mock answer");
        when(recallService.recall(anyString(), anyInt(), any(), anyString())).thenReturn(List.of(
                new RecallCandidate(1L, "法规A", "第1条", "内容A", 0.95, 1, 1, "v1")
        ));
        when(qaRepository.keywordRecall(anyString(), anyList(), anyInt(), any(), anyString())).thenReturn(List.of());

        var response = service.ask("继续问", "555", new AskFilters(List.of(), List.of(), null, null));

        assertEquals("555", response.conversationId());
        assertEquals(8, response.turnNo());
        verify(qaRepository, never()).insertConversation(anyLong(), any());
    }

    private QaApplicationService buildDefaultService() {
        return buildService(0.55, 0.08, 0.70, 2);
    }

    private QaApplicationService buildService(
            double minTop1Score,
            double minTopGap,
            double minConfidentScore,
            int minEvidenceCount
    ) {
        lenient().when(rejectThresholdConfigApplicationService.resolveForAsk())
                .thenReturn(new RejectThresholdConfig(
                        minTop1Score,
                        0.72,
                        minTopGap,
                        minConfidentScore,
                        minEvidenceCount,
                        null,
                        null
                ));
        return new QaApplicationService(
                qaRepository,
                recallService,
                answerGenerator,
                questionRewriteService,
                rejectThresholdConfigApplicationService,
                "text-embedding-3-small",
                2,
                4,
                3,
                "simple",
                true,
                0.55,
                0.25,
                0.10,
                0.10,
                3,
                3,
                120,
                6
        );
    }
}

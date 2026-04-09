package my.inspectorrag.records.application.service;

import my.inspectorrag.records.domain.repository.RecordsRepository;
import my.inspectorrag.records.interfaces.dto.QaRecordItemDto;
import my.inspectorrag.records.interfaces.dto.QaReplayCandidateDto;
import my.inspectorrag.records.interfaces.dto.QaReplayDto;
import my.inspectorrag.records.interfaces.dto.QaReplayEvidenceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecordsApplicationService {

    private final RecordsRepository recordsRepository;

    public RecordsApplicationService(RecordsRepository recordsRepository) {
        this.recordsRepository = recordsRepository;
    }

    @Transactional(readOnly = true)
    public List<QaRecordItemDto> listQa(int limit) {
        return recordsRepository.listQa(limit).stream()
                .map(it -> new QaRecordItemDto(it.qaId(), it.question(), it.answerStatus(), it.elapsedMs(), it.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public QaReplayDto replay(Long qaId) {
        var replay = recordsRepository.replay(qaId)
                .orElseThrow(() -> new IllegalArgumentException("qa replay not found: " + qaId));
        return new QaReplayDto(
                replay.qaId(),
                replay.question(),
                replay.normalizedQuestion(),
                replay.answer(),
                replay.candidates().stream()
                        .map(c -> new QaReplayCandidateDto(c.chunkId(), c.sourceType(), c.rawScore(), c.finalScore(), c.rankNo()))
                        .toList(),
                replay.evidences().stream()
                        .map(e -> new QaReplayEvidenceDto(e.citeNo(), e.chunkId(), e.lawName(), e.articleNo(), e.quotedText()))
                        .toList()
        );
    }
}

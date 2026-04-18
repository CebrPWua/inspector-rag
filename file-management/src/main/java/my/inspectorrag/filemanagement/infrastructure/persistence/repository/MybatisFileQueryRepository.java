package my.inspectorrag.filemanagement.infrastructure.persistence.repository;

import my.inspectorrag.filemanagement.application.query.model.FileDetailView;
import my.inspectorrag.filemanagement.application.query.model.FileListItemView;
import my.inspectorrag.filemanagement.application.query.repository.FileQueryRepository;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentQueryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisFileQueryRepository implements FileQueryRepository {

    private final DocumentQueryMapper queryMapper;

    public MybatisFileQueryRepository(DocumentQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    @Override
    public Optional<FileDetailView> findFileDetail(Long docId) {
        return Optional.ofNullable(queryMapper.findFileDetail(docId))
                .map(row -> new FileDetailView(
                        row.docId(),
                        row.lawName(),
                        row.lawCode(),
                        row.docType(),
                        row.versionNo(),
                        row.status(),
                        row.parseStatus(),
                        row.sourceFileName(),
                        row.fileHash(),
                        row.storagePath(),
                        row.createdAt()
                ));
    }

    @Override
    public List<FileListItemView> listFiles(int limit) {
        return queryMapper.listFiles(limit).stream()
                .map(row -> new FileListItemView(
                        row.docId(),
                        row.lawName(),
                        row.lawCode(),
                        row.docType(),
                        row.versionNo(),
                        row.status(),
                        row.parseStatus(),
                        row.createdAt()
                ))
                .toList();
    }
}

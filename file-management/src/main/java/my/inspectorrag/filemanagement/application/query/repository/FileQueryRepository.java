package my.inspectorrag.filemanagement.application.query.repository;

import my.inspectorrag.filemanagement.application.query.model.FileDetailView;
import my.inspectorrag.filemanagement.application.query.model.FileListItemView;

import java.util.List;
import java.util.Optional;

public interface FileQueryRepository {

    Optional<FileDetailView> findFileDetail(Long docId);

    List<FileListItemView> listFiles(int limit);
}

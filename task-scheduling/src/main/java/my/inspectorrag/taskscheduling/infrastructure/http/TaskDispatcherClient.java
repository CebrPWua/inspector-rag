package my.inspectorrag.taskscheduling.infrastructure.http;

import my.inspectorrag.taskscheduling.domain.model.ImportTask;
import my.inspectorrag.taskscheduling.infrastructure.config.InternalServiceProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class TaskDispatcherClient {

    private final RestClient restClient;
    private final InternalServiceProperties properties;

    public TaskDispatcherClient(InternalServiceProperties properties) {
        this.restClient = RestClient.builder().build();
        this.properties = properties;
    }

    public void dispatch(ImportTask task) {
        if ("parse".equals(task.taskType())) {
            invoke(properties.getDocAnalyzingBaseUrl() + "/internal/tasks/parse", task);
            return;
        }
        if ("embed".equals(task.taskType())) {
            invoke(properties.getEmbeddingBaseUrl() + "/internal/tasks/embed", task);
            return;
        }
        throw new IllegalArgumentException("unsupported task_type in phase1: " + task.taskType());
    }

    private void invoke(String url, ImportTask task) {
        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("taskId", task.id(), "docId", task.docId()))
                .retrieve()
                .toBodilessEntity();
    }
}

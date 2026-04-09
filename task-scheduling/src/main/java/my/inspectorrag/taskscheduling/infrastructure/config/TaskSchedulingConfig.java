package my.inspectorrag.taskscheduling.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({InternalServiceProperties.class, TaskWorkerProperties.class})
public class TaskSchedulingConfig {
}

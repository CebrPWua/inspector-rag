package my.inspectorrag.filemanagement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("my.inspectorrag.filemanagement.infrastructure.persistence.mapper")
public class FileManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManagementApplication.class, args);
    }

}

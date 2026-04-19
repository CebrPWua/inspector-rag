# Inspector RAG

Inspector RAG 是一个面向工地安全检查场景的法规检索与问答平台。系统将上传的法规文件解析为结构化条款，完成向量化索引，并在问答时返回带引用证据的结构化回答。

## 项目结构

本仓库包含 6 个后端模块 + 1 个前端模块：

| 模块 | 端口 | 主要职责 |
|---|---:|---|
| `file-management` | `8081` | 文件上传、去重、文档列表与详情、删除文档 |
| `doc-analyzing` | `8082` | 文档解析、清洗、条款切分 |
| `embedding` | `8083` | chunk 向量化、Embedding Profile 路由写入 |
| `search-and-return` | `8084` | 问答入口、检索融合、证据生成、拒答阈值配置 |
| `records` | `8085` | 问答记录查询、回放、质量报表 |
| `task-scheduling` | `8086` | 异步任务调度、重试、死信处理 |
| `frontend` | `8000` | React + Vite 管理与问答界面 |

## 技术栈

- Java 21
- Gradle (`./gradlew`)
- Spring Boot 4.0.5
- Spring AI 2.0.0-M4（Embedding / Chat）
- MyBatis Plus 3.5.16
- PostgreSQL 17 + pgvector
- RustFS（S3 兼容对象存储）
- 前端：React 19 + Vite + Ant Design

## 运行依赖

建议先准备以下依赖：

1. PostgreSQL（含 `pgvector` 扩展）
2. RustFS（或切换 `STORAGE_MODE=local` 使用本地存储）
3. OneAPI / OpenAI 兼容网关（供 `embedding` 与 `search-and-return` 调用）

## 环境变量

以下变量用于本地运行，示例均为占位符写法：

| 变量名 | 说明 | 示例 |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC 连接 | `jdbc:postgresql://localhost:5432/inspector_rag` |
| `DB_USERNAME` | 数据库用户名 | `<DB_USERNAME>` |
| `DB_PASSWORD` | 数据库密码 | `<DB_PASSWORD>` |
| `STORAGE_MODE` | 文件存储模式（`rustfs`/`local`） | `rustfs` |
| `RUSTFS_ENDPOINT` | RustFS/S3 端点 | `http://localhost:9000` |
| `RUSTFS_ACCESS_KEY` | RustFS Access Key | `<RUSTFS_ACCESS_KEY>` |
| `RUSTFS_SECRET_KEY` | RustFS Secret Key | `<RUSTFS_SECRET_KEY>` |
| `RUSTFS_BUCKET` | 存储桶名称 | `inspector-rag` |
| `SPRING_AI_OPENAI_BASE_URL` | OpenAI 兼容网关地址 | `http://localhost:3000` |
| `SPRING_AI_OPENAI_API_KEY` | 模型服务 API Key | `<SPRING_AI_OPENAI_API_KEY>` |
| `SPRING_AI_OPENAI_CHAT_MODEL` | 对话模型（search-and-return） | `openai/gpt-5-mini` |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 向量模型 | `qwen/qwen3-embedding-8b` |

## 本地启动

### 1. 启动后端服务（建议顺序）

在仓库根目录打开多个终端，分别执行：

```bash
DB_URL='jdbc:postgresql://localhost:5432/inspector_rag' \
DB_USERNAME='<DB_USERNAME>' \
DB_PASSWORD='<DB_PASSWORD>' \
STORAGE_MODE='rustfs' \
RUSTFS_ENDPOINT='http://localhost:9000' \
RUSTFS_ACCESS_KEY='<RUSTFS_ACCESS_KEY>' \
RUSTFS_SECRET_KEY='<RUSTFS_SECRET_KEY>' \
RUSTFS_BUCKET='inspector-rag' \
./gradlew :file-management:bootRun
```

```bash
DB_URL='jdbc:postgresql://localhost:5432/inspector_rag' \
DB_USERNAME='<DB_USERNAME>' \
DB_PASSWORD='<DB_PASSWORD>' \
STORAGE_MODE='rustfs' \
RUSTFS_ENDPOINT='http://localhost:9000' \
RUSTFS_ACCESS_KEY='<RUSTFS_ACCESS_KEY>' \
RUSTFS_SECRET_KEY='<RUSTFS_SECRET_KEY>' \
RUSTFS_BUCKET='inspector-rag' \
DOCLING_ENABLED='false' \
./gradlew :doc-analyzing:bootRun
```

```bash
DB_URL='jdbc:postgresql://localhost:5432/inspector_rag' \
DB_USERNAME='<DB_USERNAME>' \
DB_PASSWORD='<DB_PASSWORD>' \
SPRING_AI_OPENAI_BASE_URL='http://localhost:3000' \
SPRING_AI_OPENAI_API_KEY='<SPRING_AI_OPENAI_API_KEY>' \
SPRING_AI_OPENAI_EMBEDDING_MODEL='qwen/qwen3-embedding-8b' \
./gradlew :embedding:bootRun
```

```bash
DB_URL='jdbc:postgresql://localhost:5432/inspector_rag' \
DB_USERNAME='<DB_USERNAME>' \
DB_PASSWORD='<DB_PASSWORD>' \
SPRING_AI_OPENAI_BASE_URL='http://localhost:3000' \
SPRING_AI_OPENAI_API_KEY='<SPRING_AI_OPENAI_API_KEY>' \
SPRING_AI_OPENAI_CHAT_MODEL='openai/gpt-5-mini' \
SPRING_AI_OPENAI_EMBEDDING_MODEL='qwen/qwen3-embedding-8b' \
./gradlew :search-and-return:bootRun
```

```bash
DB_URL='jdbc:postgresql://localhost:5432/inspector_rag' \
DB_USERNAME='<DB_USERNAME>' \
DB_PASSWORD='<DB_PASSWORD>' \
./gradlew :records:bootRun
```

```bash
DB_URL='jdbc:postgresql://localhost:5432/inspector_rag' \
DB_USERNAME='<DB_USERNAME>' \
DB_PASSWORD='<DB_PASSWORD>' \
DOC_ANALYZING_BASE_URL='http://localhost:8082' \
EMBEDDING_BASE_URL='http://localhost:8083' \
./gradlew :task-scheduling:bootRun
```

### 2. 启动前端

```bash
cd frontend
npm ci
npm run dev
```

启动后访问：`http://localhost:8000`

## API 总览

### file-management (`http://localhost:8081`)

- `POST /api/files/upload`
- `GET /api/files/{docId}`
- `GET /api/files`
- `DELETE /api/files/{docId}`

### doc-analyzing（内部接口，`http://localhost:8082`）

- `POST /internal/tasks/parse`

### embedding（内部接口，`http://localhost:8083`）

- `POST /internal/tasks/embed`
- `POST /internal/embedding/profiles`
- `POST /internal/embedding/profiles/{profileKey}/activate-read`
- `POST /internal/embedding/profiles/{profileKey}/toggle-write`

### search-and-return (`http://localhost:8084`)

- `POST /api/qa/ask`
- `GET /api/qa/{qaId}`
- `GET /api/qa/conversations/{conversationId}/messages`
- `GET /api/qa/config/reject-thresholds`
- `PUT /api/qa/config/reject-thresholds`

### records (`http://localhost:8085`)

- `GET /api/records/qa`
- `GET /api/records/qa/{qaId}/replay`
- `GET /api/records/qa/quality-report`

### task-scheduling (`http://localhost:8086`)

- `POST /api/tasks/retry/{taskId}`
- `PATCH /api/tasks/dead-letter/{id}/assign`
- `PATCH /api/tasks/dead-letter/{id}/status`
- `GET /api/tasks/dead-letter`

## 任务状态联动与删除规则

- 文档删除接口 `DELETE /api/files/{docId}` 仅在 `parse_status in (success, failed)` 时可执行。
- `parse` 任务超过最大重试次数并进入死信后，文档 `parse_status` 会从 `pending/processing` 收敛到 `failed`。
- 手动调用 `POST /api/tasks/retry/{taskId}` 重试 `parse` 任务时，文档 `parse_status` 会从 `failed` 回退到 `pending`。

## 历史数据回填

- 迁移脚本：`src/main/resources/db/migration/V3__parse_dead_letter_backfill_parse_status.sql`
- 适用场景：历史库中存在 `parse` 死信记录，但文档状态仍停留在 `pending/processing`。
- 建议执行方式：随部署流程执行数据库迁移；执行后可用下列 SQL 复核是否还有残留记录。

```sql
select count(*)
from ingest.source_document d
where d.parse_status in ('pending','processing')
  and exists (
    select 1
    from ops.dead_letter_task dl
    join ops.import_task t on t.id = dl.task_id
    where dl.doc_id = d.id
      and dl.task_type = 'parse'
      and (t.task_status = 'failed' or dl.resolution_status in ('resolved','closed'))
  );
```

## 数据库结构快照

发布快照文件位置：

- `src/main/resources/db/schema/inspector_rag_schema.sql`

更新快照命令：

```bash
mkdir -p src/main/resources/db/schema

docker exec -e PGPASSWORD="$DB_PASSWORD" pgvector \
  pg_dump -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-inspector_rag}" \
  -s --no-owner --no-privileges \
  | sed '/^\\restrict /d;/^\\unrestrict /d' \
  > src/main/resources/db/schema/inspector_rag_schema.sql
```

快照验证命令：

```bash
rg -n "CREATE SCHEMA|CREATE TABLE|CREATE INDEX|INSERT INTO" \
  src/main/resources/db/schema/inspector_rag_schema.sql
```

预期结果：能看到 `CREATE SCHEMA/TABLE/INDEX`，且不应出现业务数据 `INSERT INTO`。

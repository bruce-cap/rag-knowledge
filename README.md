# ragbackend

`ragbackend` 是一个基于 Spring Boot 3 的 RAG 后端服务，用于支撑知识库管理、文档上传解析、向量检索、对话问答和空间权限控制。

项目当前已经包含这些核心能力：

- 用户注册、登录、JWT 鉴权
- 知识空间管理与成员权限管理
- 文件夹和文档管理
- 文档上传到 MinIO，并异步解析入库
- 基于 DashScope 向量化和 Chroma 检索的 RAG 问答
- 基于 Ollama 的普通对话与流式对话
- 聊天会话与消息持久化

## 技术栈

- Java 17
- Spring Boot 3.2.5
- Spring Security
- MyBatis-Plus
- MySQL 8
- LangChain4j
- Ollama
- DashScope Embedding
- Chroma
- MinIO
- Apache Tika

## 目录说明

```text
src/main/java/com/example/ragbackend
├─ config        配置类，包含安全、AI、MinIO、异步任务等
├─ controller    接口层
├─ entity        数据库实体
├─ mapper        MyBatis-Plus Mapper
├─ model/dto     请求参数
├─ model/vo      返回对象
├─ service       业务接口与实现
├─ utils         JWT、安全相关工具
└─ common        通用返回结构
```

## 核心业务说明

### 1. 鉴权

- 登录、注册接口位于 `/api/auth/**`
- 其余接口默认都需要认证
- 当前认证方式是 `Authorization: Bearer <token>`

### 2. RAG 流程

当前代码中的主要链路是：

1. 用户上传文档到知识空间
2. 文档文件保存到 MinIO
3. 后端解析文档内容
4. 文本切分后调用 DashScope 生成向量
5. 向量写入 Chroma
6. 对话时按 `spaceId`、`folderId` 和用户权限进行检索
7. 检索结果注入提示词，再调用 Ollama 生成回答

### 3. 权限模型

系统里存在知识空间和成员角色的概念，当前代码中可以看到：

- 系统管理员
- 空间管理员
- 普通成员 / 只读成员

后端会在文档访问、检索、空间成员管理等场景下校验权限。

### 4. 会话能力

- 对话会话保存在 `chat_session`
- 对话消息保存在 `chat_message`
- 支持普通问答和 SSE 流式输出
- 会根据首轮对话内容自动生成标题

## 运行依赖

本项目启动前，至少需要准备以下服务：

- MySQL
- MinIO
- Chroma
- Ollama

如果缺少其中任意一项，文档上传、向量检索或聊天能力会受到影响。

## 环境变量与默认配置

配置文件在 [application.yaml](C:/Users/Cap/Desktop/rag/ragbackend/src/main/resources/application.yaml)。

常用配置项如下：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_URL` | MySQL 连接串 | `jdbc:mysql://localhost:3306/rag...` |
| `DB_USERNAME` | MySQL 用户名 | `root` |
| `DB_PASSWORD` | MySQL 密码 | `123456` |
| `CHROMA_URL` | Chroma 地址 | `http://localhost:8000` |
| `OLLAMA_BASE_URL` | Ollama 地址 | `http://localhost:11434` |
| `OLLAMA_MODEL_NAME` | 聊天模型 | `qwen2.5:1.5b` |
| `DASHSCOPE_API_KEY` | DashScope Key | 需自行覆盖 |
| `DASHSCOPE_MODEL_NAME` | 向量模型 | `text-embedding-v4` |
| `MINIO_ENDPOINT` | MinIO 地址 | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO Access Key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO Secret Key | `minioadmin` |
| `MINIO_BUCKET_NAME` | MinIO Bucket | `rag` |
| `RAG_MIN_SCORE` | 最低检索分数 | `0.7` |
| `RAG_MAX_RESULTS` | 最大召回条数 | `5` |

建议在本地或部署环境里通过环境变量覆盖敏感配置，不要直接依赖配置文件中的默认值。

## 数据库初始化

初始化 SQL 位于 [sql/init.sql](C:/Users/Cap/Desktop/rag/ragbackend/sql/init.sql)。

执行方式示例：

```sql
source sql/init.sql;
```

数据库名默认为 `rag`。

## 启动方式

### 方式一：使用 Maven Wrapper

```powershell
.\mvnw.cmd spring-boot:run
```

### 方式二：先打包再运行

```powershell
.\mvnw.cmd clean package
java -jar target/ragbackend-0.0.1-SNAPSHOT.jar
```

默认情况下，Spring Boot 服务监听 `8080` 端口。

## 常见接口

### 认证

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`

### 知识空间

- `GET /api/space/list`
- `GET /api/space/listAll`
- `POST /api/space/create`
- `PUT /api/space/update/{id}`
- `DELETE /api/space/delete/{id}`

### 文档

- `POST /api/document/upload`
- `POST /api/document/search`
- `GET /api/document/list`
- `DELETE /api/document/delete/{id}`
- `POST /api/document/retry/{id}`
- `GET /api/document/download/{id}`
- `GET /api/document/preview/{id}`

### 对话

- `POST /api/chat/send`
- `POST /api/chat/send/stream`

## 补充说明

- 系统启动时会自动检查并初始化一个公共知识空间
- 文档解析依赖 Apache Tika，Office 和文本类文件可以直接走预览或抽取
- 流式对话基于 `SseEmitter` 实现，前端需要按 SSE 方式消费

## 开发建议

- 前后端联调时，优先确认 `Authorization` 请求头是否正确携带
- 调试 RAG 效果时，重点检查文档解析、向量写入和检索过滤条件
- 如果出现空间范围不对，先检查前端传入的 `spaceId`、`folderId`

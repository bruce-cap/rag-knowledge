# RAG Backend API 文档

## 概述

- **基础路径**: `http://localhost:8080/api`
- **认证方式**: JWT Token（通过 `Authorization: Bearer <token>` Header 或 `jwt_token` Cookie 传递）
- **公开接口**: `/api/auth/**`（登录、注册、登出）
- **统一响应格式**: `Result<T>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {}
  }
  ```
  - `code=200` 表示成功，`code=500` 表示业务错误，其他 code 表示不同错误类型

---

## 一、认证接口 (`/api/auth`)

### 1.1 登录

```
POST /api/auth/login
```

**请求体 (JSON)**:
```json
{
  "username": "string",
  "password": "string"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "jwt_token_string"
}
```

**说明**:
- 登录成功后，Token 通过响应体直接返回
- 同时前端需将 Token 存储在 `jwt_token` Cookie 中（后续接口从 Cookie 读取）

---

### 1.2 注册

```
POST /api/auth/register
```

**请求体 (JSON)**:
```json
{
  "username": "string",
  "password": "string",
  "confirmPassword": "string",
  "email": "string"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 1.3 登出

```
POST /api/auth/logout
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

## 二、聊天接口 (`/api/chat`)

### 2.1 发送消息（同步）

```
POST /api/chat/send
```

**请求体 (JSON)**:
```json
{
  "sessionId": 1,
  "message": "你好，请介绍一下RAG",
  "ragMode": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | Long | 是 | 会话 ID |
| message | String | 是 | 用户消息内容 |
| ragMode | Boolean | 否 | 是否启用 RAG 检索（默认 true） |

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "AI的回复内容..."
}
```

---

### 2.2 发送消息（流式 SSE）

```
POST /api/chat/send/stream
Content-Type: text/event-stream
```

**请求体**: 同 `2.1`

**响应 (SSE 事件流)**:
```
data: {"text": "你好"}

data: {"text": "，我"}

data: {"text": "是AI"}

event: finish
data: [DONE]

event: error
data: {"code": 500, "message": "错误信息"}
```

**说明**:
- 每个 token 通过 `{"text": "xxx"}` JSON 格式发送
- `event: finish` 表示流结束
- `event: error` 表示出错
- 超时时间: 60 分钟

---

## 三、会话管理接口 (`/api/session`)

### 3.1 创建会话

```
POST /api/session/create
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": 1
}
```
返回新建会话的 ID（Long）

---

### 3.2 获取会话列表

```
GET /api/session/list
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "title": "新的对话",
      "createTime": "2026-04-10T10:00:00",
      "updateTime": "2026-04-10T10:00:00"
    }
  ]
}
```

---

### 3.3 删除会话

```
DELETE /api/session/{id}
```

**路径参数**: `id` - 会话 ID

**响应示例**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

## 四、消息管理接口 (`/api/message`)

### 4.1 获取会话消息列表

```
GET /api/message/list/{sessionId}
```

**路径参数**: `sessionId` - 会话 ID

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "sessionId": 1,
      "content": "用户消息",
      "role": "USER",
      "createTime": "2026-04-10T10:00:00"
    },
    {
      "id": 2,
      "sessionId": 1,
      "content": "AI回复",
      "role": "AI",
      "createTime": "2026-04-10T10:00:01"
    }
  ]
}
```

---

### 4.2 删除消息

```
DELETE /api/message/delete/{id}
```

**路径参数**: `id` - 消息 ID

**响应示例**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

### 4.3 编辑消息

```
PUT /api/message/edit/{id}
```

**路径参数**: `id` - 消息 ID

**请求体 (JSON)**:
```json
{
  "content": "编辑后的消息内容"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "编辑成功",
  "data": null
}
```

---

## 五、文档管理接口 (`/api/document`)

### 5.1 上传文档

```
POST /api/document/upload
Content-Type: multipart/form-data
```

**表单参数**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件（最大 50MB） |
| isPublic | Boolean | 否 | 是否公开（默认 false） |

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**说明**:
- 上传后自动进行向量化处理并存入 Embedding Store
- 支持的文件类型由后端实现决定

---

### 5.2 获取文档列表

```
GET /api/document/list
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "fileName": "example.pdf",
      "isPublic": false,
      "createTime": "2026-04-10T10:00:00"
    }
  ]
}
```

---

### 5.3 删除文档

```
DELETE /api/document/{id}
```

**路径参数**: `id` - 文档 ID

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 5.4 文档向量搜索

```
POST /api/document/search
```

**请求体 (JSON)**:
```json
{
  "query": "RAG是什么",
  "limit": 5,
  "minScore": 0.5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | 是 | 搜索文本 |
| limit | Integer | 否 | 最大返回条数（默认 5） |
| minScore | Double | 否 | 最低相似度（默认 0.5） |

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "text": "RAG是一种检索增强生成技术...",
      "score": 0.89,
      "documentId": "1"
    }
  ]
}
```

---

## 六、测试接口

### 6.1 健康检查

```
GET /api/test
```

**响应**: `✅ Spring Boot backend is running!`

### 6.2 获取用户（测试）

```
GET /api/user
```

**说明**: 仅供开发测试，查询 ID=1 的用户

---

## 测试方法

### 使用 curl 测试

**登录**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

**创建会话**:
```bash
curl -X POST http://localhost:8080/api/session/create \
  -H "Authorization: Bearer <your_token>"
```

**发送聊天消息**:
```bash
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"sessionId":1,"message":"你好","ragMode":true}'
```

**流式聊天**:
```bash
curl -X POST http://localhost:8080/api/chat/send/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"sessionId":1,"message":"你好"}' \
  --no-buffer
```

**上传文档**:
```bash
curl -X POST http://localhost:8080/api/document/upload \
  -H "Authorization: Bearer <your_token>" \
  -F "file=@/path/to/file.pdf" \
  -F "isPublic=false"
```

**文档搜索**:
```bash
curl -X POST http://localhost:8080/api/document/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"query":"RAG是什么","limit":5}'
```

### 使用 Postman/Apifox 测试

1. 创建 Collection，配置全局变量 `baseUrl = http://localhost:8080/api`
2. 添加全局 Header: `Authorization: Bearer {{token}}`
3. 先调用 `POST /auth/login` 获取 token 并保存到变量
4. 按模块逐一测试各接口

---

## 技术栈

- **框架**: Spring Boot 3.x + Spring Security
- **数据库**: MySQL + MyBatis-Plus
- **向量数据库**: ChromaDB（`http://localhost:8000`）
- **AI 模型**: Ollama（`http://localhost:11434`）+ LangChain4j
- **Embedding**: 阿里云 DashScope（`text-embedding-v4`）
- **文件存储**: MinIO（`http://localhost:9000`）
- **认证**: JWT（无状态）

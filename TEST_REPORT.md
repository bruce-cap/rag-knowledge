# 后端 API 测试报告

**测试时间**: 2026-04-11
**测试环境**: http://localhost:8080
**测试账号**: testuser001 / Test123456 (userId: 7)

---

## 一、测试流程（正确顺序）

建议按以下顺序测试：

```
1. 注册/登录        → 获取 JWT Token
2. 创建会话        → 获取 sessionId
3. 上传文档        → 供 RAG 模式使用（可选）
4. 等待文档处理    → status: 0→1→2（约3-5秒）
5. RAG 模式聊天    → ragMode=true（基于知识库回答）
6. 普通模式聊天    → ragMode=false（自由回答）
7. 流式聊天        → SSE 实时响应
8. 消息管理        → 历史/编辑/删除
9. 会话管理        → 列表/删除
10. 文档管理        → 列表/搜索/上传
```

---

## 二、认证模块 (`/api/auth`)

### 2.1 用户登录
```
POST /api/auth/login
```
**请求**:
```json
{
  "username": "testuser001",
  "password": "Test123456"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "role": "USER",
    "userId": 7,
    "token": "eyJhbGci...",
    "username": "testuser001"
  }
}
```
✅ **返回 JWT Token 正常**

### 2.2 用户注册
```
POST /api/auth/register
```
**请求**:
```json
{
  "username": "testuser001",
  "password": "Test123456",
  "confirmPassword": "Test123456",
  "email": "test@example.com"
}
```
**响应**: `{"code":200,"message":"操作成功","data":"注册成功"}` ✅

### 2.3 登出
```
POST /api/auth/logout
```
**响应**: `{"code":200,"message":"成功退出登录","data":null}` ✅

---

## 三、会话管理模块 (`/api/session`)

> **聊天前必须先创建会话**

### 3.1 创建会话
```
POST /api/session/create
```
**响应**: `{"code":200,"message":"操作成功","data":88}` ✅
返回新建会话 ID（后续聊天需使用）

### 3.2 获取会话列表
```
GET /api/session/list
```
**响应**:
```json
{
  "code": 200,
  "data": [
    {"id": 88, "userId": 7, "title": "RAG技术是什么", ...},
    {"id": 87, "userId": 7, "title": "RAG是什么意思", ...}
  ]
}
```
✅ 返回用户所有会话，标题自动生成为首条消息内容（上限15字）

### 3.3 删除会话
```
DELETE /api/session/{id}
```
**响应**: `{"code":200,"message":"删除成功","data":null}` ✅

---

## 四、文档管理模块 (`/api/document`)

> **RAG 模式聊天前需先上传文档并等待处理完成**

### 4.1 上传文档
```
POST /api/document/upload
Content-Type: multipart/form-data
```

| 参数 | 类型 | 说明 |
|------|------|------|
| file | File | 文件（最大50MB） |
| isPublic | Boolean | 是否公开（默认false） |

**测试请求**:
```bash
curl -X POST http://localhost:8080/api/document/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@rag_test.txt" \
  -F "isPublic=false"
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": 30,
    "userId": 7,
    "fileName": "rag_test.txt",
    "minioPath": "documents/7/8bd692b5-...txt",
    "fileSize": 150,
    "status": 0,
    "isPublic": false
  }
}
```
✅ 上传成功，status=0 表示待处理

### 4.2 获取文档列表
```
GET /api/document/list
```
**响应示例**:
```json
{
  "code": 200,
  "data": [
    {"id": 30, "fileName": "rag_test.txt", "status": 2, ...},
    {"id": 29, "fileName": "test_ai.txt", "status": 2, ...}
  ]
}
```
✅ status 说明: 0=待处理, 1=处理中, 2=已完成

### 4.3 向量搜索
```
POST /api/document/search
```

**请求**:
```json
{
  "query": "RAG",
  "limit": 5
}
```

**响应**:
```json
{
  "code": 200,
  "data": [
    {"score": 0.91, "documentId": "28", "text": "RAG technology content..."},
    {"score": 0.82, "documentId": "30", "text": "RAG is a technique..."}
  ]
}
```
✅ 向量检索功能正常，中英文均支持

---

## 五、聊天模块 (`/api/chat`)

### 5.1 流式发送消息 (SSE) 【推荐】
```
POST /api/chat/send/stream
Content-Type: text/event-stream
```

**请求**:
```json
{
  "sessionId": 88,
  "message": "RAG技术是什么",
  "ragMode": true
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | Long | 是 | 会话 ID |
| message | String | 是 | 消息内容 |
| ragMode | Boolean | 否 | 是否启用 RAG（默认 true） |

**响应格式 (SSE)**:
```
data:{"text":"RAG"}
data:{"text":"技术"}
data:{"text":"是"}
...
event:finish
data:[DONE]
```

| 测试场景 | 结果 |
|---------|------|
| 英文 + ragMode=false | ✅ 正常流式输出 |
| 英文 + ragMode=true | ✅ 正常流式输出 |
| 中文消息 | ✅ 正常流式输出 |

### 5.2 同步发送消息 【已弃用】
```
POST /api/chat/send
```

> ⚠️ **已弃用**，建议使用流式接口 `/api/chat/send/stream`

| 测试场景 | 结果 |
|---------|------|
| 英文 + ragMode=true | ✅ 正常 |
| 英文 + ragMode=false | ✅ 正常 |
| 中文 + ragMode=true | ✅ 正常 |

---

## 六、消息管理模块 (`/api/message`)

### 6.1 获取消息历史
```
GET /api/message/list/{sessionId}
```
**响应**:
```json
{
  "code": 200,
  "data": [
    {"id": 876, "sessionId": 88, "role": "user", "content": "RAG技术是什么", ...},
    {"id": 877, "sessionId": 88, "role": "assistant", "content": "RAG技术是...", ...}
  ]
}
```
✅ 按时间升序返回，role 区分用户/AI消息

### 6.2 编辑消息
```
PUT /api/message/edit/{id}
```
**请求**: `{"content": "RAG技术原理是什么"}`
**响应**: `{"code":200,"message":"编辑成功","data":null}` ✅

### 6.3 删除消息
```
DELETE /api/message/delete/{id}
```
**响应**: `{"code":200,"message":"删除成功","data":null}` ✅

---

## 七、完整测试流程示例

```bash
# 1. 登录获取 Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser001","password":"Test123456"}' | jq -r '.data.token')

# 2. 创建会话
SESSION_ID=$(curl -s -X POST http://localhost:8080/api/session/create \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data')

# 3. 上传文档（供 RAG 使用）
curl -X POST http://localhost:8080/api/document/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@rag_test.txt" -F "isPublic=false"

# 4. 等待文档处理（约3-5秒）
sleep 5

# 5. RAG 模式聊天
curl -X POST http://localhost:8080/api/chat/send/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"sessionId\":$SESSION_ID,\"message\":\"RAG技术是什么\",\"ragMode\":true}"

# 6. 普通模式聊天
curl -X POST http://localhost:8080/api/chat/send/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"sessionId\":$SESSION_ID,\"message\":\"你好\",\"ragMode\":false}"
```

---

## 八、测试结果总览

| 模块 | 接口 | 方法 | 状态 | 备注 |
|------|------|------|------|------|
| **认证** | /api/auth/register | POST | ✅ | |
| | /api/auth/login | POST | ✅ | |
| | /api/auth/logout | POST | ✅ | |
| **会话** | /api/session/create | POST | ✅ | 聊天前置步骤 |
| | /api/session/list | GET | ✅ | |
| | /api/session/{id} | DELETE | ✅ | |
| **文档** | /api/document/upload | POST | ✅ | RAG前置步骤 |
| | /api/document/list | GET | ✅ | |
| | /api/document/search | POST | ✅ | |
| **聊天** | /api/chat/send/stream | POST | ✅ | 推荐使用 |
| | /api/chat/send | POST | ✅ | 已弃用 |
| **消息** | /api/message/list/{id} | GET | ✅ | |
| | /api/message/edit/{id} | PUT | ✅ | |
| | /api/message/delete/{id} | DELETE | ✅ | |

**总计**: 13个接口全部通过 ✅

---

## 九、说明事项

1. **测试顺序**: 必须先创建会话才能聊天，RAG 模式需先上传文档
2. **curl 中文编码**: 命令行直接传递中文参数可能存在编码问题，建议使用文件输入或前端工具
3. **JWT Token**: 登出后 Token 失效，需重新登录
4. **文档处理**: 上传后 status 从 0→1→2，需等待约 3-5 秒
5. **会话标题**: 首条消息内容自动截取作为标题（上限15字）
6. **弃用说明**: `/api/chat/send` 已弃用，请使用 `/api/chat/send/stream`

---

## 十、测试账号

| 字段 | 值 |
|------|-----|
| 用户名 | testuser001 |
| 密码 | Test123456 |
| userId | 7 |
| Token | eyJhbGciOiJIUzI1NiJ9...（已过期） |

---

*报告生成时间: 2026-04-11*

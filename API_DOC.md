# RAG Backend API 文档

## 1. 基本信息

- Base URL: `http://localhost:8080`
- 认证方式: JWT
- Token 传递方式:
  - `Authorization: Bearer <token>`
  - 或登录后由后端写入 `jwt_token` Cookie
- 返回结构:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

常见状态码语义:

- `200`: 成功
- `400`: 请求参数错误
- `401`: 未授权或 Token 已失效
- `403`: 无权限
- `404`: 资源不存在
- `500`: 服务器内部错误

## 2. 数据模型说明

### 2.1 用户

- 系统角色:
  - `ADMIN`
  - `USER`

### 2.2 知识空间

- 文档必须属于某个知识空间
- 公共可见能力通过系统公共空间实现
- 普通用户可访问自己加入的空间
- 管理员可访问全部空间

### 2.3 文件夹

- 文件夹属于某个知识空间
- 文件夹用于空间内分类，不单独承担权限职责

### 2.4 文档状态

- `0`: 待处理
- `1`: 处理中
- `2`: 处理完成
- `3`: 处理失败

## 3. 认证模块

### 3.1 注册

`POST /api/auth/register`

请求体:

```json
{
  "username": "alice",
  "password": "abc12345",
  "confirmPassword": "abc12345",
  "email": "alice@example.com"
}
```

说明:

- 用户名、密码、确认密码、邮箱必填
- 密码规则: 8-16 位，必须同时包含字母和数字
- 注册成功后会自动加入系统公共知识空间

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "注册成功"
}
```

### 3.2 登录

`POST /api/auth/login`

请求体:

```json
{
  "username": "alice",
  "password": "abc12345"
}
```

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "jwt-token",
    "username": "alice",
    "userId": 1,
    "role": "USER"
  }
}
```

### 3.3 登出

`POST /api/auth/logout`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "成功退出登录"
}
```

## 4. 会话模块

### 4.1 创建会话

`POST /api/session/create`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 123
}
```

说明:

- `data` 为新建会话 ID

### 4.2 获取我的会话列表

`GET /api/session/list`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 123,
      "userId": 1,
      "title": "新对话",
      "createTime": "2026-04-19T10:00:00",
      "updateTime": "2026-04-19T10:05:00",
      "isDeleted": 0
    }
  ]
}
```

### 4.3 删除会话

`DELETE /api/session/delete/{id}`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "删除成功"
}
```

失败响应示例:

```json
{
  "code": 500,
  "message": "删除失败或无权限",
  "data": null
}
```

## 5. 聊天模块

### 5.1 普通聊天

`POST /api/chat/send`

说明:

- 该接口已标记为 `Deprecated`
- 建议使用流式接口 `/api/chat/send/stream`

请求体:

```json
{
  "sessionId": 123,
  "message": "什么是RAG？",
  "ragMode": true
}
```

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "RAG 是检索增强生成..."
}
```

### 5.2 流式聊天

`POST /api/chat/send/stream`

`Content-Type: application/json`

`Accept: text/event-stream`

请求体:

```json
{
  "sessionId": 123,
  "message": "帮我总结这个知识库",
  "ragMode": true
}
```

事件说明:

- 普通 token 事件: `data: {"text":"..." }`
- 完成事件: `event: finish`, `data: [DONE]`
- 错误事件: `event: error`

说明:

- 会话必须属于当前用户
- `ragMode=true` 时启用基于知识空间权限的向量检索

## 6. 消息模块

### 6.1 获取会话消息列表

`GET /api/message/list/{sessionId}`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "sessionId": 123,
      "role": "user",
      "content": "你好",
      "createTime": "2026-04-19T10:00:00"
    },
    {
      "id": 2,
      "sessionId": 123,
      "role": "assistant",
      "content": "你好，我是知识库助手",
      "createTime": "2026-04-19T10:00:02"
    }
  ]
}
```

### 6.2 编辑消息

`PUT /api/message/edit/{id}`

请求体:

```json
{
  "content": "修改后的消息内容"
}
```

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "Message updated successfully"
}
```

### 6.3 删除消息

`DELETE /api/message/delete/{id}`

说明:

- 该接口已标记为 `Deprecated`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "Message deleted successfully"
}
```

## 7. 文档模块

### 7.1 上传文档

`POST /api/document/upload`

`Content-Type: multipart/form-data`

表单参数:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传文件 |
| spaceId | Long | 是 | 所属知识空间 ID |
| folderId | Long | 否 | 所属文件夹 ID |

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "spaceId": 10,
    "folderId": 100,
    "userId": 1,
    "fileName": "manual.pdf",
    "minioPath": "documents/1/uuid.pdf",
    "fileSize": 102400,
    "fileType": "pdf",
    "status": 0,
    "createTime": "2026-04-19T10:00:00",
    "updateTime": "2026-04-19T10:00:00",
    "isDeleted": false,
    "deleteTime": null,
    "errorMessage": null
  }
}
```

说明:

- 文档必须归属到某个知识空间
- 非空间成员不能上传到该空间
- 上传成功后异步进行解析、切片和向量化

### 7.2 获取文档列表

`GET /api/document/list`

查询参数:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| spaceId | Long | 否 | 按知识空间过滤 |
| folderId | Long | 否 | 按文件夹过滤 |

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "spaceId": 10,
      "folderId": 100,
      "userId": 1,
      "fileName": "manual.pdf",
      "minioPath": "documents/1/uuid.pdf",
      "fileSize": 102400,
      "fileType": "pdf",
      "status": 2,
      "createTime": "2026-04-19T10:00:00",
      "updateTime": "2026-04-19T10:05:00",
      "isDeleted": false,
      "deleteTime": null,
      "errorMessage": null
    }
  ]
}
```

说明:

- 普通用户只能看到自己可访问空间中的文档
- 管理员可查看全部文档

### 7.3 删除文档

`DELETE /api/document/delete/{id}`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "Document deleted successfully"
}
```

说明:

- 仅文档上传者或管理员可删除
- 删除时会尝试同时清理 MinIO 对象和向量数据

### 7.4 向量检索

`POST /api/document/search`

请求体:

```json
{
  "query": "什么是RAG",
  "limit": 5,
  "minScore": 0.5,
  "spaceId": 10,
  "folderId": 100
}
```

字段说明:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | 是 | 检索文本 |
| limit | Integer | 否 | 返回条数，默认 5 |
| minScore | Double | 否 | 最低相似度，默认 0.5 |
| spaceId | Long | 否 | 指定知识空间 |
| folderId | Long | 否 | 指定文件夹 |

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "text": "RAG 是检索增强生成技术",
      "score": 0.89,
      "documentId": "1",
      "spaceId": "10",
      "folderId": "100"
    }
  ]
}
```

说明:

- 普通用户只能检索自己有权限访问的空间
- 管理员可检索任意空间

## 8. 知识空间模块

### 8.1 获取可访问知识空间列表

`GET /api/space/list`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 10,
      "name": "公共知识库",
      "code": "PUBLIC",
      "type": "SYSTEM_PUBLIC",
      "isSystem": true,
      "description": "System-managed public knowledge space",
      "status": 1,
      "createBy": 0,
      "createTime": "2026-04-19T10:00:00",
      "updateTime": "2026-04-19T10:00:00"
    }
  ]
}
```

### 8.2 创建知识空间

`POST /api/space/create`

请求体:

```json
{
  "name": "研发部知识库",
  "description": "研发部内部资料"
}
```

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 20,
    "name": "研发部知识库",
    "code": null,
    "type": "BUSINESS",
    "isSystem": false,
    "description": "研发部内部资料",
    "status": 1,
    "createBy": 1,
    "createTime": "2026-04-19T10:00:00",
    "updateTime": "2026-04-19T10:00:00"
  }
}
```

说明:

- 创建者会自动成为该空间 `ADMIN`

### 8.3 更新知识空间

`PUT /api/space/update/{id}`

请求体:

```json
{
  "name": "研发知识库",
  "description": "更新后的描述",
  "status": 1
}
```

### 8.4 删除知识空间

`DELETE /api/space/delete/{id}`

说明:

- 系统空间不可删除
- 空间下仍有文件夹或文档时不可删除

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "Knowledge space deleted successfully"
}
```

### 8.5 获取空间成员

`GET /api/space/{id}/members`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "spaceId": 20,
      "userId": 1,
      "role": "ADMIN",
      "joinTime": "2026-04-19T10:00:00"
    }
  ]
}
```

### 8.6 添加空间成员

`POST /api/space/{id}/members`

请求体:

```json
{
  "userId": 2,
  "role": "MEMBER"
}
```

### 8.7 移除空间成员

`DELETE /api/space/{id}/members/{memberUserId}`

说明:

- 系统空间成员不可移除

## 9. 文件夹模块

### 9.1 获取文件夹树

`GET /api/folder/tree?spaceId=20`

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 100,
      "spaceId": 20,
      "parentId": null,
      "name": "产品文档",
      "createBy": 1,
      "createTime": "2026-04-19T10:00:00",
      "updateTime": "2026-04-19T10:00:00",
      "children": []
    }
  ]
}
```

### 9.2 创建文件夹

`POST /api/folder/create`

请求体:

```json
{
  "spaceId": 20,
  "parentId": null,
  "name": "产品文档"
}
```

### 9.3 更新文件夹

`PUT /api/folder/update/{id}`

请求体:

```json
{
  "name": "新文件夹名",
  "parentId": 101
}
```

### 9.4 删除文件夹

`DELETE /api/folder/delete/{id}`

说明:

- 有子文件夹时不可删除
- 有文档时不可删除

成功响应:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "Folder deleted successfully"
}
```

## 10. 测试接口

### 10.1 健康检查

`GET /api/test/test1`

成功响应:

```text
Spring Boot backend is running!
```

### 10.2 测试用户查询

`GET /api/test/user`

说明:

- 当前实现为测试用途
- 返回 `sys_user` 中 ID=1 的用户字符串
- 不建议在生产环境保留

## 11. 错误响应示例

### 11.1 未登录

```json
{
  "code": 401,
  "message": "未授权或Token已失效",
  "data": null
}
```

### 11.2 无权限访问空间

```json
{
  "code": 403,
  "message": "You do not have permission to access this space",
  "data": null
}
```

### 11.3 请求体错误

```json
{
  "code": 400,
  "message": "Request body is invalid or unreadable",
  "data": null
}
```

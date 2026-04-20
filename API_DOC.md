# RAG Backend API DOC

## 1. Basic

- Base URL: `http://localhost:8080`
- Auth:
  - `Authorization: Bearer <token>`
  - or `jwt_token` cookie
- Unified response:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

## 2. Auth

### `POST /api/auth/register`

```json
{
  "username": "alice",
  "password": "abc12345",
  "confirmPassword": "abc12345",
  "email": "alice@example.com"
}
```

Rules:

- passwords must match
- password must be 8-16 chars and contain letters and digits
- successful registration auto-joins public space with `VIEW` role

### `POST /api/auth/login`

```json
{
  "username": "alice",
  "password": "abc12345"
}
```

Response data example:

```json
{
  "token": "jwt-token",
  "username": "alice",
  "userId": 1,
  "role": "USER",
  "status": 1,
  "nickname": "alice"
}
```

### `POST /api/auth/logout`

Clears auth cookie.

## 3. Session

### `POST /api/session/create`

Create a new chat session.

### `GET /api/session/list`

List current user's sessions.

Sort order:

- `isPinned desc`
- `pinTime desc`
- `updateTime desc`

### `DELETE /api/session/delete/{id}`

Delete current user's session.

### `PUT /api/session/updateTitle/{id}`

```json
{
  "title": "新的会话标题"
}
```

### `PUT /api/session/pin/{id}`

Pin a session.

### `PUT /api/session/unpin/{id}`

Unpin a session.

### `GET /api/session/exportMd/{id}`

Export session content as Markdown file.

## 4. Chat

### `POST /api/chat/send`

Deprecated non-streaming chat.

### `POST /api/chat/send/stream`

Streaming chat with SSE.

Request body for both:

```json
{
  "sessionId": 1,
  "message": "帮我总结这个空间里的文档",
  "ragMode": true,
  "spaceId": 10,
  "folderId": 100
}
```

Rules:

- `folderId` requires `spaceId`
- request session must belong to current user
- if `ragMode=true` and `spaceId` is set, ordinary user must have access to that space
- if both `spaceId` and `folderId` are null, search all accessible spaces

SSE events:

- `data: {"text":"..."}`
- `event: finish`
- `event: error`

## 5. Message

### `GET /api/message/list/{sessionId}`

List messages in a session.

### `PUT /api/message/edit/{id}`

```json
{
  "content": "修改后的内容"
}
```

### `DELETE /api/message/delete/{id}`

Deprecated.

## 6. Document

### `POST /api/document/upload`

`multipart/form-data`

Fields:

- `file` required
- `spaceId` required
- `folderId` optional

Rules:

- only `ADMIN` / `MEMBER` / super admin can upload
- `VIEW` cannot upload
- async processing starts after upload

### `GET /api/document/list`

Query params:

- `spaceId` optional
- `folderId` optional

### `DELETE /api/document/delete/{id}`

Allowed:

- uploader
- space admin
- super admin

### `POST /api/document/retry/{id}`

Retry failed document processing.

Rules:

- only failed documents can be retried
- old vectors are cleared before retry
- allowed for uploader / space admin / super admin

### `GET /api/document/download/{id}`

Download original file.

### `GET /api/document/preview/{id}`

Inline preview original file.

### `POST /api/document/search`

```json
{
  "query": "什么是RAG",
  "limit": 5,
  "minScore": 0.5,
  "spaceId": 10,
  "folderId": 100
}
```

Rules:

- `folderId` requires `spaceId`
- ordinary user cannot search an unauthorized space
- when no `spaceId` and `folderId` are given, search all accessible spaces

Response item example:

```json
{
  "text": "匹配到的内容片段",
  "score": 0.89,
  "documentId": "1",
  "spaceId": "10",
  "folderId": "100"
}
```

## 7. Space

### `GET /api/space/list`

List accessible spaces for current user.

### `GET /api/space/listAll`

List joinable business spaces.

Rules:

- excludes system spaces
- for ordinary users, excludes already joined spaces

### `POST /api/space/create`

Super admin only.

```json
{
  "name": "研发知识库",
  "description": "研发部门空间"
}
```

### `PUT /api/space/update/{id}`

Update a space.

### `DELETE /api/space/delete/{id}`

Super admin only.

### `GET /api/space/{id}/members`

List members of a space.

### `POST /api/space/{id}/members`

Invite a user directly into the space.

```json
{
  "userId": 2,
  "role": "MEMBER"
}
```

Rules:

- super admin can invite as `VIEW`, `MEMBER`, `ADMIN`
- space admin can invite as `VIEW`, `MEMBER`
- space admin cannot directly invite a user as `ADMIN`

### `DELETE /api/space/{id}/members/{memberUserId}`

Remove a member from the space.

### `PUT /api/space/{id}/members/updateRole/{memberUserId}`

```json
{
  "role": "MEMBER"
}
```

Allowed roles:

- `VIEW`
- `MEMBER`
- `ADMIN`

Rules:

- super admin can adjust any member role
- space admin can adjust lower roles only
- space admin cannot adjust another `ADMIN`
- space admin cannot promote a member to `ADMIN`
- space admin cannot adjust their own role directly

## 8. Space Join Request

### `POST /api/space/request/create`

```json
{
  "spaceId": 10,
  "targetRole": "VIEW",
  "applyReason": "需要访问该部门空间"
}
```

Rules:

- ordinary user only
- supported targets: `VIEW`, `MEMBER`, `ADMIN`
- super admin can approve any target role
- space admin can approve `VIEW`, `MEMBER`
- space admin cannot approve a join request whose target role is `ADMIN`

### `GET /api/space/request/myList`

List current user's join requests.

### `GET /api/space/request/list`

Query params:

- `spaceId` optional

Allowed:

- super admin
- space admin for managed spaces

### `PUT /api/space/request/approve/{id}`

Optional body:

```json
{
  "reviewReason": "审批通过"
}
```

### `PUT /api/space/request/reject/{id}`

Optional body:

```json
{
  "reviewReason": "审批驳回原因"
}
```

## 9. Space Role Request

### `POST /api/space/roleRequest/create`

```json
{
  "spaceId": 10,
  "targetRole": "ADMIN",
  "applyReason": "希望在该空间承担管理员职责"
}
```

Rules:

- super admin does not need to apply
- `VIEW` can apply for `MEMBER` or `ADMIN`
- `MEMBER` can apply for `ADMIN`
- `ADMIN` should not create another role-upgrade request
- super admin can approve any target role
- space admin can approve `VIEW`, `MEMBER`
- space admin cannot approve `ADMIN`

### `GET /api/space/roleRequest/myList`

List current user's role-upgrade requests.

### `GET /api/space/roleRequest/list`

Query params:

- `spaceId` optional

### `PUT /api/space/roleRequest/approve/{id}`

Optional body:

```json
{
  "reviewReason": "审批通过"
}
```

### `PUT /api/space/roleRequest/reject/{id}`

Optional body:

```json
{
  "reviewReason": "审批驳回原因"
}
```

## 10. Folder

### `GET /api/folder/tree?spaceId=20`

Get folder tree under a space.

### `POST /api/folder/create`

```json
{
  "spaceId": 20,
  "parentId": null,
  "name": "产品文档"
}
```

### `PUT /api/folder/update/{id}`

Update folder name or parent relation.

### `DELETE /api/folder/delete/{id}`

Delete folder.

## 11. User

### `GET /api/user/list`

Super admin only.

### `GET /api/user/detail/{id}`

Super admin only.

### `PUT /api/user/updateStatus/{id}`

Super admin only.

```json
{
  "status": 0
}
```

### `GET /api/user/profile`

Get current user profile.

### `PUT /api/user/updateProfile`

```json
{
  "nickname": "张三",
  "email": "zhangsan@example.com",
  "avatar": "https://example.com/avatar.png",
  "phone": "13800000000"
}
```

### `PUT /api/user/updatePassword`

```json
{
  "oldPassword": "abc12345",
  "newPassword": "abc12346",
  "confirmPassword": "abc12346"
}
```

## 12. Test

### `GET /api/test/test1`

Health test.

### `GET /api/test/user`

Test-only endpoint.

## 13. Unified Error Examples

### unauthenticated

```json
{
  "code": 401,
  "message": "Authentication required",
  "data": null
}
```

### access denied

```json
{
  "code": 403,
  "message": "Access denied",
  "data": null
}
```

### bad request

```json
{
  "code": 400,
  "message": "Request body is invalid or unreadable",
  "data": null
}
```

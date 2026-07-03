# wellness-ca 项目总结

## 项目概览

NUS-ISS SA62 健康管理 App。Android 端 Kotlin + 后端 Spring Boot + Python RAG/Agent sidecar。

---

## 服务器

| 项目 | 值 |
|---|---|
| IP | `152.42.181.66` |
| SSH | `root@152.42.181.66` |
| 后端代码路径 | `/home/wellness-dev/wellness-backend` |
| 端口 | 8000 |
| 管理方式 | systemd: `wellness-backend.service` |
| 数据库 | Aiven MySQL (云, SSL) |
| Web 地址 | `http://152.42.181.66:8000/web/login` |

**部署命令：**
```bash
# 上传修改后的文件
scp src/... root@152.42.181.66:/home/wellness-dev/wellness-backend/src/...

# 构建 + 重启
ssh root@152.42.181.66 "cd /home/wellness-dev/wellness-backend && mvn package -DskipTests -q && systemctl restart wellness-backend && sleep 35"
```

---

## 本地仓库

| 位置 | `D:\AndroidStudioProjects\wellness-ca-experiment` |
|---|---|
| GitHub | `https://github.com/wushi2333/wellness-ca` |
| 开发分支 | `experiment` |
| 工作目录 | 在 repo 根目录运行 git 命令（不是 Service_Backend 内） |

### CI/CD

- 每次 push main → GitHub Actions 自动构建 APK → 创建 Release
- 工作流文件：`.github/workflows/release.yml`

---

## 后端架构 (`Service_Backend/`)

### REST API

| 端点 | 说明 |
|---|---|
| `POST /register`, `POST /login` | JWT 认证 |
| `GET/POST /records`, `PUT/DELETE /records/{id}` | 健康记录 CRUD |
| `POST /chat` | 基础 AI 聊天 |
| `POST /character/chat` | Yui 聊天模式 |
| `POST /character/agent` | Agent 模式（含 wellness 数据分析 + 页面导航） |
| `POST /character/tts` | 火山 TTS 语音合成 |
| `POST /character/asr` | 火山 ASR 语音识别 |
| `GET /character/sessions` | 会话列表 |
| `POST /character/sessions` | 创建会话 |
| `DELETE /character/sessions/{id}` | 删除会话 |
| `GET /character/sessions/{id}/messages` | 消息历史 |

### Web UI（Thymeleaf，Author: Guo Jiali）

| 页面 | 路由 |
|---|---|
| 登录 | `/web/login` |
| 注册 | `/web/register` |
| 仪表盘 | `/web/dashboard` |
| 记录列表 | `/web/records` |
| 新建/编辑 | `/web/records/new`, `/web/records/{id}/edit` |
| 详情 | `/web/records/{id}` |
| AI 聊天 | `/web/chat` |
| AI 推荐 | `/web/insights` |

**Web 认证：** 走 HttpSession，不走 JWT。`GatewayFilter` 和 `JwtAuthFilter` 已放行 `/web/**`、`/css/**`、`/js/**`。

### 关键文件

| 文件 | 说明 |
|---|---|
| `CharacterService.java` | Yui 聊天核心。使用 DeepSeek API，安全 prompt，mode 感知 |
| `CharacterMemoryService.java` | 记忆系统：用户画像提取 + 上下文压缩 + 标题生成 |
| `CharacterTtsService.java` | 火山 V1 TTS（语速+音调情绪控制） |
| `CharacterAsrService.java` | 火山 BigModel ASR（flash 端点） |
| `ChatService.java` | 基础聊天（已修复 RAG `user_id` 字段名） |
| `WellnessService.java` | 健康记录 CRUD。含 `findOwnedRecord` 防越权 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` 全局异常处理 |
| `SecurityConfig.java` | 放行 `/web/**`；STATELESS session |
| `GatewayFilter.java` | X-API-Token 校验（放行 web 路径） |
| `JwtAuthFilter.java` | JWT 校验（放行 web 路径） |
| `WellnessRequest.java` | **有 public 字段 + getter/setter（Thymeleaf 需要）** |

---

## Android 端 (`CA_Application/`)

### 主要功能

| 功能 | 文件 |
|---|---|
| Dashboard + Agent 弹窗 | `MainActivity.kt` |
| Yui Live2D 聊天页 | `character/CharacterChatActivity.kt` |
| 聊天适配器 | `character/CharacterChatAdapter.kt` |
| Agent 弹窗适配器 | `character/AgentPopupAdapter.kt` |
| 火山 TTS 播放 | `character/VolcanoTtsService.kt` |
| Live2D 模型控制 | `live2d/LAppMinimumModel.java` |
| Live2D 模型管理 | `live2d/LAppMinimumLive2DManager.java` |
| Live2D 委托 | `live2d/LAppMinimumDelegate.java` |
| Live2D 视图 | `live2d/Live2DCharacterView.kt` |
| API 客户端 | `network/CharacterApi.kt`, `network/ApiClient.kt` |
| Token 管理 | `auth/TokenManager.kt`（含用户名存储） |

### 数据模型

- `CharacterChatRequest/Response` — Yui 聊天请求/响应（含 `tools` 字段）
- `CharacterMessage` — 聊天消息（含 `tools: List<String>?`）
- `CharacterSession` — 会话对象

### 已实现的关键特性

1. **Live2D 悠小喵** — 呼吸/眨眼/头部摆动/表情映射/嘴型同步/水印去除
2. **Chat/Agent 模式切换** — 不同系统 prompt，Agent 可导航页面
3. **语音输入** — 按住 🎤 → AudioRecord PCM 16kHz → Base64 → 火山 ASR
4. **语音输出** — 火山 V1 TTS，情绪驱动语速/音调，Live2D 嘴型同步
5. **工具可视化** — Agent 气泡下灰色 `tools▸`，点击展开工具列表
6. **Agent 弹窗**（Dashboard）— FAB 按钮 → 底部弹窗，无 Live2D，支持 TTS/ASR/拖拽/Scrim
7. **多选删除** — 侧边栏 🗑 图标进入选择模式，支持 Pin/Delete/Delete All
8. **冷启动欢迎** — `@string/yui_welcome` 带用户名，纯本地不存服务端
9. **延迟创建会话** — 不发消息不创建 session，防止空会话堆积
10. **Live2D 缓存** — `onDestroy()` 不释放 CPU 模型数据，热启动秒开
11. **矢量图标** — `ic_send.xml`、`ic_back.xml`、`ic_trash.xml`

---

## 已知问题 / 待优化

1. **TTS 嘴型是正弦波模拟** — 非真实音频振幅驱动，效果 OK 但不完美
2. **Web 页面未登录体验** — 直接访问 `/web/records` 等会 302 到登录页，但登录仍是 API 接口而非 Web 表单登录（Web UI 登录功能还未完成）
3. **Agent 弹窗 TTS** — 没有 Live2D 嘴型同步，`onMouth` 为空
4. **"Skipped X frames" 警告** — Live2D GLSurfaceView 创建时主线程短暂拥塞，不影响功能
5. **Web UI 和 API 认证分离** — Web 用 HttpSession，API 用 JWT，`SecurityConfig` 中 `SessionCreationPolicy.STATELESS` 可能影响 Web session
6. **Agent 模式没有实际调用工具** — `tools_used` 是 LLM 在 prompt 里"模拟"的，不是真正的 function calling

---

## 团队成员

| 模块 | 作者 |
|---|---|
| Spring Boot 后端基础 + Character 系统 | Xia Zihang |
| 数据库 JWT 加固 + 健康记录 | Yutong Luo |
| RAG chatbot + ChromaDB | Huang Qianer |
| Agentic AI 推荐 | Cai Peilin |
| Web UI (Thymeleaf) | Guo Jiali |
| Android app | Wang Songyu, Liu Yu, Xia Zihang |

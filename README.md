# ERP 智能助手 — Spring Boot + Spring AI + RAG

基于 Spring AI 构建的制造业 ERP 智能问答系统，围绕大语言模型整合 **Tool Calling** 与 **RAG** 两类能力，面向多租户场景提供多轮对话、流式输出、业务图表可视化、动态工具管理、调用追踪和计费能力。

> 详细设计见同目录下的《系统设计文档》。

## 一、项目简介

本系统采用单体 Spring Boot 应用，前后端一体部署：

- 后端：Java 17 + Spring Boot + Spring AI + Spring JDBC + MyBatis-Plus
- RAG：Spring AI `QuestionAnswerAdvisor` + PostgreSQL PgVector + 本地 ONNX Embedding
- 前端：原生 HTML / CSS / JavaScript + ECharts + marked.js + highlight.js
- 业务数据库：MySQL 8
- 对话模型：DeepSeek / 通义千问 / Google Gemini，多模型可切换

主要能力：

- `auto`、`data`、`knowledge` 三种问答模式。
- LLM 自动调用 8 大 ERP 模块代码 Tool 和数据库动态 Tool。
- 用户上传或预置知识文档，经过解析、分块、向量化后用于 RAG 检索。
- 会话记忆、SSE 流式输出、Markdown 渲染。
- 业务 Tool 结果图表可视化。
- 多租户隔离、计费管理、动态 Tool 管理、Tool 调用追踪。

## 二、技术架构

```text
浏览器
  │
  ├── 原生 HTML / CSS / JS + ECharts
  │
  │  HTTP /api/* 与 SSE /api/ask/stream
  ▼
Spring Boot 应用
  ├── 多租户上下文与数据隔离
  ├── LLM 路由、Tool Calling、RAG
  ├── 会话记录、计费、动态 Tool、图表协议
  │
  ├── PostgreSQL + PgVector：知识文档向量
  ├── MySQL 8：ERP 业务、会话、计费、动态 Tool、调用流水
  ├── DeepSeek / 通义千问 / Gemini：生成模型
  └── all-MiniLM-L6-v2（ONNX 本地推理）：文本嵌入
```

核心数据流：

```text
文档上传 -> Tika 解析 -> 分块 -> 本地 Embedding -> PgVector 写入

用户提问 -> 租户过滤 -> Tool Calling 查 ERP / PgVector 检索知识
        -> 上下文拼接 -> LLM 生成 -> 净化答案 -> 保存会话与计费
```

## 三、目录结构

```text
ERP 智能助手/
├── src/main/java/com/example/rag/
│   ├── RagDemoApplication.java       应用启动类
│   ├── chat/                         AI 对话核心
│   │   ├── ErpAssistantService       问答编排
│   │   ├── DocumentLoaderService     文档加载与向量化
│   │   ├── ModelRegistry             多模型路由
│   │   ├── client/                   ChatClient 装配
│   │   ├── lifecycle/                问答生命周期收口
│   │   ├── output/                   回答净化
│   │   ├── guard/                    业务数据守卫
│   │   └── chart/                    图表可视化管线
│   ├── controller/                   REST 入口
│   ├── conversation/                 对话历史与会话记忆
│   ├── billing/                      计费与用量
│   ├── tenant/                       租户管理
│   ├── tool/                         ERP Tool Calling
│   │   ├── admin/                    动态 Tool 管理
│   │   ├── dynamic/                  动态 SQL 执行
│   │   ├── registry/                 Tool 快照
│   │   └── trace/                    Tool 调用追踪
│   ├── dao/                          Entity 与 Mapper
│   ├── init/                         数据库初始化
│   ├── config/                       基础设施配置
│   └── vo/                           请求与响应对象
├── src/main/resources/
│   ├── application.yml               应用配置
│   ├── static/                       前端单页应用
│   ├── db/init/                      数据库初始化脚本
│   ├── docs/                         预置知识文档
│   └── models/embedding/             本地嵌入模型
├── src/test/                         单元测试与集成测试
├── openspec/                         规格驱动开发文档
├── deploy/settings.xml               Maven 镜像源
├── Dockerfile                        应用镜像构建
├── docker-compose.yml                中间件与应用编排
└── 系统设计文档.md                    系统设计文档
```

## 四、环境要求

| 组件 | 版本要求 | 说明 |
| --- | --- | --- |
| Java | 17+ | 建议使用 JDK 17 |
| Maven | 3.9+ | 单模块构建 |
| Docker | 最新稳定版 | 推荐使用 Docker Compose 启动 |
| PostgreSQL | 16 + PgVector | 向量数据库 |
| MySQL | 8.0 | ERP 业务库 |
| LLM API Key | 至少一个 | DeepSeek / 通义千问 / Gemini |

Windows 下检查环境：

```powershell
java -version
mvn -version
docker --version
```

应用启动时会自动初始化 MySQL 表结构和演示数据，向量库结构由 Spring AI PgVector 自动创建。

## 五、快速启动

建议顺序：中间件 -> 配置模型 Key -> 启动应用。

### 1. 启动中间件

推荐使用 Docker Compose 一键启动 PgVector 和 MySQL：

```powershell
docker compose up -d pgvector mysql
```

也可以单独启动：

```powershell
docker run -d --name pgvector -p 5432:5432 `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -e POSTGRES_DB=rag_demo `
  pgvector/pgvector:pg16

docker run -d --name mysql-erp -p 13306:3306 `
  -e MYSQL_ROOT_PASSWORD="你的mysql密码" `
  -e MYSQL_DATABASE=erp `
  -e MYSQL_CHARSET=utf8mb4 `
  mysql:8.0 `
  --character-set-server=utf8mb4 `
  --collation-server=utf8mb4_unicode_ci
```

### 2. 配置模型 Key

至少配置一个真实模型 Key：

```powershell
$env:DEEPSEEK_API_KEY="你的DeepSeekKey"
$env:DASHSCOPE_API_KEY="你的DashScopeKey"
$env:GOOGLE_GENAI_API_KEY="你的GeminiKey"
```

### 3. 启动应用

本地 Maven 启动：

```powershell
mvn clean spring-boot:run
```

或使用 Docker Compose 启动完整环境：

```powershell
$env:DEEPSEEK_API_KEY="你的DeepSeekKey"
$env:DASHSCOPE_API_KEY="你的DashScopeKey"
$env:GOOGLE_GENAI_API_KEY="你的GeminiKey"
$env:ERP_DB_PASSWORD="你的mysql密码"

docker compose up -d
```

访问：

```text
http://localhost:8080
```

## 六、后端配置

核心配置位于 `src/main/resources/application.yml`，也可通过环境变量覆盖。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 服务端口 |
| `app.tenant.column` | `ent_code` | 多租户列名 |
| `app.chat.memory.max-history-tokens` | `6000` | 会话记忆总 Token 预算 |
| `app.chat.memory.max-message-tokens` | `1500` | 单条历史消息 Token 上限 |
| `app.chat.memory.max-history-messages` | `20` | 历史消息条数上限 |
| `app.ai.call-timeout` | `120s` | 非流式调用超时 |
| `app.ai.stream-timeout` | `300s` | 流式调用超时 |
| `spring.datasource.pgvector.url` | `jdbc:postgresql://localhost:5432/rag_demo` | 向量库地址 |
| `spring.datasource.erp.url` | `jdbc:mysql://localhost:13306/erp...` | ERP 数据源地址 |

常用环境变量：

| 环境变量 | 说明 |
| --- | --- |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `DASHSCOPE_API_KEY` | 通义千问 / DashScope API Key |
| `GOOGLE_GENAI_API_KEY` | Google Gemini API Key |
| `ERP_DB_USER` | ERP 数据源用户名，默认 `root` |
| `ERP_DB_PASSWORD` | ERP 数据源密码 |
| `SPRING_DATASOURCE_PGVECTOR_URL` | PgVector 连接地址 |
| `SPRING_DATASOURCE_ERP_URL` | ERP 数据源连接地址 |

多模型配置示例：

```yaml
app:
  models:
    - id: deepseek-chat
      label: DeepSeek Chat（通用）
      provider: deepseek
      model-name: deepseek-chat
      default: true
    - id: qwen-max
      label: 通义千问 Max
      provider: openai
      model-name: qwen-max
```

`id` 用于前端传参，`provider` 对应 Spring AI ChatModel Bean，`model-name` 是实际传给服务商 API 的模型标识。

## 七、多租户与权限模型

当前系统不提供登录鉴权，而是通过请求 Header 标识租户和用户：

- `X-Ent-Code`：租户编码。
- `X-User-Id`：用户 ID。

后端行为：

- `TenantFilter` 拦截 `/api/**` 请求，将 Header 写入 `TenantContext`。
- 缺失租户标识时返回 400。
- 代码 ERP Tool 通过 `BaseTool` 自动追加 `ent_code` 条件。
- 动态数据库 Tool 执行时强制注入当前租户条件。
- MyBatis-Plus 通过租户插件自动追加 `ent_code` 条件。
- RAG 向量检索通过 `ent_code` 过滤文档。
- 全局配置表 `a_billing_plan`、`a_billing_price_rule`、`a_llm_tool` 不参与租户隔离。

## 八、主要接口

统一响应结构：

```json
{
  "success": true,
  "errCode": null,
  "errMsg": null,
  "data": {}
}
```

### 对话与文档

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ask` | 非流式问答 |
| GET | `/api/ask/stream` | SSE 流式问答 |
| GET | `/api/models` | 可用模型列表 |
| GET | `/api/hints` | 预置示例问题 |
| GET | `/api/search` | 文档向量检索 |
| POST | `/api/load` | 加载预置文档 |
| POST | `/api/upload` | 上传文档 |

### 会话历史

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/conversations` | 查询会话列表 |
| GET | `/api/conversations/{id}/messages` | 查询会话消息 |
| DELETE | `/api/conversations/{id}` | 归档会话 |

### 计费

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/billing/account` | 查询计费账户 |
| GET | `/api/billing/plans` | 查询套餐 |
| GET | `/api/billing/transactions` | 查询交易流水 |
| POST | `/api/billing/recharge` | 充值 |
| GET | `/api/billing/usage/daily` | 每日用量 |
| GET | `/api/billing/usage/monthly` | 月度用量 |

### 管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST / PUT | `/api/admin/billing/plans` | 套餐配置管理 |
| GET / POST / PUT | `/api/admin/billing/price-rules` | 计价规则管理 |
| GET / POST / PUT | `/api/admin/billing/invoices` | 账单管理 |
| GET / POST / PUT | `/api/admin/tenants` | 租户管理 |
| GET / POST / PUT | `/api/admin/tenants/users` | 租户用户管理 |
| GET / POST / PUT / DELETE | `/api/admin/tools` | 动态 Tool 管理 |
| POST | `/api/admin/tools/refresh` | 刷新 Tool 快照 |
| GET | `/api/admin/tools/call-logs` | Tool 调用流水 |

## 九、测试

后端单元测试：

```powershell
mvn test
```

生产构建检查：

```powershell
mvn clean package
```

建议的人工验收路径：

1. 启动 PgVector 和 MySQL。
2. 配置至少一个模型 API Key。
3. 启动应用后访问 `http://localhost:8080`。
4. 在“文档管理”加载预置文档或上传知识文档。
5. 在“智能”或“数据查询”模式测试业务数据查询。
6. 在“知识问答”模式测试文档检索。
7. 查看历史记录、计费管理和工具管理页面。

## 十、常见问题

### 应用启动报数据库连接失败

- 确认 PgVector 和 MySQL 已启动。
- 确认端口 `5432`、`13306` 未被占用。
- 确认数据源 URL、用户名和密码正确。

### 问答报模型服务不可用

- 确认已配置对应模型 API Key。
- 确认网络可访问模型服务商。
- 确认 `app.models` 中 `provider`、`model-name` 配置正确。

### 文档上传或检索失败

- 确认支持的文件格式为 PDF、Word、Excel、TXT 等。
- 确认 PgVector 初始化成功。
- 查看后端日志中的解析或向量化错误。

### 动态 Tool 不生效

- 确认动态 Tool 状态为 `active`。
- 保存后调用 `/api/admin/tools/refresh` 刷新快照。
- 确认 SQL 仅包含单层只读查询且参数声明合法。

### 请求返回缺少租户标识

- 确认前端请求已携带 `X-Ent-Code` 和 `X-User-Id` Header。
- 确认请求路径以 `/api/` 开头。

## 十一、安全与注意事项

- 当前租户识别依赖 HTTP Header，尚未接入登录鉴权，不应直接暴露到公网。
- API Key 通过环境变量注入，不要写入源码、镜像或日志。
- 动态 SQL 仅允许单层只读查询，并通过参数绑定和租户注入控制风险。
- 前端动态内容必须转义，防止 XSS。
- 生产部署前应增加统一登录、JWT 鉴权、权限控制和网关限流。
- 生产环境应收紧跨域、日志和密钥管理。

## 十二、默认 AI 与 RAG 参数

| 参数 | 默认值 |
| --- | --- |
| 知识问答召回数量 | 8 |
| 知识问答相似度阈值 | 0.25 |
| 常规 RAG 召回数量 | 5 |
| 常规 RAG 相似度阈值 | 0.5 |
| 会话记忆条数 | 20 |
| 会话记忆总 Token 预算 | 6000 |
| 单条历史消息 Token 预算 | 1500 |
| 非流式调用超时 | 120s |
| 流式调用超时 | 300s |
| 动态 Tool 默认返回行数 | 50 |
| 动态 Tool 最大返回行数 | 500 |

## 十三、后续可扩展方向

- 增加登录、JWT 鉴权和细粒度权限控制。
- 增加模型调用熔断、退避和限流。
- 引入真实模型分词器，替代当前 Token 估算逻辑。
- 将图表选择改为 JSON Schema 结构化输出。
- 增加查询改写与重排序，提升复杂问题准确率。
- 增加上传任务队列，避免大文件阻塞请求。
- 增加监控告警、审计日志和集中式日志。
- 增加向量库与文件备份。

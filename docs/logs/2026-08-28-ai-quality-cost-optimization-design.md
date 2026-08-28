# AI 质量与成本低风险优化设计文档

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | AI 质量与成本低风险优化设计 |
| 项目名称 | Spring AI RAG Demo |
| 归档日期 | 2026-08-28 |
| 文档状态 | 已完成实施并验证 |
| 适用范围 | `chat/`、`conversation/`、`config/`、`resources/application.yml` |

---

## 1. 背景

ERP 智能助手基于 Spring AI 构建，核心链路包括：

- `Tool Calling`：实时查询 ERP 业务数据。
- `RAG`：从 PgVector 检索知识文档。
- 会话记忆：为多轮对话提供上下文。
- 流式与非流式问答：SSE 输出和普通 REST 输出。

在功能趋于稳定后，需要对 AI 问答的 Token 成本、上下文增长和模型调用稳定性做一轮低风险优化，避免引入大规模架构变更或影响既有业务行为。

---

## 2. 现状与问题分析

### 2.1 系统提示词过长

`AssistantClientProvider` 中的系统提示词包含大量重复解释、分段规则和长句说明。每次模型调用都会完整携带这些提示词，导致：

- Prompt Token 持续偏高。
- 与多轮会话记忆叠加后，上下文更快膨胀。
- 维护成本增加，规则重复后容易产生不一致。

### 2.2 会话记忆固定窗口

原实现固定保留最近 20 条消息，未按 Token 数量控制。当助手回答包含长表格或长文本时，20 条消息仍可能占用大量 Token，甚至挤占模型上下文窗口。

### 2.3 模型调用缺少统一超时

非流式调用只有 Spring AI 自身的重试配置，缺少应用层统一超时；流式调用同样缺少显式超时控制。模型服务异常或网络抖动时，请求可能长时间阻塞或流悬挂。

---

## 3. 优化目标

1. 降低每轮 LLM 调用的 Prompt Token 成本。
2. 控制会话记忆上下文规模，避免无限膨胀。
3. 增加模型调用超时能力，提升系统稳定性。
4. 保持 `auto` 模式继续同时启用 RAG 和 Tool Calling。
5. 保持既有对外行为、最终答案净化逻辑和图表协议不变。
6. 不引入结构化输出、熔断、限流等高复杂度改造。

---

## 4. 方案设计

### 4.1 总体思路

本次优化限定在“提示词、会话记忆、模型调用超时”三个低风险方向，不改变 RAG 路由策略，不改变 API 契约。

| 优化项 | 主要手段 | 预期收益 |
|--------|----------|----------|
| 系统提示词精简 | 压缩重复规则，保留硬约束 | 降低 Prompt Token |
| 会话记忆 Token 预算 | 按单条和总预算截断 | 控制上下文成本 |
| 模型调用超时 | 应用层统一超时控制 | 避免长时间阻塞 |

### 4.2 系统提示词精简

重写 `AssistantClientProvider` 中的三个提示词：

- `SYSTEM_PROMPT`
- `KNOWLEDGE_SYSTEM_PROMPT`
- `CHART_SELECTION_SYSTEM_PROMPT`

保留的硬约束：

- `<!--FINAL_ANSWER-->` 边界标记。
- 中文回答要求。
- 禁止泄露 Tool 名、函数名、表名、字段名、SQL。
- 当前轮业务数据必须由当前轮 Tool 查询。
- 图表规划只填写 `type` 和 `title`。
- 支持的图表类型列表。
- Markdown 格式要求。

删除的内容：

- 重复的解释性段落。
- 冗余的示例说明。
- 可以合并的规则表述。

### 4.3 会话记忆 Token 预算截断

新增配置：

```yaml
app:
  chat:
    memory:
      max-history-tokens: 6000
      max-message-tokens: 1500
      max-history-messages: 20
```

处理流程：

1. 从 `a_chat_message` 读取最近 `max-history-messages` 条成功消息。
2. 移除末尾当前用户消息，避免与当前提问重复。
3. 从最新到最旧计算估算 Token。
4. 单条消息超过 `max-message-tokens` 时截断并追加省略号。
5. 累计超过 `max-history-tokens` 时停止保留更早消息。
6. 恢复时间正序后返回给 ChatMemory。

Token 估算规则：

- CJK 字符按 1 token。
- 连续 ASCII 字母/数字按每 4 字符 1 token 向上取整。
- 空白和标点不计入。

### 4.4 模型调用超时

新增配置：

```yaml
app:
  ai:
    call-timeout: 120s
    stream-timeout: 300s
```

- 非流式调用：在独立线程中执行，阻塞等待 `call-timeout`，超时抛 `BIZ_ERROR`。
- 流式调用：在响应流上应用 Reactor `timeout()`，超时后进入既有错误收口并输出 `error` 事件。

---

## 5. 详细实现

### 5.1 新增文件

| 文件 | 职责 |
|------|------|
| `config/ChatMemoryProperties.java` | 绑定会话记忆 Token 预算配置。 |
| `config/AiTimeoutProperties.java` | 绑定模型调用超时配置。 |
| `conversation/TokenEstimator.java` | 估算和截断历史消息 Token。 |
| `chat/ModelCallTimeout.java` | 非流式与流式模型调用的统一超时执行。 |

### 5.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `chat/client/AssistantClientProvider.java` | 精简三个系统提示词。 |
| `conversation/JdbcChatMemoryRepository.java` | 增加 Token 预算截断逻辑。 |
| `chat/ErpAssistantService.java` | 使用配置的消息窗口，并接入非流式超时。 |
| `chat/lifecycle/AssistantLifecycleService.java` | 对流式响应统一增加超时。 |
| `resources/application.yml` | 增加 `app.chat.memory` 和 `app.ai` 配置。 |

### 5.3 关键设计说明

#### 非流式超时线程上下文

`ModelCallTimeout` 在异步线程执行模型调用前，会恢复当前 `TenantContext`，执行结束后清理，避免租户上下文丢失。

#### 流式超时错误处理

流式超时触发后，现有 `onErrorResume` 会统一执行 `finalizeStream()`，保存错误消息并输出安全 `error` 事件，不发送半成品图表或 `done` 事件。

---

## 6. 测试验证

### 6.1 测试覆盖

- 新增 `TokenEstimatorTest`，覆盖空文本、CJK、ASCII、混合文本和截断。
- 扩展 `JdbcChatMemoryRepositoryTest`，覆盖单条超长截断、总预算丢弃旧消息。
- 更新既有测试构造方式，适配新增配置依赖。
- 更新 `ErpAssistantServiceTest` 中系统提示词断言，匹配精简后的提示词。

### 6.2 测试结果

```text
Tests run: 201, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 7. 配置清单

```yaml
app:
  chat:
    memory:
      max-history-tokens: 6000
      max-message-tokens: 1500
      max-history-messages: 20

  ai:
    call-timeout: 120s
    stream-timeout: 300s
```

---

## 8. 回滚与兼容

### 8.1 兼容性

- 未修改 REST 请求/响应结构。
- 未修改 SSE 事件协议。
- 未修改 `FINAL_ANSWER_MARKER` 字符串。
- 未修改图表类型枚举和 `ChartSpec` 协议。

### 8.2 回滚方式

- 提示词：恢复 `AssistantClientProvider` 中旧提示词常量即可。
- 会话记忆：将 `max-history-messages` 恢复为 20，并调高 Token 预算可近似回到原行为。
- 模型超时：删除或调大 `call-timeout`、`stream-timeout` 即可关闭超时约束。

---

## 9. 风险与后续工作

### 9.1 风险

| 风险 | 说明 | 缓解 |
|------|------|------|
| 提示词精简影响模型输出 | 部分模型可能对精简后的规则敏感 | 保留所有硬约束，持续观察回答质量 |
| Token 估算与真实分词存在偏差 | 估算值不等于真实 Token | 通过配置预留安全余量 |
| 非流式超时线程池资源 | 高并发下线程池可能成为瓶颈 | 当前使用缓存线程池，后续可改为有界池 |

### 9.2 后续工作

- 将 Token 估算替换为真实模型分词器。
- 增加模型调用熔断、退避和限流。
- 对提示词精简前后做自动化效果评估。
- 将图表选择改为 JSON Schema 结构化输出，降低脆弱解析。

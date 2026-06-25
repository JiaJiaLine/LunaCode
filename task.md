# LunaCode TUI 对话内核 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `pom.xml` | Maven 项目配置、Java 17、Lanterna、Jackson、JUnit 依赖 |
| 新建 | `config.example.yaml` | 示例 Provider 配置 |
| 新建 | `src/main/java/com/lunacode/app/Main.java` | 程序入口 |
| 新建 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 启动装配和生命周期 |
| 新建 | `src/main/java/com/lunacode/config/ConfigLoader.java` | YAML 读取、环境变量解析、校验 |
| 新建 | `src/main/java/com/lunacode/config/ProviderConfig.java` | Provider 配置模型 |
| 新建 | `src/main/java/com/lunacode/config/ThinkingConfig.java` | Claude thinking 配置模型 |
| 新建 | `src/main/java/com/lunacode/conversation/MessageRole.java` | 内部消息角色 |
| 新建 | `src/main/java/com/lunacode/conversation/MessageStatus.java` | 内部消息状态 |
| 新建 | `src/main/java/com/lunacode/conversation/TokenUsage.java` | Token 用量模型 |
| 新建 | `src/main/java/com/lunacode/conversation/InternalMessage.java` | 内部消息快照模型 |
| 新建 | `src/main/java/com/lunacode/conversation/ApiMessage.java` | API 层消息模型 |
| 新建 | `src/main/java/com/lunacode/conversation/ConversationManager.java` | 对话管理器接口 |
| 新建 | `src/main/java/com/lunacode/conversation/DefaultConversationManager.java` | 并发安全对话管理器实现 |
| 新建 | `src/main/java/com/lunacode/stream/StreamEvent.java` | 统一流事件模型 |
| 新建 | `src/main/java/com/lunacode/stream/SseEvent.java` | SSE 事件模型 |
| 新建 | `src/main/java/com/lunacode/stream/SseParser.java` | SSE 解析器 |
| 新建 | `src/main/java/com/lunacode/stream/AnthropicStreamMapper.java` | Anthropic 事件映射 |
| 新建 | `src/main/java/com/lunacode/stream/OpenAiStreamMapper.java` | OpenAI chunk 映射 |
| 新建 | `src/main/java/com/lunacode/provider/ChatProvider.java` | Provider 统一接口 |
| 新建 | `src/main/java/com/lunacode/provider/ChatProviderFactory.java` | Provider 工厂 |
| 新建 | `src/main/java/com/lunacode/provider/AnthropicProvider.java` | Anthropic 流式 Provider |
| 新建 | `src/main/java/com/lunacode/provider/OpenAiProvider.java` | OpenAI 流式 Provider |
| 新建 | `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java` | 编排器接口 |
| 新建 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 输入、Provider、消息更新编排 |
| 新建 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 状态栏快照模型 |
| 新建 | `src/main/java/com/lunacode/tui/LunaTui.java` | TUI 接口 |
| 新建 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | Lanterna TUI 实现 |
| 新建 | `src/test/java/com/lunacode/config/ConfigLoaderTest.java` | 配置解析测试 |
| 新建 | `src/test/java/com/lunacode/conversation/DefaultConversationManagerTest.java` | 消息管理、并发和 API 格式转换测试 |
| 新建 | `src/test/java/com/lunacode/stream/SseParserTest.java` | SSE 解析测试 |
| 新建 | `src/test/java/com/lunacode/stream/AnthropicStreamMapperTest.java` | Anthropic 流事件映射测试 |
| 新建 | `src/test/java/com/lunacode/stream/OpenAiStreamMapperTest.java` | OpenAI 流事件映射测试 |

## T1: 初始化 Maven 项目

**文件：** `pom.xml`  
**依赖：** 无  
**步骤：**
1. 创建 Maven `jar` 项目配置。
2. 设置 `groupId` 为 `com.lunacode`，`artifactId` 为 `lunacode`。
3. 设置 Java 版本为 17。
4. 添加 Lanterna、Jackson Databind、Jackson YAML、JUnit Jupiter 依赖。
5. 配置 Surefire 插件支持 JUnit 5。

**验证：** 运行 `mvn test`，期望 Maven 能识别项目并进入测试阶段，即使暂时没有测试也不报 POM 错误。

## T2: 添加配置示例

**文件：** `config.example.yaml`  
**依赖：** T1  
**步骤：**
1. 添加 `protocol`、`model`、`base_url`、`api_key` 示例字段。
2. 添加 Anthropic thinking 示例配置。
3. 使用环境变量占位符展示 API Key 用法。
4. 避免写入真实密钥。

**验证：** 人工检查文件不包含真实 API Key；后续 T4 测试会读取该格式。

## T3: 创建配置模型

**文件：** `src/main/java/com/lunacode/config/ProviderConfig.java`、`ThinkingConfig.java`  
**依赖：** T1  
**步骤：**
1. 定义 `ProviderConfig` record，包含 `protocol`、`model`、`baseUrl`、`apiKey`、`thinking`。
2. 定义 `ThinkingConfig` record，包含 `enabled`、`budgetTokens`。
3. 提供必要的空值默认处理方式，例如 thinking 缺失时表示关闭。

**验证：** 运行 `mvn test`，期望编译通过。

## T4: 实现配置加载器与测试

**文件：** `src/main/java/com/lunacode/config/ConfigLoader.java`、`src/test/java/com/lunacode/config/ConfigLoaderTest.java`  
**依赖：** T3  
**步骤：**
1. 用 Jackson YAML 读取配置文件。
2. 校验 `protocol` 只允许 `openai`、`anthropic`。
3. 校验 `model`、`base_url`、`api_key` 必填。
4. 支持 `${ENV_NAME}` 环境变量占位符解析。
5. 错误信息不包含 API Key 原文。
6. 编写测试覆盖合法配置、缺字段、非法 protocol、环境变量占位符。

**验证：** 运行 `mvn test -Dtest=ConfigLoaderTest`，期望全部通过。

## T5: 创建消息基础模型

**文件：** `src/main/java/com/lunacode/conversation/MessageRole.java`、`MessageStatus.java`、`TokenUsage.java`、`InternalMessage.java`、`ApiMessage.java`  
**依赖：** T1  
**步骤：**
1. 定义 `MessageRole`，包含 `SYSTEM`、`USER`、`ASSISTANT`、`TOOL`。
2. 定义 `MessageStatus`，包含 `STREAMING`、`COMPLETE`、`ERROR`。
3. 定义 `TokenUsage` record。
4. 定义 `InternalMessage` record。
5. 定义 `ApiMessage` record。

**验证：** 运行 `mvn test`，期望编译通过。

## T6: 实现对话管理器接口和基础行为

**文件：** `ConversationManager.java`、`DefaultConversationManager.java`、`DefaultConversationManagerTest.java`  
**依赖：** T5  
**步骤：**
1. 定义 `ConversationManager` 接口。
2. 使用内部可变消息对象保存消息列表。
3. 实现 `addMessage()`，返回唯一 ID。
4. 实现 `addStreamingAssistantMessage()`。
5. 实现 `appendContent()`、`completeMessage()`、`failMessage()`。
6. 实现 `snapshot()` 返回不可变快照。
7. 编写测试覆盖 ID 唯一、追加内容、状态流转、快照不可变。

**验证：** 运行 `mvn test -Dtest=DefaultConversationManagerTest`，期望基础行为测试通过。

## T7: 实现 toAPIFormat 转换规则

**文件：** `DefaultConversationManager.java`、`DefaultConversationManagerTest.java`  
**依赖：** T6  
**步骤：**
1. 在 `toAPIFormat()` 中过滤 `SYSTEM`、`TOOL` 和 `ERROR` 消息。
2. 只保留 `USER` 和 `ASSISTANT`。
3. 丢弃开头不是 `USER` 的消息。
4. 合并相邻同角色消息，内容用空行连接。
5. 确保输出 user/assistant 交替。
6. 编写测试覆盖 system/tool/error 过滤、相邻合并、首条 user、交替输出。

**验证：** 运行 `mvn test -Dtest=DefaultConversationManagerTest`，期望 API 格式转换测试通过。

## T8: 验证对话管理器并发安全

**文件：** `DefaultConversationManager.java`、`DefaultConversationManagerTest.java`  
**依赖：** T7  
**步骤：**
1. 用锁保护 add、append、complete、fail、snapshot、toAPIFormat。
2. 添加并发测试：多个线程添加消息、追加内容、读取快照。
3. 验证没有异常、ID 不重复、快照结构完整。
4. 确认外部无法通过快照修改内部状态。

**验证：** 运行 `mvn test -Dtest=DefaultConversationManagerTest`，期望并发测试稳定通过。

## T9: 创建统一 StreamEvent 模型

**文件：** `src/main/java/com/lunacode/stream/StreamEvent.java`  
**依赖：** T5  
**步骤：**
1. 定义 sealed interface `StreamEvent`。
2. 定义 `MessageStart`、`ContentBlockStart`、`ContentDelta`、`ContentBlockStop`、`MessageDelta`、`MessageStop`、`Error` record。
3. 让 usage 使用 `TokenUsage`。

**验证：** 运行 `mvn test`，期望编译通过。

## T10: 实现 SSE 解析器

**文件：** `SseEvent.java`、`SseParser.java`、`SseParserTest.java`  
**依赖：** T9  
**步骤：**
1. 定义 `SseEvent`，包含 `event` 和 `data`。
2. 实现按行接收 SSE 内容并在空行处产出事件。
3. 支持多行 `data:` 合并。
4. 支持缺省 event。
5. 编写测试覆盖普通事件、多行 data、OpenAI `[DONE]`、末尾无空行。

**验证：** 运行 `mvn test -Dtest=SseParserTest`，期望全部通过。

## T11: 实现 Anthropic 流事件映射

**文件：** `AnthropicStreamMapper.java`、`AnthropicStreamMapperTest.java`  
**依赖：** T10  
**步骤：**
1. 解析 `message_start` 并提取 `input_tokens`。
2. 解析 `content_block_start` 并输出 `ContentBlockStart`。
3. 解析 `content_block_delta` 中的 `text_delta` 并输出 `ContentDelta`。
4. 解析 `content_block_stop`。
5. 解析 `message_delta` 中的 `output_tokens` 和 `stop_reason`。
6. 解析 `message_stop`。
7. JSON 解析失败时输出 `StreamEvent.Error`。
8. 编写测试覆盖完整 Anthropic 事件序列。

**验证：** 运行 `mvn test -Dtest=AnthropicStreamMapperTest`，期望全部通过。

## T12: 实现 OpenAI 流事件映射

**文件：** `OpenAiStreamMapper.java`、`OpenAiStreamMapperTest.java`  
**依赖：** T10  
**步骤：**
1. 首个有效 chunk 输出 `MessageStart`。
2. 首个文本增量前输出一次 `ContentBlockStart`。
3. `delta.content` 映射为 `ContentDelta`。
4. `finish_reason` 映射为 `MessageDelta`。
5. `usage` 映射为 `MessageDelta` usage。
6. `[DONE]` 映射为 `ContentBlockStop` 和 `MessageStop`。
7. JSON 解析失败时输出 `StreamEvent.Error`。
8. 编写测试覆盖完整 OpenAI chunk 序列。

**验证：** 运行 `mvn test -Dtest=OpenAiStreamMapperTest`，期望全部通过。

## T13: 创建 Provider 接口和工厂

**文件：** `ChatProvider.java`、`ChatProviderFactory.java`  
**依赖：** T4、T9  
**步骤：**
1. 定义 `ChatProvider.streamChat(List<ApiMessage>, ProviderConfig)`。
2. 定义 `ChatProviderFactory.create(String protocol)`。
3. `openai` 返回 `OpenAiProvider`。
4. `anthropic` 返回 `AnthropicProvider`。
5. 非法协议返回可理解错误。

**验证：** 运行 `mvn test`，期望编译通过。

## T14: 实现 AnthropicProvider 请求骨架

**文件：** `AnthropicProvider.java`  
**依赖：** T11、T13  
**步骤：**
1. 用 Java `HttpClient` 创建 POST `/v1/messages` 请求。
2. 添加 Anthropic API Key 和版本请求头。
3. 构造包含 `model`、`messages`、`stream: true` 的 JSON 请求体。
4. thinking 启用时写入 thinking 配置。
5. 读取响应行并交给 `SseParser`。
6. 将事件交给 `AnthropicStreamMapper` 输出统一 `StreamEvent`。
7. 认证或网络错误映射为 `StreamEvent.Error`，不泄露 API Key。

**验证：** 运行 `mvn test`，期望编译通过；Provider 网络行为在 checklist 阶段用模拟或真实配置验收。

## T15: 实现 OpenAiProvider 请求骨架

**文件：** `OpenAiProvider.java`  
**依赖：** T12、T13  
**步骤：**
1. 用 Java `HttpClient` 创建 OpenAI 兼容流式 POST 请求。
2. 添加 Bearer API Key 请求头。
3. 构造包含 `model`、`messages`、`stream: true` 的 JSON 请求体。
4. 请求 usage 统计选项。
5. 读取响应行并交给 `SseParser`。
6. 将事件交给 `OpenAiStreamMapper` 输出统一 `StreamEvent`。
7. 认证或网络错误映射为 `StreamEvent.Error`，不泄露 API Key。

**验证：** 运行 `mvn test`，期望编译通过；Provider 网络行为在 checklist 阶段用模拟或真实配置验收。

## T16: 创建状态快照和编排器接口

**文件：** `StatusSnapshot.java`、`ChatOrchestrator.java`  
**依赖：** T6、T13  
**步骤：**
1. 定义 `StatusSnapshot`，包含 provider、model、inputTokens、outputTokens、state、errorSummary。
2. 定义 `ChatOrchestrator.submitUserMessage(String content)`。
3. 设计状态值，例如 idle、responding、error。

**验证：** 运行 `mvn test`，期望编译通过。

## T17: 实现 DefaultChatOrchestrator

**文件：** `DefaultChatOrchestrator.java`  
**依赖：** T16、T14、T15  
**步骤：**
1. 忽略空输入。
2. 如果已有响应在 streaming，拒绝新输入并更新状态。
3. 添加 user 消息。
4. 在创建 assistant 消息前调用 `toAPIFormat()`。
5. 添加 streaming assistant 消息。
6. 在后台线程调用 Provider。
7. 处理 `ContentDelta`、usage、stop、error 事件。
8. 更新对话管理器和状态快照。
9. 通知 TUI 刷新。

**验证：** 运行 `mvn test`，期望编译通过；后续 TUI 集成和 checklist 验收流式行为。

## T18: 创建 TUI 接口和基础布局

**文件：** `LunaTui.java`、`LanternaLunaTui.java`  
**依赖：** T17  
**步骤：**
1. 定义 `LunaTui` 接口。
2. 用 Lanterna 创建主界面。
3. 创建上方对话区域。
4. 创建下方输入框。
5. 创建底部状态栏。
6. 回车时把输入交给 `ChatOrchestrator`。
7. 提供 `render()` 根据快照刷新消息和状态。

**验证：** 运行 `mvn test`，期望编译通过；手动启动时能看到三段式界面。

## T19: 实现应用入口和装配

**文件：** `Main.java`、`LunaCodeApplication.java`  
**依赖：** T18  
**步骤：**
1. `Main.main()` 调用 `LunaCodeApplication.run(args)`。
2. 支持从参数读取配置路径，默认使用 `config.yaml`。
3. 调用 `ConfigLoader`。
4. 创建 Provider、ConversationManager、Orchestrator、TUI。
5. 配置失败时打印可理解错误并退出。
6. 成功时启动 TUI。

**验证：** 运行 `mvn test` 和 `mvn package`，期望编译打包通过。

## T20: 全量编译和基础运行检查

**文件：** 全项目  
**依赖：** T19  
**步骤：**
1. 运行全部测试。
2. 运行 Maven 打包。
3. 使用示例配置或临时测试配置启动程序。
4. 确认 TUI 可显示，不出现启动异常。
5. 记录无法真实联网时的限制，留给 checklist 的模拟或真实 API 验收。

**验证：** 运行 `mvn test`、`mvn package`，并启动 LunaCode，期望测试通过、包生成、TUI 能进入界面。

## 执行顺序

```text
T1 -> T2
T1 -> T3 -> T4
T1 -> T5 -> T6 -> T7 -> T8
T5 -> T9 -> T10 -> T11 -> T14
              -> T12 -> T15
T4 + T9 -> T13
T6 + T13 -> T16 -> T17 -> T18 -> T19 -> T20
```

T2、T3、T5 可以在 T1 后并行。T11 和 T12 可以在 T10 后并行。T14 和 T15 可以分别在对应 mapper 完成后并行。
# LunaCode Claude 风格 TUI 视觉升级 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 新建 | `src/main/java/com/lunacode/orchestrator/BackgroundActivitySnapshot.java` | 向 UI 暴露后台任务摘要 |
| 修改 | `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java` | 增加只读 Agent 事件订阅接口 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 转发 Agent 事件并汇总后台活动 |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 携带后台活动并保持构造兼容 |
| 验证 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 确认现有 TUI 与编排器装配无需承担展示细节 |
| 新建 | `src/main/java/com/lunacode/tui/TerminalProfile.java` | 检测颜色、Unicode 和交互能力 |
| 新建 | `src/main/java/com/lunacode/tui/TuiTone.java` | 定义主题语义色 |
| 新建 | `src/main/java/com/lunacode/tui/TuiSymbol.java` | 定义 Unicode/ASCII 语义符号 |
| 新建 | `src/main/java/com/lunacode/tui/LunaTheme.java` | 实现 Luna 月夜主题与降级 |
| 新建 | `src/main/java/com/lunacode/tui/Spinner.java` | 根据 elapsed 计算动画帧 |
| 新建 | `src/main/java/com/lunacode/tui/ActivityKind.java` | 定义活动类型 |
| 新建 | `src/main/java/com/lunacode/tui/ActivityPhase.java` | 定义活动阶段 |
| 新建 | `src/main/java/com/lunacode/tui/TuiActivity.java` | 表达 UI 活动生命周期 |
| 新建 | `src/main/java/com/lunacode/tui/TuiActivityTracker.java` | 跟踪模型、工具、压缩和后台活动 |
| 新建 | `src/main/java/com/lunacode/tui/TerminalLayout.java` | 处理列宽、截断、输入视口和补全布局 |
| 新建 | `src/main/java/com/lunacode/tui/LiveRegionFrame.java` | 表达一次实时区域帧 |
| 新建 | `src/main/java/com/lunacode/tui/LiveRegionRenderer.java` | 原位擦除并重绘临时区域 |
| 新建 | `src/main/java/com/lunacode/tui/AnimationTicker.java` | 定义可替换动画时钟接口 |
| 新建 | `src/main/java/com/lunacode/tui/ScheduledAnimationTicker.java` | 提供生产环境定时刷新 |
| 新建 | `src/main/java/com/lunacode/tui/BuildInfo.java` | 读取版本或开发构建标识 |
| 修改 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | 接入主题、活动、实时区域、品牌和生命周期 |
| 验证 | `src/main/java/com/lunacode/tui/LunaTui.java` | 保持现有 UI 契约不变 |
| 新建 | `src/test/java/com/lunacode/orchestrator/DefaultChatOrchestratorTuiEventTest.java` | 验证事件转发与后台快照 |
| 新建 | `src/test/java/com/lunacode/tui/TerminalProfileTest.java` | 验证能力检测与 `NO_COLOR` |
| 新建 | `src/test/java/com/lunacode/tui/LunaThemeTest.java` | 验证颜色、符号与重置 |
| 新建 | `src/test/java/com/lunacode/tui/SpinnerTest.java` | 验证确定性动画帧 |
| 新建 | `src/test/java/com/lunacode/tui/TuiActivityTrackerTest.java` | 验证全部活动生命周期 |
| 新建 | `src/test/java/com/lunacode/tui/TerminalLayoutTest.java` | 验证中英文列宽与窄终端布局 |
| 新建 | `src/test/java/com/lunacode/tui/LiveRegionRendererTest.java` | 验证实时区域原位刷新 |
| 新建 | `src/test/java/com/lunacode/tui/BuildInfoTest.java` | 验证版本回退 |
| 新建 | `src/test/java/com/lunacode/tui/ScheduledAnimationTickerTest.java` | 验证动画计时器生命周期 |
| 修改 | `src/test/java/com/lunacode/tui/LanternaLunaTuiTest.java` | 验证品牌、消息样式和终端恢复 |
| 修改 | `src/test/java/com/lunacode/tui/LanternaLunaTuiStatusContextTest.java` | 验证紧凑状态与活动提示 |
| 修改 | `src/test/java/com/lunacode/tui/LanternaLunaTuiCommandCompletionTest.java` | 验证主题化候选和输入恢复 |
| 修改 | `src/test/java/com/lunacode/app/LunaCodeApplicationTest.java` | 验证默认应用装配兼容 |

## T1: 定义主题和活动基础类型

**文件：** `TuiTone.java`、`TuiSymbol.java`、`ActivityKind.java`、`ActivityPhase.java`、`TuiActivity.java`、`BackgroundActivitySnapshot.java`

**依赖：** 无

**步骤：**
1. 按 plan 定义语义色、语义符号、活动类型和活动阶段枚举。
2. 定义 `TuiActivity`，规范空标题、空详情和空错误摘要。
3. 定义 `BackgroundActivitySnapshot`，保证 ID、摘要和开始时间可安全读取。
4. 只表达展示数据，不加入终端写入或业务控制逻辑。

**验证：** 运行 `mvn -q -DskipTests compile`，期望新增类型编译通过。

## T2: 实现终端能力检测

**文件：** `TerminalProfile.java`、`TerminalProfileTest.java`

**依赖：** T1

**步骤：**
1. 从 JLine Terminal、环境变量和 Charset 构造能力快照。
2. 实现 `NO_COLOR`、`dumb`、非交互和无颜色能力降级。
3. 通过字符集编码能力决定是否启用 Unicode 符号。
4. 测试现代终端、`NO_COLOR`、dumb、非交互和非 Unicode 编码组合。

**验证：** 运行 `mvn -q -Dtest=TerminalProfileTest test`，期望全部通过。

## T3: 实现 Luna 月夜主题

**文件：** `LunaTheme.java`、`LunaThemeTest.java`

**依赖：** T1、T2

**步骤：**
1. 建立紫色、蓝色、青色、成功、警告、错误和弱化文本的语义映射。
2. 使用 JLine attributed text 输出局部重置的 ANSI 文本。
3. 为每个 `TuiSymbol` 提供 Unicode 和 ASCII 两套字符。
4. 在禁用 ANSI 时返回无控制码文本，在禁用 Unicode 时使用 ASCII 符号。
5. 测试颜色存在、无颜色纯文本、符号降级和样式不泄漏。

**验证：** 运行 `mvn -q -Dtest=LunaThemeTest test`，期望全部通过。

## T4: 实现确定性 Spinner

**文件：** `Spinner.java`、`SpinnerTest.java`

**依赖：** T2

**步骤：**
1. 定义 Unicode 月相或点阵帧序列和 ASCII `| / - \\` 帧序列。
2. 根据 elapsed 和固定帧周期计算索引，不保存可变线程状态。
3. 处理负 Duration、零 Duration 和长时间运行的循环取模。
4. 测试固定时间点对应帧、循环和 ASCII 降级。

**验证：** 运行 `mvn -q -Dtest=SpinnerTest test`，期望全部通过且测试不使用真实 sleep。

## T5: 增加 Agent 事件只读订阅契约

**文件：** `ChatOrchestrator.java`、`DefaultChatOrchestrator.java`、`DefaultChatOrchestratorTuiEventTest.java`

**依赖：** T1

**步骤：**
1. 在编排接口增加返回可关闭订阅句柄的事件观察方法，并为其他实现提供安全默认行为。
2. 在默认编排器维护线程安全观察者集合。
3. 保持“更新编排器状态 → 通知观察者 → 触发现有 onChange”的顺序。
4. 隔离单个观察者异常，并在订阅关闭后停止通知。
5. 测试事件顺序、取消订阅、多个观察者和异常隔离。

**验证：** 运行 `mvn -q -Dtest=DefaultChatOrchestratorTuiEventTest test`，期望事件观察测试全部通过。

## T6: 扩展状态快照并保持兼容

**文件：** `StatusSnapshot.java`、现有直接构造 `StatusSnapshot` 的测试文件

**依赖：** T1

**步骤：**
1. 增加不可变的 `backgroundActivities` 字段并对 null 归一为空列表。
2. 保留现有便捷构造器和 `withAgentMode`、`withPermissionMode`、`withSessionAndMemory` 行为。
3. 确保所有 with 方法都保留后台活动。
4. 编译现有调用方并修复只由 record 参数扩展造成的兼容问题。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest,BuiltinSlashCommandsTest test`，期望现有状态测试通过。

## T7: 汇总后台任务活动

**文件：** `DefaultChatOrchestrator.java`、`DefaultChatOrchestratorTuiEventTest.java`

**依赖：** T6

**步骤：**
1. 在配置后台任务时保存只读 manager 引用。
2. 在状态 enrich 阶段筛选运行中任务。
3. 使用 recent activity、任务文本和安全默认文案生成紧凑摘要。
4. 保留任务 ID、开始时间并按开始时间稳定排序。
5. 测试无 manager、无任务、一个任务、多个任务和任务完成后移除。

**验证：** 运行 `mvn -q -Dtest=DefaultChatOrchestratorTuiEventTest,DefaultBackgroundTaskManagerTest test`，期望全部通过。

## T8: 实现模型活动跟踪

**文件：** `TuiActivityTracker.java`、`TuiActivityTrackerTest.java`

**依赖：** T1

**步骤：**
1. 根据状态从非 responding 进入 responding 时创建模型活动。
2. 收到首个 `StreamText` 后更新模型阶段并停止等待 Spinner。
3. 在 loop complete、idle、cancelled、warning、error、等待用户和等待权限时正确结束活动。
4. 使用注入的 Instant 验证开始时间和重复同步不会重置耗时。

**验证：** 运行 `mvn -q -Dtest=TuiActivityTrackerTest#model* test`，期望模型生命周期用例通过。

## T9: 实现工具活动跟踪

**文件：** `TuiActivityTracker.java`、`TuiActivityTrackerTest.java`

**依赖：** T8

**步骤：**
1. 从 `ToolUseStarted` 提取 requestId、工具名和安全目标摘要。
2. 使用 requestId 独立跟踪多个并行工具。
3. 从 `ToolResultReady` 生成成功或失败最终活动并保存 Duration。
4. 最终记录只保留紧凑摘要，不包含完整参数和工具结果。
5. 测试文件、Bash、Grep、未知工具、失败工具和并行完成乱序。

**验证：** 运行 `mvn -q -Dtest=TuiActivityTrackerTest#tool* test`，期望工具生命周期用例通过。

## T10: 实现压缩和后台活动跟踪

**文件：** `TuiActivityTracker.java`、`TuiActivityTrackerTest.java`

**依赖：** T7、T9

**步骤：**
1. 处理压缩开始、成功和失败事件。
2. 同步状态快照中的后台活动，并移除已完成任务。
3. 实现“工具 > 压缩 > 模型 > 后台”的主要活动优先级。
4. 多后台任务显示数量、代表性摘要和最早开始时间。
5. 测试活动切换、前后台优先级和清空行为。

**验证：** 运行 `mvn -q -Dtest=TuiActivityTrackerTest test`，期望全部活动测试通过。

## T11: 实现列宽计算和安全截断

**文件：** `TerminalLayout.java`、`TerminalLayoutTest.java`

**依赖：** T1、T2

**步骤：**
1. 使用 JLine WCWidth 按 code point 计算显示列宽。
2. 实现不会截断代理对的左侧、右侧和尾部截断。
3. 为 Unicode 与 ASCII profile 选择对应省略符。
4. 测试 ASCII、中文、emoji、组合输入和小于省略符宽度的边界。

**验证：** 运行 `mvn -q -Dtest=TerminalLayoutTest#truncate* test`，期望截断用例通过。

## T12: 实现紧凑状态和输入水平视口

**文件：** `TerminalLayout.java`、`TerminalLayoutTest.java`

**依赖：** T11

**步骤：**
1. 按模型、Agent 模式、权限模式、会话顺序构造可裁剪上下文。
2. 空间不足时先缩短上下文，再围绕光标选择输入可见窗口。
3. 计算绘制完成后需要回退的视觉列数。
4. 复用 `InputLineBuffer` 现有内容与光标索引，不改变输入编辑语义。
5. 测试长模型名、长中文输入、光标位于开头/中间/结尾和极窄终端。

**验证：** 运行 `mvn -q -Dtest=TerminalLayoutTest,InputLineBufferTest test`，期望全部通过。

## T13: 实现补全候选布局

**文件：** `TerminalLayout.java`、`TerminalLayoutTest.java`

**依赖：** T11

**步骤：**
1. 把命令和别名归属格式化为独立展示片段。
2. 根据终端宽度换行候选，优先保留命令本身。
3. 截断过长别名说明，不截断成无效 Unicode。
4. 测试单候选、多候选、别名、窄终端和空候选。

**验证：** 运行 `mvn -q -Dtest=TerminalLayoutTest#completion* test`，期望补全布局用例通过。

## T14: 实现实时区域帧与原位重绘

**文件：** `LiveRegionFrame.java`、`LiveRegionRenderer.java`、`LiveRegionRendererTest.java`

**依赖：** T11、T12、T13

**步骤：**
1. 定义活动行、候选行、上下文行、输入行和光标回退列数。
2. 记录上一帧真实行数并实现从输入行向上擦除。
3. 实现永久输出前擦除、输出后重绘和空帧清理。
4. 处理新帧比旧帧短、状态文本变短和多行候选关闭时的残留。
5. 用内存 Writer 验证帧更新只使用原位控制序列，不逐帧追加历史行。

**验证：** 运行 `mvn -q -Dtest=LiveRegionRendererTest test`，期望原位刷新与残留清理用例通过。

## T15: 实现动画 Ticker

**文件：** `AnimationTicker.java`、`ScheduledAnimationTicker.java`、`src/test/java/com/lunacode/tui/ScheduledAnimationTickerTest.java`

**依赖：** T4

**步骤：**
1. 定义 start、stop、running 和 close 契约。
2. 使用单线程 daemon scheduler 按固定周期调用 tick。
3. 确保重复 start 不创建重复任务，stop/close 后不再调用。
4. 使用可控 executor 或短周期同步机制测试，不让测试依赖长时间等待。

**验证：** 运行 `mvn -q -Dtest=ScheduledAnimationTickerTest test`，期望生命周期测试通过且进程可正常退出。

## T16: 实现构建版本读取

**文件：** `BuildInfo.java`、`BuildInfoTest.java`

**依赖：** 无

**步骤：**
1. 优先读取 package implementation version。
2. 读取 Maven `pom.properties` 作为第二来源。
3. 两者都不存在时返回 `dev`。
4. 测试显式版本、属性版本和开发回退。

**验证：** 运行 `mvn -q -Dtest=BuildInfoTest test`，期望全部通过。

## T17: 为 TUI 注入展示依赖并管理生命周期

**文件：** `LanternaLunaTui.java`、`LunaTui.java`、`LanternaLunaTuiTest.java`

**依赖：** T2–T5、T10、T12、T14–T16

**步骤：**
1. 保留 `LunaTui` 接口和现有公开构造器，并增加测试可注入 Clock、Ticker、Profile、Theme、Layout 和 Renderer 的构造路径。
2. TUI 启动时订阅 Agent 事件并在关闭时取消订阅。
3. 根据 tracker 是否存在动画活动启动或停止 Ticker。
4. 把所有 writer 写入收敛到同步渲染路径。
5. 在 finally 中依次停止 Ticker、取消订阅并恢复终端。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiTest test`，期望启动、订阅和关闭生命周期用例通过。

## T18: 实现紧凑品牌卡片

**文件：** `LanternaLunaTui.java`、`BuildInfo.java`、`TerminalLayout.java`、`LanternaLunaTuiTest.java`

**依赖：** T3、T12、T16、T17

**步骤：**
1. 输出小型月亮/LunaCode 标识、版本、模型、工作目录和快捷提示。
2. 使用 Luna 月夜主题区分品牌、上下文和提示。
3. 实现窄终端下缩短目录、精简辅文和单行品牌降级。
4. 测试正常宽度、窄宽度、无版本和纯文本 profile。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiTest#banner* test`，期望品牌卡片用例通过。

## T19: 主题化用户与助手消息

**文件：** `LanternaLunaTui.java`、`LanternaLunaTuiTest.java`

**依赖：** T3、T14、T17

**步骤：**
1. 写永久消息前统一擦除实时区域。
2. 使用用户和助手语义符号替换 `[complete]`、`[streaming]` 标签。
3. 保留现有 assistant 字符增量追加和完成换行行为。
4. 使用错误符号和错误色输出 assistant 错误摘要。
5. 测试用户消息只打印一次、流增量不重复、完成和错误状态。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiTest#message* test`，期望消息渲染用例通过。

## T20: 接入模型、工具、压缩和后台动画

**文件：** `LanternaLunaTui.java`、`TuiActivityTracker.java`、`LanternaLunaTuiStatusContextTest.java`

**依赖：** T10、T14、T15、T17、T19

**步骤：**
1. 每次 render 同步最新状态并计算主要活动。
2. 使用 Spinner、动作摘要、elapsed 和 Esc 提示构造活动行。
3. 首个流式文本到达时停止模型 Spinner并暂停实时区域，回复完成后恢复。
4. 前台空闲但有后台任务时显示后台活动；前台活动出现后按优先级替换。
5. 测试固定 Clock 下帧变化、动作切换和 Ticker 启停。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest,TuiActivityTrackerTest test`，期望动画集成用例通过。

## T21: 输出紧凑工具最终记录

**文件：** `LanternaLunaTui.java`、`TuiActivityTracker.java`、`LanternaLunaTuiStatusContextTest.java`

**依赖：** T9、T19、T20

**步骤：**
1. 每帧 drain 尚未打印的工具完成记录。
2. 成功记录显示成功符号、工具名、目标摘要和 Duration。
3. 失败记录显示失败符号、工具名和截断错误摘要。
4. 验证记录中不包含完整工具结果或完整 JSON 参数。
5. 测试并行工具乱序完成时每个 requestId 恰好打印一次。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest#tool* test`，期望紧凑工具记录用例通过。

## T22: 优化阻塞与最终状态提示

**文件：** `LanternaLunaTui.java`、`LanternaLunaTuiStatusContextTest.java`

**依赖：** T3、T20

**步骤：**
1. 为等待用户、等待权限、取消、警告、错误和普通信息定义独立符号与色调。
2. 进入上述状态时停止不应继续的前台动画。
3. 提示保留必要会话或权限上下文，但不输出内部枚举名。
4. 使用状态 key 保证同一最终提示只打印一次。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest#status* test`，期望所有最终状态用例通过。

## T23: 实现紧凑上下文与输入行

**文件：** `LanternaLunaTui.java`、`TerminalLayout.java`、`LanternaLunaTuiStatusContextTest.java`

**依赖：** T12、T14、T22

**步骤：**
1. 空闲时只显示模型、Agent 模式、权限模式和会话标识。
2. 从常驻状态中移除 Provider、Token 和 Memory。
3. 通过实时区域绘制上下文和提示符，并恢复输入光标。
4. 验证 `/status` 的详细 Provider、Token 和 Memory 输出保持不变。
5. 测试缺失字段、极窄终端和长输入视口。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest,BuiltinSlashCommandsTest test`，期望紧凑状态和详细命令测试通过。

## T24: 主题化命令补全并保护输入

**文件：** `LanternaLunaTui.java`、`TerminalLayout.java`、`LanternaLunaTuiCommandCompletionTest.java`

**依赖：** T3、T13、T14、T23

**步骤：**
1. 把候选列表纳入实时区域帧，不再假定候选只占一行。
2. 对命令使用主要色，对别名归属使用弱化色。
3. 选择、取消或输入新字符时清除候选并恢复输入视口。
4. 动画 tick 期间保持候选和光标不丢失。
5. 测试单候选、多行候选、别名、动画并发和窄终端。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiCommandCompletionTest test`，期望全部通过。

## T25: 完成清屏与终端恢复

**文件：** `LanternaLunaTui.java`、`LiveRegionRenderer.java`、`LanternaLunaTuiTest.java`

**依赖：** T18–T24

**步骤：**
1. `/clear` 停止旧动画帧、清理补全、重置消息与状态渲染缓存。
2. 清屏后重新输出品牌卡片并绘制当前紧凑上下文和输入行。
3. 正常退出、Esc、取消和异常路径统一清除实时区域并输出样式 reset。
4. 恢复 JLine 原始终端属性，确保 close 可重复调用。
5. 测试清屏后新消息可见、无旧状态漏绘和关闭后 reset/属性恢复。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiTest,LiveRegionRendererTest test`，期望清屏与恢复用例通过。

## T26: 验证应用装配和现有行为兼容

**文件：** `LunaCodeApplication.java`、`LunaCodeApplicationTest.java`、`DefaultChatOrchestrator.java`

**依赖：** T5–T7、T17、T25

**步骤：**
1. 确认默认公开构造器可由现有应用装配直接创建 TUI。
2. 保持现有 TUI 构造调用；事件订阅由 TUI 启动生命周期完成，BuildInfo 由 TUI 自行读取，应用层不注入展示细节。
3. 确认 MCP 关闭、终端 finally 和编排器生命周期顺序不变。
4. 测试应用入口创建、默认状态、事件订阅和关闭路径。

**验证：** 运行 `mvn -q -Dtest=LunaCodeApplicationTest,DefaultChatOrchestratorTuiEventTest test`，期望全部通过。

## T27: 更新并补齐 TUI 回归测试

**文件：** 所有 `src/test/java/com/lunacode/tui/*Test.java`

**依赖：** T2–T26

**步骤：**
1. 删除只断言旧 `[complete]`、`[streaming]` 文案的过期预期。
2. 补充颜色模式和纯文本模式的等价行为断言。
3. 补充动画期间输入编辑、光标移动、Tab 补全和流式文本交替到达场景。
4. 补充窄终端、Unicode 宽度、并行工具和异常关闭场景。
5. 确保测试通过固定 Clock、Profile 和内存输出运行，不依赖真实模型和长时间 sleep。

**验证：** 运行 `mvn -q -Dtest='com.lunacode.tui.*Test' test`，期望全部 TUI 测试通过。

## T28: 运行相关模块回归测试

**文件：** TUI、编排器、Agent、命令、后台、会话、权限相关代码与测试

**依赖：** T27

**步骤：**
1. 运行 TUI 和编排器测试。
2. 运行 Agent Loop、命令补全、后台任务、权限和会话测试。
3. 修复因状态快照扩展、事件观察或渲染时序造成的回归。
4. 确认对话内容、工具结果和权限决策没有变化。

**验证：** 运行 `mvn -q -Dtest='com.lunacode.tui.*Test,DefaultChatOrchestratorTest,DefaultAgentLoopTest,BuiltinSlashCommandsTest,DefaultBackgroundTaskManagerTest,DefaultPermissionEngineTest,DefaultSessionServiceTest' test`，期望全部通过。

## T29: 全量测试与打包

**文件：** 全项目

**依赖：** T28

**步骤：**
1. 运行全量 Maven 测试。
2. 修复所有与本阶段相关的失败，并确认既有功能无回退。
3. 构建可执行 fat JAR。
4. 运行差异空白检查。

**验证：** 依次运行 `mvn test`、`mvn package -DskipTests`、`git diff --check`，期望全部成功。

## T30: 执行 tmux 端到端 UI 验收

**文件：** 运行环境、`spec/15/checklist.md`

**依赖：** T29、已批准的 checklist

**步骤：**
1. 在 tmux 中启动打包后的 LunaCode。
2. 检查品牌卡片、Luna 月夜颜色、紧凑上下文和输入行。
3. 输入一个需要读取多个文件并总结的真实请求。
4. 观察模型等待 Spinner、工具运行 Spinner、紧凑成功记录和流式回复。
5. 在动画期间输入并移动光标，确认回复完成后内容与光标恢复。
6. 测试 Tab 补全、`/status`、`/clear` 和 Esc 取消。
7. 使用 `NO_COLOR` 重新启动，确认 ASCII 与纯文本降级。
8. 对照 checklist 逐项记录实际证据。

**验证：** tmux 中完整流程可操作，视觉输出无逐帧新增行、残留 ANSI 或输入错位，checklist 全部通过。

## 执行顺序

```text
T1 -> T2 -> T3
      └──> T4

T1 -> T5
T1 -> T6 -> T7

T1 -> T8 -> T9
T7 + T9 -> T10

T1 + T2 -> T11 -> T12 -> T13 -> T14
T4 -> T15
T16（可与 T1-T15 并行）

T2-T5 + T10 + T12 + T14-T16 -> T17
T3 + T12 + T16 + T17 -> T18
T3 + T14 + T17 -> T19
T10 + T14 + T15 + T17 + T19 -> T20
T9 + T19 + T20 -> T21
T3 + T20 -> T22
T12 + T14 + T22 -> T23
T3 + T13 + T14 + T23 -> T24
T18-T24 -> T25
T5-T7 + T17 + T25 -> T26
T2-T26 -> T27 -> T28 -> T29 -> T30
```

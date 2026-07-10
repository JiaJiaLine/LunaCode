# LunaCode Claude 风格 TUI 视觉升级 Plan

## 架构概览

本次升级保留“对话历史增量输出 + 底部实时区域”的终端形态，不引入全屏缓冲区。现有对话消息仍由对话管理器提供，现有运行状态仍由编排器维护；新增的 TUI 展示模型只读取消息快照、状态快照和 Agent 事件，不参与 Agent Loop、工具执行或权限决策。

整体分为五个协作部分：

1. **事件与状态观察层**：编排器继续作为 Agent 事件的主消费者，同时向只读观察者转发事件；状态快照补充正在运行的后台任务摘要。该层为 TUI 提供工具 requestId、执行耗时、压缩生命周期和后台活动信息。
2. **展示状态层**：把原始状态字符串和 Agent 事件转换为模型处理、工具执行、上下文压缩、后台任务、等待确认等稳定的 UI 活动；跟踪开始时间、当前阶段、并行工具和待打印的最终工具记录。
3. **主题与终端能力层**：检测交互性、颜色、Unicode 和 `NO_COLOR`，选择 Luna 月夜颜色、Unicode/ASCII 符号及 Spinner 帧。颜色文本使用 JLine 的 attributed text 能力生成，减少 ANSI 重置遗漏。
4. **布局与实时区域层**：计算终端宽度、输入可见窗口、状态截断和候选布局；统一擦除并重绘临时活动行、候选行、紧凑上下文行和输入行，已完成内容只追加到滚动历史。
5. **TUI 生命周期层**：负责终端 raw mode、事件循环、动画计时器、消息增量打印、品牌卡片、清屏和关闭恢复；所有实际终端写入仍经过同一同步边界。

```text
ConversationManager.snapshot ───────────────┐
                                             │
AgentEvent ─> DefaultChatOrchestrator ─> Event observers
                    │                        │
                    └─> StatusSnapshot ──────┤
                                             v
                                   TuiActivityTracker
                                             │
TerminalProfile + LunaTheme + Spinner ───────┤
                                             v
                                      TerminalFrame
                                             │
AnimationTicker ───────> requestRender ──────┤
                                             v
                                   LiveRegionRenderer
                                             │
                                             v
                                        JLine Terminal
```

## 核心数据结构

### TerminalProfile

```java
public record TerminalProfile(
        boolean interactive,
        boolean ansiEnabled,
        boolean unicodeEnabled,
        int maxColors
) {
    public static TerminalProfile detect(
            Terminal terminal,
            Map<String, String> environment,
            Charset charset
    );
}
```

职责：集中描述当前终端可用的装饰能力。检测规则包括：

- 存在 `NO_COLOR` 时关闭颜色。
- `dumb`、非交互或没有颜色能力的终端关闭 ANSI 样式。
- 当前字符集无法编码主题符号时关闭 Unicode。
- 装饰能力降级不影响输入、提交、取消和纯文本状态提示。

### TuiTone

```java
public enum TuiTone {
    BRAND_PRIMARY,
    BRAND_SECONDARY,
    USER,
    ASSISTANT,
    TOOL,
    SUCCESS,
    WARNING,
    ERROR,
    MUTED,
    NORMAL
}
```

职责：表达文本语义而不是直接传播颜色值，保证主题选择与业务文案解耦。

### LunaTheme

```java
public final class LunaTheme {
    public String style(TuiTone tone, String text, Terminal terminal, TerminalProfile profile);
    public String symbol(TuiSymbol symbol, TerminalProfile profile);
    public String reset(TerminalProfile profile);
}
```

`TuiSymbol` 覆盖品牌月亮、用户标识、助手标识、工具标识、成功、失败、警告、问题、权限和左右截断符。Unicode 与 ASCII 符号在同一主题中成对定义，任何状态都不能只依赖颜色表达。

### Spinner

```java
public final class Spinner {
    public String frame(Duration elapsed, TerminalProfile profile);
}
```

Spinner 是纯计算对象，不自行创建线程。Unicode 模式使用平滑月相或点阵帧，ASCII 模式使用 `| / - \\`。帧由经过时间确定，因此测试可使用固定时间，无需真实等待。

### ActivityKind 与 ActivityPhase

```java
public enum ActivityKind {
    MODEL,
    TOOL,
    COMPACTION,
    BACKGROUND
}

public enum ActivityPhase {
    RUNNING,
    SUCCESS,
    ERROR,
    CANCELLED
}
```

### TuiActivity

```java
public record TuiActivity(
        String id,
        ActivityKind kind,
        ActivityPhase phase,
        String title,
        String detail,
        Instant startedAt,
        Duration finalDuration,
        String errorSummary
) {}
```

约束：

- 工具活动使用工具 requestId 作为稳定 ID。
- 模型、压缩和后台活动使用带类型前缀的稳定 ID。
- 运行中耗时由 `now - startedAt` 计算；完成后固定为 `finalDuration`。
- `title` 和 `detail` 都是安全、紧凑的展示摘要，不包含完整工具输出。

### BackgroundActivitySnapshot

```java
public record BackgroundActivitySnapshot(
        String id,
        String summary,
        Instant startedAt
) {}
```

职责：把后台任务管理器中的运行中任务压缩成只读 UI 数据，避免 TUI 依赖后台任务内部对象。

### StatusSnapshot 扩展

```java
public record StatusSnapshot(
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String state,
        String errorSummary,
        String toolName,
        String toolSummary,
        AgentMode agentMode,
        PermissionMode permissionMode,
        String sessionShortId,
        Boolean memoryAutoUpdateEnabled,
        String memoryLatestState,
        List<BackgroundActivitySnapshot> backgroundActivities
) {}
```

现有便捷构造方式继续保留并默认使用空列表，避免已有调用方和测试被迫了解后台任务。

### TuiActivityTracker

```java
public final class TuiActivityTracker {
    public synchronized void onAgentEvent(AgentEvent event, Instant now);
    public synchronized void synchronize(StatusSnapshot status, Instant now);
    public synchronized Optional<TuiActivity> primaryActivity();
    public synchronized List<TuiActivity> activeTools();
    public synchronized List<TuiActivity> drainCompletedRecords();
    public synchronized boolean hasAnimatedActivity();
    public synchronized void clear();
}
```

职责：

- `responding` 首次出现时建立模型活动；收到首个流式文本后把标题从“正在思考”更新为“正在回复”，并停止模型 Spinner，避免 Spinner 与长流式文本争夺当前行。
- `ToolUseStarted` 建立工具活动，`ToolResultReady` 根据 requestId 完成对应活动并加入最终记录队列。
- 并行工具使用 requestId 独立跟踪；实时行展示活动数量和一个代表性摘要，完成记录逐项保留。
- `CompactionStarted/Completed/Failed` 管理压缩活动。
- 状态快照中的运行中后台任务在前台空闲时成为主要动画；多个后台任务显示数量及最近活动摘要。
- 等待用户、等待权限、取消、警告和错误会结束不应继续的前台动画。

### PromptFrame

```java
public record PromptFrame(
        String contextLine,
        String promptLine,
        int cursorColumnsFromEnd
) {}
```

### TerminalLayout

```java
public final class TerminalLayout {
    public String truncate(String text, int maxColumns, String ellipsis);
    public PromptFrame prompt(
            String compactContext,
            String input,
            int cursorIndex,
            int terminalWidth,
            TerminalProfile profile
    );
    public List<String> completionLines(
            List<SlashCommandName> candidates,
            int terminalWidth
    );
}
```

职责：以显示列宽而不是 Java 字符数量计算布局。空间不足时按以下顺序降级：

1. 缩短或隐藏次要上下文。
2. 截断工具目标、模型名和候选别名说明。
3. 对输入内容启用围绕光标的水平可见窗口。
4. 始终保留输入提示符、光标附近内容和状态符号。

### LiveRegionFrame

```java
public record LiveRegionFrame(
        List<String> activityLines,
        List<String> completionLines,
        String contextLine,
        String promptLine,
        int cursorColumnsFromEnd
) {}
```

### LiveRegionRenderer

```java
public final class LiveRegionRenderer {
    public synchronized void beforePersistentOutput(PrintWriter writer);
    public synchronized void render(PrintWriter writer, LiveRegionFrame frame);
    public synchronized void clear(PrintWriter writer);
    public synchronized void reset();
}
```

内部记录上一帧占用的终端行数。写入新历史内容前先擦除实时区域，写完后再绘制最新实时区域。模型正文开始流式输出后暂时不绘制实时区域，直至该段输出完成，以保持现有增量打印语义；输入缓冲和光标索引仍由内存保存，完成后原样恢复。

### AnimationTicker

```java
public interface AnimationTicker extends AutoCloseable {
    void start(Runnable tick);
    void stop();
    boolean running();
}

public final class ScheduledAnimationTicker implements AnimationTicker {
    // 使用单线程 daemon scheduler，以固定周期触发 tick
}
```

Ticker 只在存在可动画活动时运行，停止后取消后续帧，不使用忙等待。TUI 关闭时必须关闭 scheduler。

### BuildInfo

```java
public record BuildInfo(String version) {
    public static BuildInfo load();
}
```

按顺序读取包实现版本、Maven `pom.properties`，开发环境无法取得版本时使用明确的 `dev` 标识。

## 模块设计

### 事件与状态观察

**职责：** 在不改变现有事件处理顺序的前提下，让 TUI 观察 Agent 事件；向状态快照补充后台活动。

**对外接口：**

```java
public interface ChatOrchestrator {
    AutoCloseable observeAgentEvents(AgentEventSink observer);
    StatusSnapshot status();
}
```

编排器先完成自身状态更新，再按注册顺序通知观察者，最后触发现有 `onChange`。观察者异常被隔离，不能中断 Agent Loop。观察者集合采用适合少量订阅者并发读取的线程安全结构。

后台任务管理器在配置到编排器时被保存为只读状态来源。`status()` 只提取 `RUNNING` 任务的 ID、开始时间和最近活动或任务摘要；完成通知继续走现有对话消息流程。

**覆盖需求：** F6、F7、F10、F14、N2、N5。

### 终端能力与 Luna 主题

**职责：** 提供颜色、符号、Spinner 和纯文本降级。

颜色通过 JLine attributed text 生成；光标移动、清行等终端控制仍使用最小 ANSI 控制序列，并集中在实时区域渲染器中。每段着色文本独立重置。

Luna 月夜语义映射：

| 语义 | 样式 |
| --- | --- |
| 品牌主色、用户提示 | 紫色 |
| 品牌辅色、助手标识 | 蓝色 |
| 工具与活动 | 青色 |
| 成功 | 绿色 |
| 警告、权限等待 | 黄色 |
| 错误、失败 | 红色 |
| 模型、模式、会话、耗时 | 弱化灰色 |

**覆盖需求：** F2–F5、F18、F20、N4、N8、N9。

### 活动展示模型

**职责：** 把原始事件转换成稳定的用户可见生命周期。

优先级：等待确认和错误等阻塞提示优先于动画；前台工具优先于压缩，压缩优先于模型活动，前台活动优先于后台活动。并行工具不丢事件，实时区域聚合展示，结束后每个工具生成一条最终记录。

工具目标摘要继续复用现有安全摘要原则，但格式化职责下沉到 TUI 展示层：文件工具显示路径，命令工具显示截断命令，搜索工具显示模式和可选路径，未知工具只显示工具名和“请求”。完整参数和结果不进入 UI 记录。

**覆盖需求：** F6–F11、F14、N3、N10。

### 滚动历史与实时区域

**职责：** 严格区分永久历史和可擦除实时行。

永久历史包括：品牌卡片、用户消息、已完成助手回复、最终工具记录、问题、权限结果、取消、警告和错误。实时区域包括：运行中活动、补全候选、紧凑上下文和输入行。

渲染流程：

```text
收到消息、状态、事件或动画 tick
  -> 获取同步渲染锁
  -> 擦除上一帧实时区域
  -> 追加尚未打印的永久内容
  -> 处理已完成工具记录
  -> 根据终端当前宽度生成新布局
  -> 绘制实时区域
  -> 恢复输入光标
  -> flush
```

流式助手正文开始时清除实时区域，继续按现有字符增量写入；正文完成后换行并恢复实时区域。这样避免重复重绘长回复，同时保留用户已经输入但暂时不可见的缓冲内容。

**覆盖需求：** F1、F8、F15–F17、F19、N1、N2、N6、N8。

### 紧凑品牌卡片

**职责：** 启动时提供身份与运行上下文。

卡片最多占用四个内容行，结构为小型月亮/LunaCode 标识、版本与模型、工作目录、快捷提示。窄终端按“缩短目录 → 省略版本辅文 → 切换单行品牌”的顺序降级。清屏后重新显示卡片和实时区域，但不重新打印旧对话。

**覆盖需求：** F2、F17、F19。

### 紧凑状态与命令补全

**职责：** 降低输入区噪声并保持现有命令能力。

空闲上下文顺序固定为：模型、Agent 模式、权限模式、会话标识。只展示存在的字段；默认不展示 Provider、Token 和 Memory。`/status` 保持现有详细内容。

补全候选按终端宽度分行，命令使用主要颜色，别名归属使用弱化颜色；关闭候选前统一擦除实时区域，避免依赖固定向上一行的假设。

**覆盖需求：** F12、F13、F16、F19。

### 生命周期与终端恢复

**职责：** 管理订阅、Ticker、raw mode 和关闭清理。

启动顺序：创建终端、检测能力、进入 raw mode、订阅事件、打印品牌卡片、绘制初始实时区域、进入事件循环。关闭顺序：停止 Ticker、取消事件订阅、清除实时区域、输出样式重置、恢复属性、关闭终端。任一步异常都进入同一 `finally` 清理路径。

**覆盖需求：** F7、F20、N1、N4。

## 模块交互

### 启动流程

```text
LunaCodeApplication
  -> 创建 DefaultChatOrchestrator
  -> 创建 LanternaLunaTui
  -> TUI.start
      -> 创建 JLine Terminal
      -> TerminalProfile.detect
      -> 订阅 AgentEvent
      -> BuildInfo.load
      -> 打印品牌卡片
      -> 绘制紧凑上下文和输入行
      -> 启动键盘事件循环
```

### 模型响应流程

```text
提交用户消息
  -> StatusSnapshot.state = responding
  -> tracker 建立 MODEL 活动
  -> Ticker 周期请求渲染 Spinner 与耗时
  -> StreamText 到达
  -> tracker 标记模型已开始输出并停止等待 Spinner
  -> TUI 擦除实时区域并增量打印助手正文
  -> LoopComplete
  -> 恢复紧凑上下文和输入行
```

### 工具生命周期

```text
ToolUseStarted(requestId, name, input)
  -> tracker 保存 RUNNING 工具活动
  -> 实时行显示 Spinner + 工具摘要 + 耗时
  -> ToolResultReady(requestId, result, duration)
  -> tracker 按 requestId 完成对应工具
  -> LiveRegionRenderer 擦除运行行
  -> 追加成功/失败工具记录
  -> 绘制下一活动或输入行
```

### 压缩与后台任务

```text
CompactionStarted
  -> 显示压缩 Spinner
  -> CompactionCompleted/Failed
  -> 停止动画并输出成功摘要或警告

StatusSnapshot.backgroundActivities 非空且前台空闲
  -> 显示后台任务数量、摘要与最早任务耗时
  -> 后台完成通知进入对话历史
  -> 状态快照移除已完成任务，Spinner 停止或切换到剩余任务
```

## 文件组织

```text
src/main/java/com/lunacode/
├── app/
│   └── LunaCodeApplication.java                 # 保持 TUI 与编排器装配
├── orchestrator/
│   ├── ChatOrchestrator.java                    # 增加只读事件订阅接口
│   ├── DefaultChatOrchestrator.java             # 转发事件并汇总后台活动
│   ├── StatusSnapshot.java                      # 增加后台活动快照
│   └── BackgroundActivitySnapshot.java          # 新增后台 UI 摘要
└── tui/
    ├── LanternaLunaTui.java                     # 重组 TUI 生命周期与渲染协调
    ├── LunaTui.java                             # 保持现有启动与渲染契约
    ├── TerminalProfile.java                     # 新增终端能力检测
    ├── TuiTone.java                             # 新增语义色枚举
    ├── TuiSymbol.java                           # 新增 Unicode/ASCII 语义符号
    ├── LunaTheme.java                           # 新增主题、符号与降级
    ├── Spinner.java                             # 新增纯计算 Spinner
    ├── ActivityKind.java                        # 新增活动类型
    ├── ActivityPhase.java                       # 新增活动阶段
    ├── TuiActivity.java                        # 新增活动视图模型
    ├── TuiActivityTracker.java                 # 新增活动生命周期跟踪
    ├── TerminalLayout.java                     # 新增列宽、截断与输入窗口
    ├── LiveRegionFrame.java                    # 新增实时区域帧
    ├── LiveRegionRenderer.java                 # 新增原位擦除与重绘
    ├── AnimationTicker.java                    # 新增可替换动画时钟接口
    ├── ScheduledAnimationTicker.java           # 新增生产环境计时器
    └── BuildInfo.java                          # 新增版本读取

src/test/java/com/lunacode/
├── orchestrator/
│   └── DefaultChatOrchestratorTuiEventTest.java # 新增事件转发与后台快照测试
└── tui/
    ├── TerminalProfileTest.java                # 新增能力与 NO_COLOR 测试
    ├── LunaThemeTest.java                      # 新增颜色、符号与重置测试
    ├── SpinnerTest.java                        # 新增确定性帧测试
    ├── TuiActivityTrackerTest.java             # 新增模型/工具/压缩/后台生命周期测试
    ├── TerminalLayoutTest.java                 # 新增中英文宽度与窄终端测试
    ├── LiveRegionRendererTest.java             # 新增原位刷新和残留清理测试
    ├── LanternaLunaTuiTest.java                # 更新消息、品牌与恢复测试
    ├── LanternaLunaTuiStatusContextTest.java   # 更新紧凑状态测试
    └── LanternaLunaTuiCommandCompletionTest.java
                                                    # 更新主题候选和输入恢复测试
```

## Spec 需求覆盖

| Spec 需求 | 设计归属 |
| --- | --- |
| F1–F3 | 滚动历史、品牌卡片、主题化消息标识 |
| F4–F5 | `TerminalProfile`、`LunaTheme`、文本符号降级 |
| F6–F9 | `TuiActivityTracker`、`Spinner`、`AnimationTicker`、实时区域 |
| F10–F11 | Agent 事件观察、工具活动跟踪、最终工具记录 |
| F12–F14 | 紧凑状态布局、阻塞与最终状态渲染 |
| F15–F17 | 输入缓冲保护、补全实时区域、清屏重置 |
| F18–F20 | 终端能力检测、列宽布局、统一关闭恢复 |

## 技术决策

| 决策点 | 选择 | 理由 |
| --- | --- | --- |
| 总体布局 | 滚动历史 + 可擦除实时区域 | 接近 Claude Code，保留终端原生滚动记录，兼容当前增量渲染 |
| 事件来源 | 编排器处理后转发 AgentEvent 给只读观察者 | 保留 requestId、输入摘要和 Duration，避免从状态字符串反向解析 |
| 后台状态 | `StatusSnapshot` 暴露精简后台活动 | TUI 不依赖后台管理器内部模型，也不改变任务行为 |
| 主题实现 | JLine attributed text + 语义色 | 已有依赖，无需引入新 UI 库，降低 ANSI 样式泄漏风险 |
| 动画驱动 | 可启停的单线程 daemon ticker | 没有模型事件时仍能推进帧，不忙等待，关闭简单 |
| Spinner 计算 | 基于 elapsed 的纯函数 | 可确定性测试，不依赖真实 sleep |
| 工具记录 | 事件 ID 驱动，运行行转最终单行 | 支持并行工具，记录紧凑且不丢完成状态 |
| 流式正文 | 开始输出后暂停实时区域，继续增量打印 | 避免反复重绘长文本和终端换行定位复杂度，输入缓冲仍保留 |
| 状态密度 | 模型、模式、权限、会话常驻；其余由 `/status` 提供 | 降低视觉噪声并保持详细诊断入口 |
| 宽度处理 | WCWidth 列宽 + 输入水平窗口 | 正确处理中文、Unicode 符号和光标位置 |
| 降级策略 | `NO_COLOR`/dumb/编码能力检测，ASCII 兜底 | 保证现代效果与旧终端可操作性兼得 |
| 版本显示 | Manifest/Maven 属性，开发环境回退 `dev` | 避免在 UI 中硬编码易过期版本号 |
| 测试策略 | 注入时间、能力与输出，验证纯模型和控制序列 | 避免慢测试和对真实终端主题的依赖 |

## 风险与控制

| 风险 | 控制措施 |
| --- | --- |
| 动画线程与模型线程同时写终端 | 所有写入统一进入 TUI 同步渲染边界，Ticker 只请求渲染 |
| 并行工具状态互相覆盖 | 按 requestId 保存独立活动，实时聚合、完成逐项输出 |
| 实时区域擦除污染滚动历史 | 统一记录上一帧行数，任何永久输出前先调用同一擦除入口 |
| 流式长文本重绘成本过高 | 首个正文到达后停止等待 Spinner并暂停实时区域，保留增量追加 |
| 窄终端导致输入错位 | 先裁剪元数据，再使用光标附近输入窗口，按 WCWidth 计算 |
| ANSI/Unicode 不受支持 | 终端能力检测与纯文本 ASCII 降级 |
| TUI 观察者异常影响 Agent | 编排器隔离观察者异常，主事件处理始终先完成 |
| 关闭时遗留 raw mode 或颜色 | Ticker、订阅、实时区域、样式和终端属性统一在 finally 释放 |

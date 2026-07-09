# LunaCode

LunaCode 是一个用 Java 实现的终端 AI 编程助手，目标是提供类似 Claude Code 的交互体验：在当前仓库里读取代码、运行命令、修改文件、管理会话，并通过子 Agent、Git Worktree 和团队协作能力处理更复杂的开发任务。

项目仍处于快速开发阶段，功能以 `spec/` 目录中的章节规格为主要演进线索。

## 功能概览

- 终端对话式编程助手：支持多轮对话、流式输出、工具调用和状态展示。
- 多 Provider：支持 `anthropic` 和 `openai` 协议。
- 文件与命令工具：读取、写入、编辑文件，执行 Bash/PowerShell 命令，Glob/Grep 搜索。
- 权限系统：支持 `default`、`acceptEdits`、`plan`、`bypassPermissions` 等权限模式，并可通过本地规则持久化允许项。
- 会话管理：会话历史保存到 `.lunacode/sessions/`，支持查看、切换和恢复。
- 上下文压缩：长上下文会按配置自动或手动压缩，临时产物默认写入 `.lunacode/tmp/context/`。
- 子 Agent：可派生定义式子 Agent 或 Fork 子 Agent，支持前台、后台和自动后台化。
- Worktree 隔离：可为子 Agent 创建独立 Git Worktree，避免并行修改互相覆盖。
- Agent Team：支持创建长期团队、注册队员、共享任务列表、邮箱消息和团队内协作。
- MCP 工具：支持本地命令型 MCP server 和远程 HTTP MCP server。
- Hook：支持 pre/post tool use 等事件钩子，可执行命令或 HTTP 请求。

## 环境要求

- JDK 17 或更高版本
- Maven
- Git
- 可用的模型 API Key，例如 `ANTHROPIC_API_KEY` 或 OpenAI 兼容服务的 Key

Windows 可以直接运行；Linux 下命令沙箱会优先使用 Bubblewrap，Windows 下使用直接命令沙箱。

## 快速开始

复制配置样例：

```powershell
Copy-Item config.example.yaml config.yaml
```

设置 API Key：

```powershell
$env:ANTHROPIC_API_KEY = "你的 key"
```

构建：

```powershell
mvn package -DskipTests
```

启动：

```powershell
java -jar target/lunacode-0.1.0-SNAPSHOT.jar config.yaml
```

进入界面后，可以直接输入自然语言任务，例如：

```text
阅读这个项目的结构，告诉我启动入口在哪里
```

## 配置

默认配置文件是 `config.yaml`，也可以在启动时传入其它路径：

```powershell
java -jar target/lunacode-0.1.0-SNAPSHOT.jar path/to/config.yaml
```

常用配置项：

```yaml
protocol: anthropic
model: claude-sonnet-4-20250514
base_url: https://api.anthropic.com
api_key: ${ANTHROPIC_API_KEY}

permissions:
  mode: default

agent:
  auto_background_ms: 120000

context:
  context_window_tokens: 200000
  session_root: .lunacode/tmp/context
```

配置支持环境变量展开，例如 `${ANTHROPIC_API_KEY}`。

MCP 配置会合并用户级 `~/.lunacode/config.yaml` 和项目级配置；Hook 配置会合并项目级 `.lunacode/config.yaml`、用户级 `~/.lunacode/config.yaml` 和本地级 `.lunacode/config.local.yaml`。

## 常用命令

LunaCode 内置斜杠命令：

| 命令 | 说明 |
| --- | --- |
| `/help` | 查看可用命令 |
| `/status` | 查看当前 Provider、模型、权限模式、会话和运行状态 |
| `/clear` | 清空终端可见输出 |
| `/compact` | 手动压缩当前上下文 |
| `/plan` | 进入计划模式 |
| `/do` | 回到执行模式 |
| `/permission` | 查看或切换权限模式 |
| `/session` | 管理会话，支持 current、list、resume、new |
| `/memory` | 管理记忆，支持 list、delete、on、off |
| `/worktree` | 管理 Git Worktree 隔离目录 |
| `/team` | 管理 Agent Team |
| `/review` | 审查当前 git diff |
| `/cancel` | 取消当前运行或等待状态 |

示例：

```text
/session list
/worktree list
/team create core
/permission acceptEdits
```

## Agent Team

团队能力用于让主 Agent 作为 Team Lead 拆任务、派队员并行工作。团队持久化在当前仓库内：

```text
.lunacode/teams/
```

典型流程：

```text
/team create core
```

然后在对话中要求 Lead 派生队员，例如：

```text
创建 alice 和 bob 两个队员，分别分析认证模块和测试模块，然后互相同步发现
```

队员可以使用共享任务工具和 `SendMessage` 直接协作。`TeamCreate`、`TaskCreate`、`TaskUpdate`、`SendMessage` 这类非破坏性团队工具在 `default` 权限下会自动允许；`TeamDelete` 仍然需要确认。

## Worktree 隔离

Worktree 功能使用 Git 原生多工作目录机制，为子 Agent 创建隔离目录和独立分支，避免并行写文件互相覆盖。

相关目录和状态保存在 `.lunacode/` 下。退出或清理 Worktree 时会检查未提交修改和新增 commit，默认保护未保存的工作成果。

常用命令：

```text
/worktree create my-task
/worktree list
/worktree enter my-task
/worktree exit
```

## 项目目录

```text
src/main/java/com/lunacode/
  agent/          Agent 循环与工具执行
  app/            应用入口和主装配
  command/        斜杠命令
  config/         配置加载和 feature gate
  context/        上下文压缩与恢复
  hook/           工具事件钩子
  memory/         记忆管理
  mcp/            MCP 客户端与工具包装
  permission/     权限规则、沙箱和确认策略
  provider/       Anthropic/OpenAI Provider
  session/        会话持久化
  stream/         SSE 流事件解析
  subagent/       子 Agent 定义、派生和运行
  team/           Agent Team、任务和邮箱
  tool/           内置工具
  tui/            终端 UI
  worktree/       Git Worktree 隔离

spec/             功能规格和实现计划
```

运行时目录：

```text
.lunacode/
  sessions/       会话 JSONL
  teams/          团队、队员、任务和邮箱
  tmp/context/    上下文压缩临时产物
```

## 开发

运行测试：

```powershell
mvn test
```

打包：

```powershell
mvn package -DskipTests
```

检查空白问题：

```powershell
git diff --check
```

项目约定开发完功能后做端到端测试：在 tmux 中启动 LunaCode，输入真实对话请求，观察工具调用和回复，再对照对应 `checklist.md` 验收。当前 Windows 环境如果没有 tmux，需要在具备 tmux 的环境里补跑这一步。

## 权限与安全

- `default` 模式会自动允许读类工具，写文件、Bash 和破坏性工具通常需要确认。
- `acceptEdits` 会自动允许文件编辑，但 Bash 仍按 default 策略处理。
- `bypassPermissions` 是危险模式，需要显式确认。
- 团队协作类非破坏工具在 default 下自动允许，方便 Lead 和队员协作。
- Worktree 删除、Team 删除等可能丢失状态的操作仍应经过保护或确认。

## 当前限制

- 团队后端目前以同进程队员为主，独立终端窗格后端仍需要继续完善。
- Team 合并策略主要交给 Lead 通过 Git 命令执行，复杂冲突仍需要人工判断。
- 跨机器分布式团队、实时流式队员通信和更复杂的任务依赖约束尚未实现。
- tmux 端到端验收依赖本机环境，Windows 默认环境通常需要额外安装或换到 Linux/WSL。

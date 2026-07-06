---
id: test_always_approval_mode
type: project_knowledge
title: Always批准权限模式测试结果
created_at: 2026-07-06T00:41:18.789751600Z
updated_at: 2026-07-06T00:43:54.338083100Z
source_session: 20260705-192542-8e94
---

在tooltest目录下测试always模式：规则匹配机制为精确匹配+通配符*，非前缀匹配。WriteFile规则无通配符时只匹配精确路径；Bash规则在存盘时自动追加通配符*以覆盖前缀。用户每次手动批准一个操作后，系统将该命令原文精确记录为规则（WriteFile不加通配符，Bash加通配符）。因此未命中已有规则的操作会弹ask需要用户手动允许，而非自动放行。deny规则仍优先。权限系统行为已澄清。

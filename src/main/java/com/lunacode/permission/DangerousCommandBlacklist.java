package com.lunacode.permission;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class DangerousCommandBlacklist {
    private final List<Entry> entries;

    public DangerousCommandBlacklist() {
        this.entries = List.of(
                entry("递归强制删除根目录", "(^|[;&|]\\s*)rm\\s+(-[^\\s]*r[^\\s]*f[^\\s]*|-[^\\s]*f[^\\s]*r[^\\s]*)\\s+(--\\s*)?(/|/\\*|~|\\$HOME)(\\s|$)"),
                entry("格式化磁盘或分区", "(^|[;&|]\\s*)(mkfs(\\.[A-Za-z0-9]+)?|mkswap)\\s+"),
                entry("直接覆写磁盘设备", "(^|[;&|]\\s*)dd\\s+.*\\bof=/dev/"),
                entry("fork bomb", ":\\s*\\(\\s*\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*}\\s*;\\s*:"),
                entry("递归放开系统根目录权限", "(^|[;&|]\\s*)chmod\\s+-R\\s+777\\s+(/|/\\*)\\s*$"),
                entry("递归修改系统根目录归属", "(^|[;&|]\\s*)chown\\s+-R\\s+\\S+\\s+(/|/\\*)\\s*$"),
                entry("关闭系统或重启", "(^|[;&|]\\s*)(shutdown|reboot|halt|poweroff)\\b")
        );
    }

    public Optional<String> firstMatch(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> entry.pattern().matcher(command).find())
                .map(Entry::reason)
                .findFirst();
    }

    private Entry entry(String reason, String regex) {
        return new Entry(reason, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
    }

    private record Entry(String reason, Pattern pattern) {}
}

package com.lunacode.permission;

public final class SensitivePathPolicy {
    public boolean isSensitive(VirtualPath path) {
        if (path == null) {
            return false;
        }
        String value = path.virtualPath();
        return value.equals("/project/.lunacode/config.yaml")
                || value.equals("/project/.lunacode/permissions.local.yaml")
                || value.equals("/project/.lunacode/skills")
                || value.startsWith("/project/.lunacode/skills/");
    }
}

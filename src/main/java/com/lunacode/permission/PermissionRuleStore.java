package com.lunacode.permission;

public interface PermissionRuleStore {
    LoadedPermissionRules load();

    AppendResult appendLocalAllow(String rule);

    record AppendResult(boolean success, String error) {
        public static AppendResult ok() {
            return new AppendResult(true, "");
        }

        public static AppendResult failure(String error) {
            return new AppendResult(false, error == null ? "追加本地权限规则失败" : error);
        }
    }
}

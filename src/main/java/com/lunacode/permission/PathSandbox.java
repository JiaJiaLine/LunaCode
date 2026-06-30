package com.lunacode.permission;

import java.util.List;

public interface PathSandbox {
    Result validate(String requestedPath, PathIntent intent);

    List<SandboxRoot> roots();

    record Result(boolean allowed, VirtualPath path, String reason) {
        public static Result allow(VirtualPath path) {
            return new Result(true, path, "");
        }

        public static Result deny(String reason) {
            return new Result(false, null, reason == null ? "路径被沙箱拒绝" : reason);
        }
    }
}

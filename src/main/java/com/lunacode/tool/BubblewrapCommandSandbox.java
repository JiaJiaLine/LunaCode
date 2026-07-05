package com.lunacode.tool;

import com.lunacode.config.SandboxConfig;
import com.lunacode.permission.SandboxRoot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BubblewrapCommandSandbox implements CommandSandbox {
    private final boolean checkAvailability;
    private final Path bwrapPath;

    public BubblewrapCommandSandbox() {
        this(true, Path.of("/usr/bin/bwrap"));
    }

    public BubblewrapCommandSandbox(boolean checkAvailability, Path bwrapPath) {
        this.checkAvailability = checkAvailability;
        this.bwrapPath = bwrapPath == null ? Path.of("/usr/bin/bwrap") : bwrapPath;
    }

    @Override
    public PreparedCommand wrapShellCommand(String command, Path workspaceRoot, List<SandboxRoot> roots, SandboxConfig sandboxConfig) {
        if (checkAvailability && !Files.isExecutable(bwrapPath)) {
            return PreparedCommand.error("Linux 命令沙箱不可用: 未找到可执行 bubblewrap (" + bwrapPath + ")");
        }
        try {
            Path seccomp = createAllowListSeccompProgram();
            List<String> bwrap = new ArrayList<>();
            bwrap.add(shellQuote(bwrapPath.toString()));
            bwrap.add("--die-with-parent");
            bwrap.add("--unshare-pid");
            bwrap.add("--unshare-ipc");
            bwrap.add("--unshare-uts");
            if (sandboxConfig == null || !sandboxConfig.networkEnabled()) {
                bwrap.add("--unshare-net");
            }
            bindIfExists(bwrap, "--ro-bind", "/usr");
            bindIfExists(bwrap, "--ro-bind", "/bin");
            bindIfExists(bwrap, "--ro-bind", "/lib");
            bindIfExists(bwrap, "--ro-bind", "/lib64");
            bindIfExists(bwrap, "--ro-bind", "/etc");
            bwrap.add("--proc");
            bwrap.add("/proc");
            bwrap.add("--dev");
            bwrap.add("/dev");
            bwrap.add("--tmpfs");
            bwrap.add("/tmp");
            for (SandboxRoot root : roots == null ? List.<SandboxRoot>of() : roots) {
                bwrap.add(root.readOnly() ? "--ro-bind" : "--bind");
                bwrap.add(shellQuote(root.realPath().toString()));
                bwrap.add(shellQuote(root.realPath().toString()));
            }
            bwrap.add("--chdir");
            bwrap.add(shellQuote(workspaceRoot.toAbsolutePath().normalize().toString()));
            bwrap.add("--seccomp");
            bwrap.add("3");
            bwrap.add("--");
            bwrap.add("/bin/sh");
            bwrap.add("-lc");
            bwrap.add(shellQuote(command));
            String script = "exec " + String.join(" ", bwrap) + " 3<" + shellQuote(seccomp.toString());
            return PreparedCommand.success(List.of("/bin/sh", "-lc", script));
        } catch (IOException e) {
            return PreparedCommand.error("Linux 命令沙箱不可用: seccomp 配置生成失败 (" + e.getMessage() + ")");
        }
    }

    private void bindIfExists(List<String> args, String mode, String path) {
        if (Files.exists(Path.of(path))) {
            args.add(mode);
            args.add(path);
            args.add(path);
        }
    }

    private Path createAllowListSeccompProgram() throws IOException {
        Path file = Files.createTempFile("lunacode-seccomp-", ".bpf");
        file.toFile().deleteOnExit();
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
        buffer.putShort((short) 0x0006); // BPF_RET | BPF_K
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.putInt(0x7fff0000); // SECCOMP_RET_ALLOW
        Files.write(file, buffer.array());
        return file;
    }

    private String shellQuote(String value) {
        if (value == null || value.isEmpty()) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}

package com.lunacode.subagent;

public final class AgentExecutionContextHolder {
    private static final ThreadLocal<SubAgentParentContext> CURRENT = new ThreadLocal<>();

    private AgentExecutionContextHolder() {
    }

    public static SubAgentParentContext current() {
        return CURRENT.get();
    }

    public static <T> T withContext(SubAgentParentContext context, java.util.concurrent.Callable<T> callable) {
        SubAgentParentContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}

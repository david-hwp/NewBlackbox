package com.duodian.admin.config;

public class AuthContext {

    private static final ThreadLocal<CurrentPrincipal> CURRENT_PRINCIPAL = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_PRINCIPAL.set(CurrentPrincipal.userIdOnly(userId));
    }

    public static void setPrincipal(CurrentPrincipal principal) {
        CURRENT_PRINCIPAL.set(principal);
    }

    public static Long getUserId() {
        CurrentPrincipal principal = CURRENT_PRINCIPAL.get();
        return principal == null ? null : principal.getUserId();
    }

    public static CurrentPrincipal getPrincipal() {
        return CURRENT_PRINCIPAL.get();
    }

    public static void clear() {
        CURRENT_PRINCIPAL.remove();
    }
}

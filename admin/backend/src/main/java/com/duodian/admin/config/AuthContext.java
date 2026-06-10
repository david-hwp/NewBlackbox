package com.duodian.admin.config;

public class AuthContext {

    private static final ThreadLocal<CurrentPrincipal> CURRENT_PRINCIPAL = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_PRINCIPAL.set(CurrentPrincipal.userIdOnly(userId));
    }

    public static void setPrincipal(CurrentPrincipal principal) {
        CURRENT_PRINCIPAL.set(principal);
    }

    public static void setToken(String token) {
        CURRENT_TOKEN.set(token);
    }

    public static Long getUserId() {
        CurrentPrincipal principal = CURRENT_PRINCIPAL.get();
        return principal == null ? null : principal.getUserId();
    }

    public static CurrentPrincipal getPrincipal() {
        return CURRENT_PRINCIPAL.get();
    }

    public static String getToken() {
        return CURRENT_TOKEN.get();
    }

    public static void clear() {
        CURRENT_PRINCIPAL.remove();
        CURRENT_TOKEN.remove();
    }
}

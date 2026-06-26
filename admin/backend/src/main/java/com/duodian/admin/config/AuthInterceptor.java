package com.duodian.admin.config;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.UserRepository;
import com.duodian.admin.service.ExternalCallbackTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final byte ACTIVE = 0;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/auth/login",
            "/auth/register",
            "/feedbacks/log-upload",
            "/system-parameters/app",
            "/advanced-features/app"
    );

    public AuthInterceptor(JwtUtil jwtUtil, UserRepository userRepository, ChannelRepository channelRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
            path = path.substring(contextPath.length());
        }

        if (isPublicFileRequest(request.getMethod()) && isPublicFileDownload(path)) {
            return true;
        }

        if (isReleaseJobCallback(request.getMethod(), path)) {
            return true;
        }

        if (isExternalCallbackTokenRequest(
                request.getMethod(),
                path,
                request.getHeader(ExternalCallbackTokenService.HEADER_NAME)
        )) {
            return true;
        }

        // Check whitelist
        for (String white : WHITE_LIST) {
            if (path.equals(white) || path.startsWith(white + "/")) {
                return true;
            }
        }

        // Extract token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或token无效\",\"data\":null}");
            return false;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token已过期或无效\",\"data\":null}");
            return false;
        }

        CurrentPrincipal principal = resolvePrincipal(token);
        if (principal == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"用户不存在或已停用\",\"data\":null}");
            return false;
        }
        AuthContext.setPrincipal(principal);
        AuthContext.setToken(token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        AuthContext.clear();
    }

    private boolean isPublicFileDownload(String path) {
        if (!path.startsWith("/files/")) {
            return false;
        }
        String rest = path.substring("/files/".length());
        return rest.startsWith("engine-packages/")
                || rest.startsWith("app-packages/")
                || rest.startsWith("platform-icons/");
    }

    private boolean isPublicFileRequest(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private boolean isReleaseJobCallback(String method, String path) {
        if (!"POST".equalsIgnoreCase(method) || !path.startsWith("/release-jobs/")) {
            return false;
        }
        return path.endsWith("/callback/progress") || path.endsWith("/callback/complete");
    }

    private boolean isExternalCallbackTokenRequest(String method, String path, String callbackToken) {
        if (callbackToken == null || callbackToken.isBlank()) {
            return false;
        }
        return ("POST".equalsIgnoreCase(method) && "/shop-orders/ingest".equals(path))
                || ("GET".equalsIgnoreCase(method) && "/shop-orders/crawl-targets".equals(path));
    }

    private CurrentPrincipal resolvePrincipal(String token) {
        Long userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        Long channelId = jwtUtil.extractChannelId(token);
        String apkChannel = jwtUtil.extractApkChannel(token);
        if (role != null && channelId != null && apkChannel != null) {
            return new CurrentPrincipal(userId, role, channelId, apkChannel, apkChannel, "jwt");
        }

        User user = userRepository.findByIdAndDeleted(userId, ACTIVE).orElse(null);
        if (user == null) {
            return null;
        }
        Channel channel = user.getChannelId() == null
                ? null
                : channelRepository.findByIdAndDeleted(user.getChannelId(), ACTIVE).orElse(null);
        String channelCode = channel != null ? channel.getCode() : user.getApkChannel();
        return new CurrentPrincipal(
                user.getId(),
                user.getRole(),
                user.getChannelId(),
                channelCode,
                user.getApkChannel(),
                "db"
        );
    }
}

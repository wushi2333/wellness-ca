// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.security;

/**
 * Shared path-whitelist logic used by GatewayFilter and JwtAuthFilter.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static boolean isPublicPath(String path, String method) {
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        if ("/error".equals(path) || "/health".equals(path) || "/".equals(path)) return true;
        if ("/web".equals(path) || path.startsWith("/web/")
                || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/uploads/")) return true;
        return false;
    }
}

// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class GatewayFilter extends OncePerRequestFilter {
    private final String token;
    public GatewayFilter(@Value("${app.gateway.token}") String t) { token=t; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())
                || "/error".equals(path) || "/".equals(path)
                || path.startsWith("/web/") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.startsWith("/images/")) {
            chain.doFilter(req, res);
            return;
        }

        if (!token.equals(req.getHeader("X-API-Token"))) { res.sendError(403, "Forbidden"); return; }
        chain.doFilter(req, res);
    }
}

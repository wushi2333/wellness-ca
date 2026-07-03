// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwt;
    public JwtAuthFilter(JwtTokenProvider j) { jwt=j; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (isPublicRequest(req)) {
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            res.sendError(401, "Missing token");
            return;
        }

        try {
            Long userId = Long.parseLong(jwt.validateAndGetUserId(header.substring(7)));
            req.setAttribute("userId", userId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            chain.doFilter(req, res);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            res.sendError(401, "Invalid token");
        }
    }

    private boolean isPublicRequest(HttpServletRequest req) {
        String path = req.getServletPath();
        return "OPTIONS".equalsIgnoreCase(req.getMethod())
                || "/error".equals(path) || "/".equals(path)
                || path.startsWith("/web/") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.startsWith("/images/")
                || ("/register".equals(path) && "POST".equalsIgnoreCase(req.getMethod()))
                || ("/login".equals(path) && "POST".equalsIgnoreCase(req.getMethod()));
    }
}

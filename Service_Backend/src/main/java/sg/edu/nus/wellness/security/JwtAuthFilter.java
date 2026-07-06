// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.edu.nus.wellness.repository.UserRepo;
import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwt;
    private final UserRepo users;
    public JwtAuthFilter(JwtTokenProvider j, UserRepo users) { this.jwt = j; this.users = users; }

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
            if (!users.existsById(userId)) {
                throw new IllegalArgumentException("User no longer exists");
            }
            req.setAttribute("userId", userId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            chain.doFilter(req, res);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            res.sendError(401, "Invalid token");
        }
    }

    private boolean isPublicRequest(HttpServletRequest req) {
        String path = req.getServletPath();
        if (SecurityUtils.isPublicPath(path, req.getMethod())) return true;
        if ("POST".equalsIgnoreCase(req.getMethod())) {
            if ("/register".equals(path) || "/login".equals(path) || "/auth/google".equals(path)) return true;
        }
        return false;
    }
}

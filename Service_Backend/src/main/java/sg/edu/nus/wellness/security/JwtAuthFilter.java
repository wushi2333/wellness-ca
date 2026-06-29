// Author: Xia Zihang
package sg.edu.nus.wellness.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwt;
    public JwtAuthFilter(JwtTokenProvider j) { jwt=j; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                req.setAttribute("userId", Long.parseLong(jwt.validateAndGetUserId(header.substring(7))));
            } catch (Exception e) { res.sendError(401, "Invalid token"); return; }
        }
        chain.doFilter(req, res);
    }
}

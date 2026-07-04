// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared helper to extract authenticated user ID from request attributes.
 */
@Component
public class RequestUserExtractor {
    public Long userId(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        return userId;
    }
}

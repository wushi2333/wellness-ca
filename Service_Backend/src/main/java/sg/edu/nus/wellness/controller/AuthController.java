// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.controller;
import sg.edu.nus.wellness.dto.AuthRequest;
import sg.edu.nus.wellness.dto.ChangePasswordRequest;
import sg.edu.nus.wellness.dto.EmailRequest;
import sg.edu.nus.wellness.service.AuthService;
import sg.edu.nus.wellness.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;
    private final UserService userService;
    private final RequestUserExtractor userExt;

    public AuthController(AuthService a, UserService us, RequestUserExtractor ue) {
        auth = a;
        userService = us;
        userExt = ue;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.status(201).body(Map.of("message","Registered","userId",auth.register(req.username, req.password, req.email)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(auth.login(req.username, req.password));
    }

    @PostMapping("/auth/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result = auth.googleLogin(
                body.getOrDefault("authCode", ""),
                body.getOrDefault("idToken", ""),
                body.getOrDefault("username", ""),
                body.getOrDefault("redirectUri", "http://localhost/oauth2callback"));
            if (Boolean.TRUE.equals(result.get("conflict"))) {
                return ResponseEntity.status(409).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }

    @PostMapping("/auth/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest request) {
        try {
            Long userId = userExt.userId(request);
            userService.changePassword(userId, req.oldPassword, req.newPassword);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }

    @PutMapping("/auth/email")
    public ResponseEntity<?> bindEmail(@RequestBody EmailRequest req, HttpServletRequest request) {
        try {
            Long userId = userExt.userId(request);
            userService.bindEmail(userId, req.email);
            return ResponseEntity.ok(Map.of("message", "Email updated", "email", req.email));
        } catch (UserService.EmailConflictException e) {
            return ResponseEntity.status(409).body(Map.of("detail", e.getMessage(), "conflict", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }

    @DeleteMapping("/auth/account")
    public ResponseEntity<?> deleteAccount(HttpServletRequest request) {
        try {
            Long userId = userExt.userId(request);
            userService.deleteAccount(userId);
            return ResponseEntity.ok(Map.of("message", "Account deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }
}

// Author: Xia Zihang
package sg.edu.nus.wellness.controller;
import sg.edu.nus.wellness.dto.AuthRequest;
import sg.edu.nus.wellness.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService a) { auth=a; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest req) {
        try { return ResponseEntity.status(201).body(Map.of("message","Registered","userId",auth.register(req.username,req.password))); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("detail",e.getMessage())); }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        try { return ResponseEntity.ok(auth.login(req.username, req.password)); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("detail",e.getMessage())); }
    }
}

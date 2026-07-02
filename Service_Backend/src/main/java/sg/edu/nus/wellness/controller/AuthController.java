// Author: Xia Zihang, Yutong Luo
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
        return ResponseEntity.status(201).body(Map.of("message","Registered","userId",auth.register(req.username,req.password)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(auth.login(req.username, req.password));
    }
}

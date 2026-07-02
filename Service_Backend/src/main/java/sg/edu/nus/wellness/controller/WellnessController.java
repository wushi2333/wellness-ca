// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.controller;
import sg.edu.nus.wellness.dto.WellnessRequest;
import sg.edu.nus.wellness.service.WellnessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class WellnessController {
    private final WellnessService ws;
    public WellnessController(WellnessService w) { ws=w; }

    private Long uid(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        return userId;
    }

    @GetMapping("/records")
    public ResponseEntity<?> list(HttpServletRequest req) {
        return ResponseEntity.ok(ws.list(uid(req)));
    }

    @PostMapping("/records")
    public ResponseEntity<?> create(@Valid @RequestBody WellnessRequest r, HttpServletRequest req) {
        return ResponseEntity.status(201).body(Map.of("message","Created","id",ws.create(uid(req),r)));
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody WellnessRequest r, HttpServletRequest req) {
        ws.update(uid(req), id, r);
        return ResponseEntity.ok(Map.of("message","Updated"));
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest req) {
        ws.delete(uid(req), id);
        return ResponseEntity.ok(Map.of("message","Deleted"));
    }
}

package sg.edu.nus.wellness.controller;
import sg.edu.nus.wellness.dto.WellnessRequest;
import sg.edu.nus.wellness.service.WellnessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class WellnessController {
    private final WellnessService ws;
    public WellnessController(WellnessService w) { ws=w; }

    private Long uid(HttpServletRequest req) { return (Long) req.getAttribute("userId"); }

    @GetMapping("/records")
    public ResponseEntity<?> list(HttpServletRequest req) {
        return ResponseEntity.ok(ws.list(uid(req)));
    }

    @PostMapping("/records")
    public ResponseEntity<?> create(@RequestBody WellnessRequest r, HttpServletRequest req) {
        return ResponseEntity.status(201).body(Map.of("message","Created","id",ws.create(uid(req),r)));
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody WellnessRequest r, HttpServletRequest req) {
        try { ws.update(uid(req), id, r); return ResponseEntity.ok(Map.of("message","Updated")); }
        catch (RuntimeException e) { return ResponseEntity.notFound().build(); }
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest req) {
        try { ws.delete(uid(req), id); return ResponseEntity.ok(Map.of("message","Deleted")); }
        catch (RuntimeException e) { return ResponseEntity.notFound().build(); }
    }
}

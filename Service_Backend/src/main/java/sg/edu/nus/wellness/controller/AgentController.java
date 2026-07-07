// Author: Cai Peilin, Yutong Luo
package sg.edu.nus.wellness.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import sg.edu.nus.wellness.repository.RecRepo;
import java.util.List;
import java.util.Map;

@RestController
public class AgentController {
    private final RestTemplate http;
    private final String agentUrl;
    private final RecRepo recRepo;

    public AgentController(@Value("${app.agent.url:http://localhost:8002}") String url, RestTemplate rt, RecRepo recRepo) { http=rt;
        this.recRepo = recRepo;
        agentUrl=url;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/agent/recommend")
    public ResponseEntity<?> recommend(@RequestBody(required = false) Map<String, Object> requestBody, HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        String token = req.getHeader("X-API-Token");
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> agentBody = new java.util.HashMap<>();
        if (requestBody != null) agentBody.putAll(requestBody);
        agentBody.put("user_id", userId);
        try {
            HttpHeaders h = new HttpHeaders(); h.set("Authorization",auth); h.set("X-API-Token",token);
            ResponseEntity<Map> resp = http.exchange(agentUrl+"/recommend", HttpMethod.POST,
                new HttpEntity<>(agentBody, h), Map.class);
            // Save to MySQL so history/delete work from RecRepo
            if (resp.getBody() != null && userId != null) {
                var body = resp.getBody();
                String content = (String) body.getOrDefault("recommendation", "");
                List<Map<String,String>> evidence = (List<Map<String,String>>) body.getOrDefault("evidence", List.of());
                String evidenceJson = "[]";
                try { evidenceJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence); } catch (Exception ignored) {}
                Integer iterations = (Integer) body.getOrDefault("iterations", 1);
                var saved = recRepo.save(new sg.edu.nus.wellness.model.Recommendation(userId, content, evidenceJson, iterations));
                body.put("saved_id", saved.getId());
            }
            return ResponseEntity.ok(resp.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("detail","Agent unavailable"));
        }
    }

    @DeleteMapping("/agent/recommend/{id}")
    public ResponseEntity<?> deleteRec(@PathVariable Long id, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("detail","Unauthorized"));
        var rec = recRepo.findByIdAndUserId(id, userId);
        if (rec.isEmpty()) return ResponseEntity.status(404).body(Map.of("detail","Not found"));
        recRepo.delete(rec.get());
        return ResponseEntity.ok(Map.of("message","Deleted"));
    }

    @GetMapping("/agent/recommend/history")
    public ResponseEntity<?> history(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("detail","Unauthorized"));
        var recs = recRepo.findByUserIdOrderByCreatedAtDesc(userId);
        var result = recs.stream().map(r -> Map.of(
            "id", r.getId(),
            "content", r.getContent(),
            "evidence", r.getEvidence() != null ? r.getEvidence() : "[]",
            "iterations", r.getIterations() != null ? r.getIterations() : 0,
            "created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
        )).toList();
        return ResponseEntity.ok(result);
    }
}

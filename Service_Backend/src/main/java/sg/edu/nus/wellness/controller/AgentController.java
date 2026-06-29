// Author: Cai Peilin
package sg.edu.nus.wellness.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@RestController
public class AgentController {
    private final RestTemplate http = new RestTemplate();
    private final String agentUrl;

    public AgentController(@Value("${app.agent.url:http://localhost:8002}") String url) {
        agentUrl=url;
    }

    @PostMapping("/agent/recommend")
    public ResponseEntity<?> recommend(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        String token = req.getHeader("X-API-Token");
        try {
            HttpHeaders h = new HttpHeaders(); h.set("Authorization",auth); h.set("X-API-Token",token);
            ResponseEntity<Map> resp = http.exchange(agentUrl+"/recommend", HttpMethod.POST,
                new HttpEntity<>(null,h), Map.class);
            return ResponseEntity.ok(resp.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("detail","Agent unavailable"));
        }
    }

    @GetMapping("/agent/recommend/history")
    public ResponseEntity<?> history(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        String token = req.getHeader("X-API-Token");
        try {
            HttpHeaders h = new HttpHeaders(); h.set("Authorization",auth); h.set("X-API-Token",token);
            ResponseEntity<?> resp = http.exchange(agentUrl+"/recommend/history", HttpMethod.GET,
                new HttpEntity<>(h), Object.class);
            return ResponseEntity.ok(resp.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("detail","Agent unavailable"));
        }
    }
}

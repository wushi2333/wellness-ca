// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.repository.RecRepo;

import java.util.List;
import java.util.Map;

@Controller
public class WebRecommendationController {
    private final RestTemplate http;
    private final RecRepo recRepo;
    private final String agentUrl;
    private final String gatewayToken;

    public WebRecommendationController(RecRepo recRepo,
                                       @Value("${app.agent.url:http://localhost:8002}") String agentUrl,
                                       @Value("${app.gateway.token:team-wellness-2025}") String gatewayToken,
                                       RestTemplate rt) {
        this.recRepo = recRepo;
        this.agentUrl = agentUrl;
        this.gatewayToken = gatewayToken;
        this.http = rt;
    }

    @GetMapping("/web/insights")
    public String insights(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) {
            return WebSession.redirectToLogin();
        }
        WebSession.addCommonModel(session, model, "insights");
        model.addAttribute("state", "idle");
        return "web/insights";
    }

    @PostMapping("/web/insights/generate")
    public String generate(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) {
            return WebSession.redirectToLogin();
        }

        WebSession.addCommonModel(session, model, "insights");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(WebSession.accessToken(session));
            headers.set("X-API-Token", gatewayToken);
            ResponseEntity<Map> response = http.exchange(
                    agentUrl + "/recommend",
                    HttpMethod.POST,
                    new HttpEntity<>(null, headers),
                    Map.class);

            Map<?, ?> body = response.getBody();
            model.addAttribute("state", "result");
            model.addAttribute("recommendation", body == null ? "" : body.get("recommendation"));
            model.addAttribute("evidence", body == null ? List.of() : body.get("evidence"));
            model.addAttribute("iterations", body == null ? "" : body.get("iterations"));
            model.addAttribute("savedId", body == null ? "" : body.get("saved_id"));
        } catch (RuntimeException ex) {
            model.addAttribute("state", "idle");
            model.addAttribute("error", "Agent unavailable. Please make sure the agent sidecar is running.");
        }
        return "web/insights";
    }

    @GetMapping("/web/insights/history")
    public String history(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        WebSession.addCommonModel(session, model, "insights");
        model.addAttribute("items", recRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId));
        return "web/insight-history";
    }
}

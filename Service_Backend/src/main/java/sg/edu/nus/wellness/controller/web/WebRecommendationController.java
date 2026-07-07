// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.model.Recommendation;
import sg.edu.nus.wellness.repository.RecRepo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebRecommendationController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        if (!WebSession.isLoggedIn(session)) return WebSession.redirectToLogin();
        WebSession.addCommonModel(session, model, "insights");
        model.addAttribute("state", "idle");
        model.addAttribute("history", WebViewModels.recommendations(
                recRepo.findTop10ByUserIdOrderByCreatedAtDesc(WebSession.userId(session))));
        return "web/insights";
    }

    @PostMapping("/web/insights/generate")
    public String generate(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();

        WebSession.addCommonModel(session, model, "insights");
        model.addAttribute("history", WebViewModels.recommendations(
                recRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId)));
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_id", userId);
            requestBody.put("language", WebSession.language(session));

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(WebSession.accessToken(session));
            headers.set("X-API-Token", gatewayToken);
            ResponseEntity<Map> response = http.exchange(
                    agentUrl + "/recommend",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    Map.class);

            Map<?, ?> body = response.getBody();
            Object recommendationValue = body == null ? null : body.get("recommendation");
            Object evidenceValue = body == null ? null : body.get("evidence");
            String recommendation = recommendationValue == null ? "" : String.valueOf(recommendationValue);
            Object evidence = evidenceValue == null ? List.of() : evidenceValue;
            Integer iterations = toInteger(body == null ? null : body.get("iterations"), 1);
            Recommendation saved = recRepo.save(new Recommendation(userId, recommendation, evidenceJson(evidence), iterations));
            WebViewModels.RecommendationView savedView = WebViewModels.recommendations(List.of(saved)).get(0);

            model.addAttribute("state", "result");
            model.addAttribute("recommendation", recommendation);
            model.addAttribute("evidence", savedView.evidence());
            model.addAttribute("iterations", iterations);
            model.addAttribute("savedId", saved.getId());
            model.addAttribute("history", WebViewModels.recommendations(
                    recRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId)));
        } catch (RuntimeException ex) {
            model.addAttribute("state", "idle");
            model.addAttribute("error", "Agent unavailable. Please make sure the agent sidecar is running.");
        }
        return "web/insights";
    }

    @GetMapping("/web/insights/history")
    public String history(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();

        WebSession.addCommonModel(session, model, "insights");
        model.addAttribute("items", WebViewModels.recommendations(recRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId)));
        return "web/insight-history";
    }

    @PostMapping("/web/insights/history/{id}/delete")
    public String delete(@PathVariable Long id,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        var rec = recRepo.findByIdAndUserId(id, userId);
        if (rec.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Recommendation not found.");
        } else {
            recRepo.delete(rec.get());
            redirectAttributes.addFlashAttribute("success", "Recommendation deleted.");
        }
        return "redirect:/web/insights/history";
    }

    private String evidenceJson(Object evidence) {
        try {
            return MAPPER.writeValueAsString(evidence == null ? List.of() : evidence);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private Integer toInteger(Object value, Integer fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.toString());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}

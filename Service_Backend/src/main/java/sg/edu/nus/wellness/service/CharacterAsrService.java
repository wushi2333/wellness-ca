// Author: Xia Zihang
package sg.edu.nus.wellness.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CharacterAsrService {

    private final RestTemplate http = new RestTemplate();
    private final String appId, token;

    public CharacterAsrService(
            @Value("${app.volcano.tts.appid}") String appId,
            @Value("${app.volcano.tts.token}") String token) {
        this.appId = appId;
        this.token = token;
    }

    @SuppressWarnings("unchecked")
    public String recognize(String base64Audio) {
        try {
            Map<String, Object> body = Map.of(
                "user", Map.of("uid", appId),
                "audio", Map.of("data", base64Audio),
                "request", Map.of("model_name", "bigmodel")
            );

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("X-Api-App-Key", appId);
            h.set("X-Api-Access-Key", token);
            h.set("X-Api-Resource-Id", "volc.bigasr.auc_turbo");
            h.set("X-Api-Request-Id", UUID.randomUUID().toString());
            h.set("X-Api-Sequence", "-1");

            ResponseEntity<Map> resp = http.exchange(
                "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash",
                HttpMethod.POST, new HttpEntity<>(body, h), Map.class);

            if (resp.getBody() != null && resp.getBody().containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) resp.getBody().get("result");
                return (String) result.getOrDefault("text", "");
            }
            return null;
        } catch (Exception e) {
            System.err.println("[ASR] Error: " + e.getMessage());
            return null;
        }
    }
}

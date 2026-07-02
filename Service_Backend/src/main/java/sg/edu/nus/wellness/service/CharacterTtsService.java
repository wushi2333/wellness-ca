// Author: Xia Zihang
package sg.edu.nus.wellness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CharacterTtsService {

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String appId, token, speaker;

    public CharacterTtsService(
            @Value("${app.volcano.tts.appid}") String appId,
            @Value("${app.volcano.tts.token}") String token,
            @Value("${app.volcano.tts.speaker}") String speaker) {
        this.appId = appId;
        this.token = token;
        this.speaker = speaker;
    }

    private static final Map<String, String> EMOTION_CONTEXT = Map.of(
        "happy", "用开心的语气说话",
        "listening", "用温柔关心的语气说话",
        "thinking", "用思考的语气说话",
        "surprised", "用惊讶的语气说话",
        "focused", "用认真专业的语气说话",
        "confused", "用疑惑的语气说话"
    );

    @SuppressWarnings("unchecked")
    public byte[] synthesize(String text, String emotion) {
        try {
            String spd = emotion.equals("surprised") || emotion.equals("happy") ? "1.1" : "1.0";
            String pit = emotion.equals("surprised") ? "3" : emotion.equals("happy") ? "2" : "0";

            Map<String, Object> body = Map.of(
                "app", Map.of("appid", appId, "token", token, "cluster", "volcano_tts"),
                "user", Map.of("uid", "yui-user"),
                "audio", Map.of("voice_type", speaker, "encoding", "mp3", "rate", 24000,
                    "speed_ratio", spd, "pitch_ratio", pit),
                "request", Map.of("reqid", UUID.randomUUID().toString(),
                    "text", text, "text_type", "plain", "operation", "query")
            );

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("Authorization", "Bearer;" + token);

            ResponseEntity<Map> resp = http.exchange(
                "https://openspeech.bytedance.com/api/v1/tts",
                HttpMethod.POST, new HttpEntity<>(body, h), Map.class);

            if (resp.getBody() != null && resp.getBody().containsKey("data")) {
                return Base64.getDecoder().decode((String) resp.getBody().get("data"));
            }
            return null;
        } catch (Exception e) {
            System.err.println("[TTS] Error: " + e.getMessage());
            return null;
        }
    }
}

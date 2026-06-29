package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.model.ChatHistory;
import sg.edu.nus.wellness.repository.ChatHistoryRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class ChatService {
    private final RestTemplate http = new RestTemplate();
    private final ChatHistoryRepo repo;
    private final String key, url = "https://api.deepseek.com/chat/completions";

    public ChatService(ChatHistoryRepo r, @Value("${app.deepseek.key}") String k) { repo=r; key=k; }

    @SuppressWarnings("unchecked")
    public String chat(Long userId, String message) {
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(key); h.setContentType(MediaType.APPLICATION_JSON);
        Map<String,Object> body = Map.of("model","deepseek-chat","messages",List.of(
            Map.of("role","system","content","You are a friendly wellness assistant. Give concise, practical health advice."),
            Map.of("role","user","content",message)
        ),"temperature",0.7,"max_tokens",512);
        ResponseEntity<Map> resp = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body,h), Map.class);
        String reply = ((Map<String,String>)((Map)((List)resp.getBody().get("choices")).get(0)).get("message")).get("content");
        repo.save(new ChatHistory(userId, message, reply));
        return reply;
    }
}

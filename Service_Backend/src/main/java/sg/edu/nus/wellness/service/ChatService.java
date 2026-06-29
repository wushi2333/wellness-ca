// Author: Xia Zihang
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
    private final String ragUrl;

    public ChatService(ChatHistoryRepo r,
                       @Value("${app.deepseek.key}") String k,
                       @Value("${app.rag.url:http://localhost:8001}") String rag) {
        repo=r; key=k; ragUrl=rag;
    }

    @SuppressWarnings("unchecked")
    public String chat(Long userId, String message) {
        // RAG: retrieve relevant wellness context from ChromaDB sidecar
        String context = "";
        try {
            Map<String,Object> ragReq = Map.of("query",message,"userId",userId,"k",4);
            ResponseEntity<Map> ragResp = http.postForEntity(ragUrl+"/search", ragReq, Map.class);
            if (ragResp.getBody() != null) context = (String) ragResp.getBody().getOrDefault("context","");
        } catch (Exception ignored) {}

        String systemPrompt = "You are a friendly wellness assistant. Give concise, practical health advice.";
        if (!context.isEmpty()) systemPrompt += "\n\nRelevant user data:\n"+context;

        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(key); h.setContentType(MediaType.APPLICATION_JSON);
        Map<String,Object> body = Map.of("model","deepseek-chat","messages",List.of(
            Map.of("role","system","content", systemPrompt),
            Map.of("role","user","content", message)
        ),"temperature",0.7,"max_tokens",512);
        ResponseEntity<Map> resp = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body,h), Map.class);
        String reply = ((Map<String,String>)((Map)((List)resp.getBody().get("choices")).get(0)).get("message")).get("content");
        repo.save(new ChatHistory(userId, message, reply));
        return reply;
    }
}

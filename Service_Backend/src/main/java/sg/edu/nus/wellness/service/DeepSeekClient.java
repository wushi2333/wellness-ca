// Author: Huang Qianer, Wang Songyu, Xia Zihang
package sg.edu.nus.wellness.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Shared DeepSeek API client used by ChatService, RecService, CharacterService,
 * and CharacterMemoryService. Centralizes the boilerplate of building requests,
 * calling the chat/completions endpoint, and extracting the reply content.
 */
@Component
public class DeepSeekClient {

    private final RestTemplate http;
    private final String key;
    private final String apiUrl = "https://api.deepseek.com/chat/completions";

    public DeepSeekClient(RestTemplate rt, @Value("${app.deepseek.key}") String key) {
        this.http = rt;
        this.key = key;
    }

    /**
     * Calls the DeepSeek chat API and returns the assistant's reply.
     *
     * @param messages  list of message maps, each with "role" and "content"
     * @param temperature  sampling temperature (0.0 - 2.0)
     * @param maxTokens    max tokens in the response
     * @return the assistant's text reply
     */
    @SuppressWarnings("unchecked")
    public String complete(List<Map<String, String>> messages, double temperature, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(key);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "model", "deepseek-chat",
            "messages", messages,
            "temperature", temperature,
            "max_tokens", maxTokens
        );

        ResponseEntity<Map> resp = http.exchange(
            apiUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        return ((Map<String, String>) ((Map) ((List) resp.getBody()
            .get("choices")).get(0)).get("message")).get("content");
    }
}

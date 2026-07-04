// Author: Huang Qianer
package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.model.ChatHistory;
import sg.edu.nus.wellness.repository.ChatHistoryRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatHistoryRepo repo;
    private final DeepSeekClient llm;
    private final RagClient rag;

    public ChatService(ChatHistoryRepo r, DeepSeekClient llm, RagClient rag) {
        repo=r; this.llm=llm; this.rag=rag;
    }

    public String chat(Long userId, String message) {
        String context = rag.search(userId, message, 4);

        String systemPrompt = "You are a friendly wellness assistant. Give concise, practical health advice.";
        if (!context.isEmpty()) systemPrompt += "\n\nRelevant user data:\n"+context;

        List<Map<String,String>> messages = List.of(
            Map.of("role","system","content", systemPrompt),
            Map.of("role","user","content", message)
        );
        String reply = llm.complete(messages, 0.7, 512);
        repo.save(new ChatHistory(userId, message, reply));
        return reply;
    }
}

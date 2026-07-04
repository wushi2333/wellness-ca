// Author: Xia Zihang
package sg.edu.nus.wellness.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sg.edu.nus.wellness.model.*;
import sg.edu.nus.wellness.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CharacterMemoryService {
    private static final Logger log = LoggerFactory.getLogger(CharacterMemoryService.class);

    private final CharacterSessionRepo sessionRepo;
    private final CharacterMessageRepo messageRepo;
    private final CharacterUserProfileRepo profileRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeepSeekClient llm;

    private static final int CONTEXT_WINDOW = 20;
    private static final int COMPRESS_EVERY = 10;

    public CharacterMemoryService(
            CharacterSessionRepo sessionRepo,
            CharacterMessageRepo messageRepo,
            CharacterUserProfileRepo profileRepo,
            DeepSeekClient llm) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.profileRepo = profileRepo;
        this.llm = llm;
    }

    // ---- Sessions ----------------------------------------------------------

    public List<CharacterSession> getSessions(Long userId) {
        return sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public CharacterSession createSession(Long userId, String mode) {
        CharacterSession s = new CharacterSession();
        s.userId = userId;
        s.mode = mode != null ? mode : "chat";
        s.title = "New Chat";
        return sessionRepo.save(s);
    }

    public void deleteSession(Long sessionId) {
        List<CharacterMessage> msgs = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        messageRepo.deleteAll(msgs);
        sessionRepo.deleteById(sessionId);
    }

    // ---- Messages ----------------------------------------------------------

    public List<CharacterMessage> getMessages(Long sessionId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public CharacterMessage saveMessage(Long sessionId, String role, String content, String emotion, List<String> tools) {
        CharacterMessage m = new CharacterMessage();
        m.sessionId = sessionId;
        m.role = role;
        m.content = content;
        m.emotion = emotion;
        if (tools != null && !tools.isEmpty()) {
            try { m.tools = mapper.writeValueAsString(tools); } catch (Exception e) { log.warn("Failed to serialize tools JSON for message session {}", sessionId, e); }
        }
        m = messageRepo.save(m);

        CharacterSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s != null) {
            s.messageCount = messageRepo.countBySessionId(sessionId);
            s.updatedAt = LocalDateTime.now();
            if (s.messageCount == 1) {
                s.title = content.length() < 50 ? content : content.substring(0, 50);
            }
            sessionRepo.save(s);
            // After first exchange, generate a concise title
            if (s.messageCount == 2) maybeGenerateTitle(s);
        }
        return m;
    }

    // ---- Context building --------------------------------------------------

    public String buildContext(Long sessionId, Long userId, boolean agentMode) {
        StringBuilder ctx = new StringBuilder();

        String profile = getProfileContext(userId);
        if (!profile.isEmpty()) ctx.append(profile);

        String history = getSessionContext(sessionId);
        if (!history.isEmpty()) ctx.append("\n\n").append(history);

        return ctx.toString();
    }

    private String getProfileContext(Long userId) {
        CharacterUserProfile p = profileRepo.findById(userId).orElse(null);
        if (p == null || p.facts.equals("{}")) return "";
        try {
            Map<String, Object> facts = mapper.readValue(p.facts, Map.class);
            StringBuilder sb = new StringBuilder("USER PROFILE:\n");
            for (var entry : facts.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ")
                  .append(mapper.writeValueAsString(entry.getValue())).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to parse profile facts for user {}", userId, e);
            return "";
        }
    }

    private String getSessionContext(Long sessionId) {
        CharacterSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s == null) return "";

        StringBuilder ctx = new StringBuilder();

        if (s.compressedContext != null && !s.compressedContext.isEmpty()) {
            ctx.append("PREVIOUS CONVERSATION SUMMARY:\n").append(s.compressedContext).append("\n\n");
        }

        List<CharacterMessage> recent = messageRepo
            .findTop20BySessionIdAndIsCompressedFalseOrderByCreatedAtAsc(sessionId);
        if (!recent.isEmpty()) {
            ctx.append("RECENT MESSAGES:\n");
            for (var m : recent) {
                ctx.append(m.role.equals("user") ? "User: " : "Yui: ").append(m.content).append("\n");
            }
        }
        return ctx.toString();
    }

    // ---- Compression -------------------------------------------------------

    @Async
    public void maybeCompress(Long sessionId) {
        CharacterSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s == null || s.messageCount < COMPRESS_EVERY) return;

        List<CharacterMessage> all = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<CharacterMessage> unmarked = all.stream().filter(m -> !m.isCompressed).toList();
        if (unmarked.size() < COMPRESS_EVERY) return;

        // Take oldest unmarked messages, leaving 5 recent ones uncompressed
        int leaveRecent = Math.min(5, unmarked.size());
        List<CharacterMessage> toCompress = unmarked.subList(0, unmarked.size() - leaveRecent);
        if (toCompress.isEmpty()) return;

        String existingSummary = s.compressedContext != null ? s.compressedContext : "";
        String newSummary = summarizeMessages(existingSummary, toCompress);
        if (newSummary == null) return;

        s.compressedContext = newSummary;
        sessionRepo.save(s);

        for (var m : toCompress) {
            m.isCompressed = true;
            messageRepo.save(m);
        }
    }

    private String summarizeMessages(String existingSummary, List<CharacterMessage> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (var m : messages) {
            dialogue.append(m.role.equals("user") ? "User: " : "Yui: ").append(m.content).append("\n");
        }

        String prompt = existingSummary.isEmpty()
            ? "Summarize this conversation in 3-5 sentences. Keep key facts, decisions, and topics. Reply in the same language as the conversation.\n\n" + dialogue
            : "Here is an existing summary:\n" + existingSummary + "\n\nAdd these new messages to the summary. Keep it under 8 sentences. Keep key facts, decisions, and topics. Reply in the same language as the conversation.\n\n" + dialogue;

        return callLLM(prompt, 200);
    }

    // ---- Title generation ----------------------------------------------------

    @Async
    public void maybeGenerateTitle(CharacterSession s) {
        List<CharacterMessage> msgs = messageRepo.findBySessionIdOrderByCreatedAtAsc(s.id);
        if (msgs.size() < 2) return;
        String prompt = "Generate a SHORT title (3-6 words) for this conversation. Return ONLY the title, no quotes, no extra text. Language: " +
            (msgs.get(0).content.matches(".*[\\u4e00-\\u9fff].*") ? "Chinese" : "English") +
            "\n\nUser: " + msgs.get(0).content + "\nYui: " + msgs.get(1).content;
        String title = callLLM(prompt, 30);
        if (title != null && !title.isBlank()) {
            s.title = title.trim().replace("\"", "");
            sessionRepo.save(s);
        }
    }

    // ---- Profile extraction ------------------------------------------------

    @Async
    public void maybeUpdateProfile(Long userId, String userMessage, String aiReply) {
        CharacterUserProfile profile = profileRepo.findById(userId).orElse(null);
        String existing = (profile != null) ? profile.facts : "{}";

        String prompt = """
            Extract personal facts about the user from this conversation. Return ONLY valid JSON.
            Current profile: %s

            User message: %s
            AI reply: %s

            Return format (add or update facts — each fact MUST have a "recordedAt" timestamp in ISO format):
            {
              "name": {"value": "...", "recordedAt": "2026-..."},
              "goals": [{"value": "...", "recordedAt": "..."}],
              "prefs": [{"value": "...", "recordedAt": "..."}],
              "notes": [{"value": "...", "recordedAt": "..."}],
              "health_status": {"value": "...", "recordedAt": "..."}
            }

            Rules:
            - Only include fields where you found new or updated information.
            - If nothing new, return {}.
            - "notes" is for miscellaneous facts (hobbies, habits, life events).
            - "prefs" is for user preferences about communication style or app usage.
            - "goals" is for health or life goals.
            - "health_status" is a SINGLE object (latest state), not an array.
            - ALL facts MUST have a "recordedAt" field with current timestamp.
            - Merge with existing profile: keep old facts unless the new one supersedes them.
            """.formatted(existing, userMessage, aiReply);

        String result = callLLM(prompt, 400);
        if (result == null || result.trim().isEmpty() || result.trim().equals("{}")) return;

        try {
            Map<String, Object> existingFacts = mapper.readValue(existing, new TypeReference<>() {});
            Map<String, Object> newFacts = mapper.readValue(result, new TypeReference<>() {});
            existingFacts.putAll(newFacts);

            if (profile == null) {
                profile = new CharacterUserProfile();
                profile.userId = userId;
            }
            profile.facts = mapper.writeValueAsString(existingFacts);
            profile.updatedAt = LocalDateTime.now();
            profileRepo.save(profile);
        } catch (Exception e) {
            log.warn("Profile extraction failed for user {}", userId, e);
        }
    }

    // ---- LLM helper --------------------------------------------------------

    private String callLLM(String prompt, int maxTokens) {
        try {
            return llm.complete(List.of(Map.of("role", "user", "content", prompt)), 0.3, maxTokens);
        } catch (Exception e) {
            log.warn("LLM call failed for CharacterMemory (prompt len={})", prompt.length(), e);
            return null;
        }
    }
}

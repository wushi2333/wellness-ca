// Author: Wang Songyu, Xia Zihang
package sg.edu.nus.wellness.service;

import sg.edu.nus.wellness.dto.CharacterDTO;
import sg.edu.nus.wellness.model.UserProfile;
import sg.edu.nus.wellness.repository.UserProfileRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
@PropertySource(value = "classpath:character-prompts.properties", ignoreResourceNotFound = true)
public class CharacterService {
    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final CharacterMemoryService memory;
    private final UserProfileRepo profileRepo;
    private final DeepSeekClient llm;
    private final RagClient rag;

    public CharacterService(
            CharacterMemoryService memory,
            UserProfileRepo profileRepo,
            DeepSeekClient llm,
            RagClient rag) {
        this.memory = memory;
        this.profileRepo = profileRepo;
        this.llm = llm;
        this.rag = rag;
    }

    // RAG cache: userId → (wellnessData, expiryTime)
    private final ConcurrentMap<Long, RagCacheEntry> ragCache = new ConcurrentHashMap<>();
    private static final long RAG_CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private static class RagCacheEntry {
        final String data; final long expiry;
        RagCacheEntry(String data, long expiry) { this.data = data; this.expiry = expiry; }
    }

    // Prompts loaded from classpath:character-prompts.properties (editable without recompilation)
    @Value("${character.prompt.base:You are Yui (結衣), a cheerful and caring wellness companion.}")
    private String characterBase;

    @Value("${character.prompt.chat:CURRENT MODE: chat. You are a friendly companion chatting casually.}")
    private String chatModeSuffix;

    @Value("${character.prompt.agent:CURRENT MODE: agent. You have access to the user's wellness records.}")
    private String agentModeSuffix;

    @SuppressWarnings("unchecked")
    public CharacterDTO.Resp chat(Long userId, Long sessionId, String message, String mode) {
        // Create or reuse session
        if (sessionId == null) {
            sessionId = memory.createSession(userId, mode).id;
        }

        // Save user message
        memory.saveMessage(sessionId, "user", message, null, null);

        // Build system prompt with memory context
        boolean isAgent = "agent".equals(mode);
        String systemPrompt = characterBase + "\n\n" + (isAgent ? agentModeSuffix : chatModeSuffix);
        if (isAgent) {
            systemPrompt += "\n\nTODAY'S DATE: " + java.time.LocalDate.now() + " (use this exact date for recordDate)";
        }

        // Agent mode: inject wellness data from RAG asynchronously
        CompletableFuture<String> wellnessFuture = isAgent ? fetchWellnessData(userId, message) : null;

        String context = memory.buildContext(sessionId, userId, isAgent);
        if (!context.isEmpty()) {
            systemPrompt += "\n\n" + context;
        }

        // Append user profile data for agent mode
        if (isAgent) {
            String profileText = buildProfileContext(userId);
            if (!profileText.isEmpty()) {
                systemPrompt += "\n\n=== USER PROFILE ===\n" + profileText;
            }
        }

        // Join async RAG result and append wellness data
        if (wellnessFuture != null) {
            try {
                String wellnessData = wellnessFuture.get();
                if (!wellnessData.isEmpty()) {
                    systemPrompt += "\n\n=== USER WELLNESS DATA ===\n" + wellnessData;
                }
            } catch (Exception e) {
                log.warn("RAG wellness data fetch failed for user {}", userId, e);
            }
        }

        // Call DeepSeek via shared client
        List<Map<String,String>> llmMessages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", message)
        );
        String rawReply = llm.complete(llmMessages, 0.8, 512);

        CharacterDTO.Resp result = parseResponse(rawReply);
        result.sessionId = sessionId;

        // Save AI reply
        memory.saveMessage(sessionId, "assistant", result.reply, result.emotion, result.tools);

        // Async: compress context & update profile
        memory.maybeCompress(sessionId);
        memory.maybeUpdateProfile(userId, message, result.reply);

        return result;
    }

    /** Preload RAG data in background — call when chat opens. Non-blocking. */
    public void preloadRag(Long userId) {
        CompletableFuture.runAsync(() -> {
            // Use a generic wellness query to warm the cache
            String data = rag.search(userId, "sleep exercise wellness", 10);
            if (!data.isEmpty()) {
                ragCache.put(userId, new RagCacheEntry(data, System.currentTimeMillis() + RAG_CACHE_TTL_MS));
                log.info("RAG preloaded for user {}", userId);
            }
        });
    }

    /** Check if RAG cache has valid (non-expired) data for the user. */
    public boolean isRagReady(Long userId) {
        RagCacheEntry entry = ragCache.get(userId);
        return entry != null && System.currentTimeMillis() < entry.expiry;
    }

    private CompletableFuture<String> fetchWellnessData(Long userId, String query) {
        // Use cached data if available and fresh
        RagCacheEntry cached = ragCache.get(userId);
        if (cached != null && System.currentTimeMillis() < cached.expiry) {
            log.info("Using cached RAG data for user {}", userId);
            return CompletableFuture.completedFuture(cached.data);
        }
        // Fallback: fetch fresh (with timeout)
        return CompletableFuture.supplyAsync(() -> {
            String data = rag.search(userId, query, 6);
            if (!data.isEmpty()) {
                ragCache.put(userId, new RagCacheEntry(data, System.currentTimeMillis() + RAG_CACHE_TTL_MS));
            }
            return data;
        }).orTimeout(8, TimeUnit.SECONDS)
          .exceptionally(ex -> {
              log.warn("RAG wellness fetch timeout/error for user {}", userId);
              return "";
          });
    }

    /** Builds a human-readable profile summary for the agent prompt. */
    private String buildProfileContext(Long userId) {
        try {
            UserProfile p = profileRepo.findById(userId).orElse(null);
            if (p == null) return "";
            StringBuilder sb = new StringBuilder();
            if (p.getNickname() != null) sb.append("- Nickname: ").append(p.getNickname()).append("\n");
            if (p.getAge() != null) sb.append("- Age: ").append(p.getAge()).append("\n");
            if (p.getHeightCm() != null) sb.append("- Height: ").append(p.getHeightCm()).append(" cm\n");
            if (p.getWeightKg() != null) sb.append("- Weight: ").append(p.getWeightKg()).append(" kg\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to read profile for user {}", userId, e);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private CharacterDTO.Resp parseResponse(String raw) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            Map<String, Object> parsed = mapper.readValue(json, Map.class);
            String reply = (String) parsed.getOrDefault("reply", raw);
            String emotion = (String) parsed.getOrDefault("emotion", "happy");
            CharacterDTO.Resp resp = new CharacterDTO.Resp(reply, emotion);
            Object intentObj = parsed.get("intent");
            if (intentObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> intentMap = (Map<String, Object>) intentObj;
                resp.intent = intentMap;
            }
            // Parse tools_used
            Object toolsObj = parsed.get("tools_used");
            if (toolsObj instanceof List) {
                resp.tools = new ArrayList<>();
                for (Object item : (List<?>) toolsObj) {
                    if (item instanceof String) resp.tools.add((String) item);
                }
            }
            return resp;
        } catch (Exception e) {
            log.warn("Failed to parse character response JSON: {}", raw.substring(0, Math.min(100, raw.length())), e);
            return new CharacterDTO.Resp(raw, "happy");
        }
    }
}

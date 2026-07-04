// Author: Huang Qianer, Wang Songyu, Yutong Luo, Xia Zihang
package sg.edu.nus.wellness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared RAG (ChromaDB) client used by ChatService, CharacterService, and WellnessService.
 * Centralizes search, sync, and delete operations against the RAG sidecar.
 */
@Component
public class RagClient {

    private static final Logger log = LoggerFactory.getLogger(RagClient.class);

    private final RestTemplate http;
    private final String ragUrl;

    public RagClient(@Value("${app.rag.url:http://localhost:8001}") String ragUrl, RestTemplate rt) {
        this.ragUrl = ragUrl;
        this.http = rt;
    }

    /** Search ChromaDB for context relevant to the user query. */
    @SuppressWarnings("unchecked")
    public String search(Long userId, String query, int k) {
        try {
            Map<String, Object> ragReq = Map.of("query", query, "user_id", userId, "k", k);
            ResponseEntity<Map> ragResp = http.postForEntity(ragUrl + "/search", ragReq, Map.class);
            if (ragResp.getBody() != null) {
                return (String) ragResp.getBody().getOrDefault("context", "");
            }
        } catch (Exception e) {
            log.warn("RAG search failed for user {}: {}", userId, e.getMessage());
        }
        return "";
    }

    /** Sync a wellness record to ChromaDB (old flat format — backward compat). */
    public void sync(Long recordId, Long userId, Map<String, Object> data) {
        try {
            data.put("record_id", recordId.intValue());
            data.put("user_id", userId.intValue());
            http.postForEntity(ragUrl + "/sync", data, String.class);
        } catch (Exception e) {
            log.warn("RAG sync failed for record {}", recordId, e);
        }
    }

    /** Sync a sleep record to ChromaDB. */
    public void syncSleep(Long recordId, Long userId, double sleepHours,
                          String sleepTime, String wakeTime, int moodScore,
                          String recordDate, String notes) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("record_id", recordId.intValue());
            data.put("user_id", userId.intValue());
            data.put("sleep_hours", sleepHours);
            if (sleepTime != null && !sleepTime.isEmpty()) data.put("sleep_time", sleepTime);
            if (wakeTime != null && !wakeTime.isEmpty()) data.put("wake_time", wakeTime);
            if (moodScore > 0) data.put("mood_score", moodScore);
            data.put("record_date", recordDate);
            data.put("notes", notes != null ? notes : "");
            http.postForEntity(ragUrl + "/sync/sleep", data, String.class);
        } catch (Exception e) {
            log.warn("RAG sync sleep failed for record {}", recordId, e);
        }
    }

    /** Sync an exercise record to ChromaDB. */
    public void syncExercise(Long recordId, Long userId, String exerciseActivity,
                             int exerciseDuration, String recordDate, String notes) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("record_id", recordId.intValue());
            data.put("user_id", userId.intValue());
            data.put("exercise_activity", exerciseActivity != null ? exerciseActivity : "");
            data.put("exercise_duration", exerciseDuration);
            data.put("record_date", recordDate);
            data.put("notes", notes != null ? notes : "");
            http.postForEntity(ragUrl + "/sync/exercise", data, String.class);
        } catch (Exception e) {
            log.warn("RAG sync exercise failed for record {}", recordId, e);
        }
    }

    /** Delete a record from ChromaDB (old format). */
    public void delete(Long recordId) {
        try {
            http.delete(ragUrl + "/sync/" + recordId);
        } catch (Exception e) {
            log.warn("RAG sync delete failed for record {}", recordId, e);
        }
    }

    /** Delete a sleep record from ChromaDB. */
    public void deleteSleep(Long recordId) {
        try {
            http.delete(ragUrl + "/sync/sleep/" + recordId);
        } catch (Exception e) {
            log.warn("RAG sync delete sleep failed for record {}", recordId, e);
        }
    }

    /** Delete an exercise record from ChromaDB. */
    public void deleteExercise(Long recordId) {
        try {
            http.delete(ragUrl + "/sync/exercise/" + recordId);
        } catch (Exception e) {
            log.warn("RAG sync delete exercise failed for record {}", recordId, e);
        }
    }
}

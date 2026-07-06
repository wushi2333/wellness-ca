// Author: Xia Zihang
package sg.edu.nus.wellness.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.wellness.model.CharacterMessage;
import sg.edu.nus.wellness.model.CharacterSession;
import sg.edu.nus.wellness.service.CharacterMemoryService;

import java.util.*;

@RestController
public class CharacterSessionController {

    private final CharacterMemoryService memory;
    private final RequestUserExtractor userExt;

    public CharacterSessionController(CharacterMemoryService memory, RequestUserExtractor userExt) {
        this.memory = memory;
        this.userExt = userExt;
    }

    @GetMapping("/character/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        List<Map<String, Object>> result = new ArrayList<>();
        for (CharacterSession s : memory.getSessions(userId)) {
            result.add(Map.of("id", s.id, "title", s.title, "mode", s.mode,
                "messageCount", s.messageCount, "updatedAt", s.updatedAt.toString()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/character/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        CharacterSession s = memory.createSession(userId, body.getOrDefault("mode", "chat"));
        return ResponseEntity.ok(Map.of("id", s.id, "title", s.title, "mode", s.mode));
    }

    @DeleteMapping("/character/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable Long id, HttpServletRequest req) {
        memory.deleteSession(userExt.userId(req), id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/character/sessions/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long id, HttpServletRequest req) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CharacterMessage m : memory.getMessages(userExt.userId(req), id)) {
            Map<String, Object> msg = new java.util.LinkedHashMap<>();
            msg.put("id", m.id);
            msg.put("role", m.role);
            msg.put("content", m.content);
            msg.put("emotion", m.emotion != null ? m.emotion : "");
            msg.put("createdAt", m.createdAt.toString());
            if (m.tools != null && !m.tools.isEmpty()) {
                try {
                    msg.put("tools", new ObjectMapper().readValue(m.tools, List.class));
                } catch (Exception ignored) {}
            }
            result.add(msg);
        }
        return ResponseEntity.ok(result);
    }
}

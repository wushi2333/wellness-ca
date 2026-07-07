// Author: Guo Jiali, Xia Zihang
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.CharacterDTO;
import sg.edu.nus.wellness.dto.ExerciseRecordRequest;
import sg.edu.nus.wellness.dto.SleepRecordRequest;
import sg.edu.nus.wellness.model.CharacterMessage;
import sg.edu.nus.wellness.service.CharacterAsrService;
import sg.edu.nus.wellness.service.CharacterMemoryService;
import sg.edu.nus.wellness.service.CharacterService;
import sg.edu.nus.wellness.service.CharacterTtsService;
import sg.edu.nus.wellness.service.WellnessService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebChatController {
    private final CharacterService characterService;
    private final CharacterMemoryService memoryService;
    private final WellnessService wellnessService;
    private final CharacterTtsService ttsService;
    private final CharacterAsrService asrService;

    public WebChatController(CharacterService characterService,
                             CharacterMemoryService memoryService,
                             WellnessService wellnessService,
                             CharacterTtsService ttsService,
                             CharacterAsrService asrService) {
        this.characterService = characterService;
        this.memoryService = memoryService;
        this.wellnessService = wellnessService;
        this.ttsService = ttsService;
        this.asrService = asrService;
    }

    @GetMapping("/web/chat")
    public String chat(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        characterService.preloadRag(userId);
        addChatModel(session, model, userId);
        return "web/chat";
    }

    @PostMapping("/web/chat")
    public String send(@RequestParam String message,
                       @RequestParam(required = false) String mode,
                       HttpSession session,
                       RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();

        String cleanMessage = message == null ? "" : message.trim();
        String cleanMode = "agent".equals(mode) ? "agent" : WebSession.chatMode(session);
        WebSession.saveChatMode(session, cleanMode);
        if (cleanMessage.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please type a message.");
            return "redirect:/web/chat";
        }

        Long sid = WebSession.chatSessionId(session);
        if (sid != null) {
            try {
                memoryService.requireOwnedSession(userId, sid);
            } catch (RuntimeException ex) {
                WebSession.saveChatSession(session, null);
                sid = null;
            }
        }
        try {
            CharacterDTO.Resp response = characterService.chat(userId, sid, cleanMessage, cleanMode);
            WebSession.saveChatSession(session, response.sessionId);
            handleIntent(userId, response.intent, redirectAttributes);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Yui is temporarily unavailable. Please try again.");
        }
        return "redirect:/web/chat";
    }

    @PostMapping("/web/chat/mode")
    public String switchMode(@RequestParam String mode, HttpSession session) {
        WebSession.saveChatMode(session, mode);
        return "redirect:/web/chat";
    }

    /** JSON send endpoint for the chat page (instant send + loading spinner on the client).
     *  Returns the reply, emotion, sessionId, intent, tools, and any intent notice/target. */
    @PostMapping(value = "/web/chat/send", produces = "application/json")
    @ResponseBody
    public Map<String, Object> sendJson(@RequestParam String message,
                                        @RequestParam(required = false) String mode,
                                        @RequestParam(required = false) Long sessionId,
                                        HttpSession session) {
        Long userId = WebSession.userId(session);
        Map<String, Object> out = new LinkedHashMap<>();
        if (userId == null) {
            out.put("error", "not_logged_in");
            return out;
        }
        String cleanMessage = message == null ? "" : message.trim();
        String cleanMode = "agent".equals(mode) ? "agent" : WebSession.chatMode(session);
        WebSession.saveChatMode(session, cleanMode);
        if (cleanMessage.isEmpty()) {
            out.put("error", "empty");
            return out;
        }
        Long currentSessionId = sessionId != null ? sessionId : WebSession.chatSessionId(session);
        // If the referenced session no longer exists (deleted/expired), fall back to a fresh
        // session instead of erroring — matches Android's resilient behavior.
        if (currentSessionId != null) {
            try {
                memoryService.requireOwnedSession(userId, currentSessionId);
            } catch (RuntimeException ex) {
                currentSessionId = null;
                WebSession.saveChatSession(session, null);
            }
        }
        try {
            CharacterDTO.Resp response = characterService.chat(userId, currentSessionId, cleanMessage, cleanMode);
            WebSession.saveChatSession(session, response.sessionId);
            out.put("reply", response.reply == null ? "" : response.reply);
            out.put("emotion", response.emotion == null ? "" : response.emotion);
            out.put("sessionId", response.sessionId);
            out.put("intent", response.intent);
            out.put("tools", response.tools == null ? List.of() : response.tools);
            IntentResult ir = handleIntentJson(userId, response.intent);
            out.put("notice", ir.notice);
            out.put("intentTarget", ir.intentTarget);
        } catch (Throwable ex) {
            // Surface the real reason so the client can show something useful (and us, in logs).
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            System.err.println("[web/chat/send] userId=" + userId + " mode=" + cleanMode + " err: " + msg);
            ex.printStackTrace();
            out.put("error", msg);
        }
        return out;
    }

    @PostMapping("/web/chat/new")
    public String newChat(HttpSession session) {
        WebSession.saveChatSession(session, null);
        return "redirect:/web/chat";
    }

    @PostMapping("/web/chat/sessions/{id}")
    public String switchSession(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            var selected = memoryService.requireOwnedSession(userId, id);
            WebSession.saveChatSession(session, id);
            WebSession.saveChatMode(session, selected.mode);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Chat session not found.");
        }
        return "redirect:/web/chat";
    }

    @PostMapping("/web/chat/sessions/{id}/delete")
    public String deleteSession(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            memoryService.deleteSession(userId, id);
            if (id.equals(WebSession.chatSessionId(session))) WebSession.saveChatSession(session, null);
            redirectAttributes.addFlashAttribute("success", "Chat deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Chat session not found.");
        }
        return "redirect:/web/chat";
    }

    @PostMapping("/web/chat/clear")
    public String clear(HttpSession session) {
        WebSession.saveChatSession(session, null);
        return "redirect:/web/chat";
    }

    private void addChatModel(HttpSession session, Model model, Long userId) {
        WebSession.addCommonModel(session, model, "chat");
        Long currentSessionId = WebSession.chatSessionId(session);
        List<CharacterMessage> messages = currentSessionId == null
                ? List.of()
                : memoryService.getMessages(userId, currentSessionId);
        model.addAttribute("messages", WebViewModels.chatMessages(messages));
        List<WebViewModels.ChatSessionView> sessions =
                WebViewModels.chatSessions(memoryService.getSessions(userId), currentSessionId);
        model.addAttribute("sessions", sessions);
        model.addAttribute("currentSessionId", currentSessionId);
        // Resolve the current session's title (AI-generated short title) for the header.
        String currentSessionTitle = null;
        if (currentSessionId != null) {
            for (WebViewModels.ChatSessionView s : sessions) {
                if (currentSessionId.equals(s.id())) { currentSessionTitle = s.title(); break; }
            }
        }
        model.addAttribute("currentSessionTitle", currentSessionTitle);
        model.addAttribute("ragReady", characterService.isRagReady(userId));
    }

    /** TTS proxy: synthesize reply text to MP3. Same-origin + session auth, browser needs no JWT. */
    @PostMapping(value = "/web/chat/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> tts(@RequestParam String text,
                                      @RequestParam(required = false) String emotion,
                                      HttpSession session) {
        if (WebSession.userId(session) == null) return ResponseEntity.status(401).build();
        byte[] audio = ttsService.synthesize(text, emotion == null ? "" : emotion);
        return audio != null
                ? ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audio)
                : ResponseEntity.status(502).build();
    }

    /** ASR proxy: transcribe base64 PCM (16kHz/16bit/mono) to text. */
    @PostMapping(value = "/web/chat/asr", produces = "application/json")
    @ResponseBody
    public Map<String, String> asr(@RequestParam String audio,
                                   @RequestParam(required = false) String language,
                                   HttpSession session) {
        Map<String, String> out = new LinkedHashMap<>();
        if (WebSession.userId(session) == null) {
            out.put("error", "not_logged_in");
            return out;
        }
        String lang = language != null ? language
                : ("zh".equals(WebSession.language(session)) ? "zh-CN" : "en-US");
        String text = asrService.recognize(audio, lang);
        out.put("text", text == null ? "" : text);
        return out;
    }

    private void handleIntent(Long userId, Map<String, Object> intent, RedirectAttributes redirectAttributes) {
        if (intent == null) return;
        Object actionObj = intent.get("action");
        if (actionObj == null) return;
        String action = actionObj.toString();
        if ("navigate".equals(action)) {
            redirectAttributes.addFlashAttribute("intentTarget", String.valueOf(intent.getOrDefault("target", "dashboard")));
            return;
        }
        if ("create_record".equals(action)) {
            String msg = createRecordFromIntent(userId, intent);
            if (msg != null) {
                if (msg.startsWith("ERROR:")) redirectAttributes.addFlashAttribute("error", msg.substring(6));
                else redirectAttributes.addFlashAttribute("success", msg);
            }
        }
    }

    /** JSON-path intent handling: returns a notice (create_record) and/or intentTarget (navigate). */
    private IntentResult handleIntentJson(Long userId, Map<String, Object> intent) {
        IntentResult r = new IntentResult();
        if (intent == null) return r;
        Object actionObj = intent.get("action");
        if (actionObj == null) return r;
        String action = actionObj.toString();
        if ("navigate".equals(action)) {
            r.intentTarget = String.valueOf(intent.getOrDefault("target", "dashboard"));
            return r;
        }
        if ("create_record".equals(action)) {
            String msg = createRecordFromIntent(userId, intent);
            if (msg != null) r.notice = msg.startsWith("ERROR:") ? msg.substring(6) : msg;
        }
        return r;
    }

    private String createRecordFromIntent(Long userId, Map<String, Object> intent) {
        String type = String.valueOf(intent.getOrDefault("recordType", ""));
        String date = String.valueOf(intent.getOrDefault("recordDate", LocalDate.now().toString()));
        try {
            if ("sleep".equals(type)) {
                SleepRecordRequest req = new SleepRecordRequest();
                req.sleepHours = number(intent.get("sleepHours"), 0.0).doubleValue();
                req.sleepTime = string(intent.get("sleepTime"), "");
                req.wakeTime = string(intent.get("wakeTime"), "");
                req.moodScore = number(intent.get("moodScore"), 0).intValue();
                req.recordDate = date;
                req.notes = string(intent.get("notes"), "Created via agent");
                wellnessService.createSleep(userId, req);
                return "Sleep record saved via agent.";
            } else if ("exercise".equals(type)) {
                ExerciseRecordRequest req = new ExerciseRecordRequest();
                req.exerciseActivity = string(intent.get("exerciseActivity"), "Other");
                req.exerciseDuration = number(intent.get("exerciseDuration"), 0).intValue();
                req.recordDate = date;
                req.notes = string(intent.get("notes"), "Created via agent");
                wellnessService.createExercise(userId, req);
                return "Exercise record saved via agent.";
            }
        } catch (RuntimeException ex) {
            return "ERROR:Agent suggested a record, but it could not be saved.";
        }
        return null;
    }

    private static class IntentResult {
        String notice;
        String intentTarget;
    }

    private Number number(Object value, Number fallback) {
        if (value instanceof Number n) return n;
        if (value == null) return fallback;
        try {
            String text = value.toString();
            return text.contains(".") ? Double.parseDouble(text) : Integer.parseInt(text);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String string(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }
}

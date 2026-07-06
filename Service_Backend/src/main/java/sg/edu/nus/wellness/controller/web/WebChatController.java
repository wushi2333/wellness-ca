// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.CharacterDTO;
import sg.edu.nus.wellness.dto.ExerciseRecordRequest;
import sg.edu.nus.wellness.dto.SleepRecordRequest;
import sg.edu.nus.wellness.model.CharacterMessage;
import sg.edu.nus.wellness.service.CharacterMemoryService;
import sg.edu.nus.wellness.service.CharacterService;
import sg.edu.nus.wellness.service.WellnessService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class WebChatController {
    private final CharacterService characterService;
    private final CharacterMemoryService memoryService;
    private final WellnessService wellnessService;

    public WebChatController(CharacterService characterService,
                             CharacterMemoryService memoryService,
                             WellnessService wellnessService) {
        this.characterService = characterService;
        this.memoryService = memoryService;
        this.wellnessService = wellnessService;
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

        try {
            CharacterDTO.Resp response = characterService.chat(
                    userId, WebSession.chatSessionId(session), cleanMessage, cleanMode);
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
        model.addAttribute("sessions", WebViewModels.chatSessions(memoryService.getSessions(userId), currentSessionId));
        model.addAttribute("currentSessionId", currentSessionId);
        model.addAttribute("ragReady", characterService.isRagReady(userId));
    }

    private void handleIntent(Long userId, Map<String, Object> intent, RedirectAttributes redirectAttributes) {
        if (intent == null) return;
        Object actionObj = intent.get("action");
        if (actionObj == null) return;
        String action = actionObj.toString();
        if ("navigate".equals(action)) {
            String target = String.valueOf(intent.getOrDefault("target", "dashboard"));
            redirectAttributes.addFlashAttribute("intentTarget", target);
            return;
        }
        if ("create_record".equals(action)) {
            createRecordFromIntent(userId, intent, redirectAttributes);
        }
    }

    private void createRecordFromIntent(Long userId, Map<String, Object> intent, RedirectAttributes redirectAttributes) {
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
                redirectAttributes.addFlashAttribute("success", "Sleep record saved via agent.");
            } else if ("exercise".equals(type)) {
                ExerciseRecordRequest req = new ExerciseRecordRequest();
                req.exerciseActivity = string(intent.get("exerciseActivity"), "Other");
                req.exerciseDuration = number(intent.get("exerciseDuration"), 0).intValue();
                req.recordDate = date;
                req.notes = string(intent.get("notes"), "Created via agent");
                wellnessService.createExercise(userId, req);
                redirectAttributes.addFlashAttribute("success", "Exercise record saved via agent.");
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Agent suggested a record, but it could not be saved.");
        }
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

// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.service.ChatService;

import java.util.ArrayList;
import java.util.List;

@Controller
public class WebChatController {
    private static final String CHAT_MESSAGES = "WEB_CHAT_MESSAGES";

    private final ChatService chatService;

    public WebChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/web/chat")
    public String chat(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) {
            return WebSession.redirectToLogin();
        }

        WebSession.addCommonModel(session, model, "chat");
        model.addAttribute("messages", messages(session));
        return "web/chat";
    }

    @PostMapping("/web/chat")
    public String send(@RequestParam String message, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        String cleanMessage = message == null ? "" : message.trim();
        if (cleanMessage.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please type a message.");
            return "redirect:/web/chat";
        }

        List<ChatMessage> chat = messages(session);
        chat.add(new ChatMessage("user", cleanMessage));
        try {
            String reply = chatService.chat(userId, cleanMessage);
            chat.add(new ChatMessage("ai", reply));
        } catch (RuntimeException ex) {
            chat.add(new ChatMessage("ai", "The assistant is temporarily unavailable. Please try again later."));
        }
        session.setAttribute(CHAT_MESSAGES, chat);
        return "redirect:/web/chat";
    }

    @PostMapping("/web/chat/clear")
    public String clear(HttpSession session) {
        session.removeAttribute(CHAT_MESSAGES);
        return "redirect:/web/chat";
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> messages(HttpSession session) {
        Object value = session.getAttribute(CHAT_MESSAGES);
        if (value instanceof List<?>) {
            return (List<ChatMessage>) value;
        }
        List<ChatMessage> empty = new ArrayList<>();
        session.setAttribute(CHAT_MESSAGES, empty);
        return empty;
    }

    public static class ChatMessage {
        public final String role;
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}

// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

final class WebSession {
    static final String USER_ID = "WEB_USER_ID";
    static final String USERNAME = "WEB_USERNAME";
    static final String ACCESS_TOKEN = "WEB_ACCESS_TOKEN";
    static final String CHAT_SESSION_ID = "WEB_CHARACTER_SESSION_ID";
    static final String CHAT_MODE = "WEB_CHARACTER_MODE";
    static final String LANGUAGE = "WEB_LANGUAGE";

    private WebSession() {}

    static boolean isLoggedIn(HttpSession session) {
        return session.getAttribute(USER_ID) instanceof Long;
    }

    static Long userId(HttpSession session) {
        Object value = session.getAttribute(USER_ID);
        return value instanceof Long ? (Long) value : null;
    }

    static String username(HttpSession session) {
        Object value = session.getAttribute(USERNAME);
        return value == null ? "" : value.toString();
    }

    static String accessToken(HttpSession session) {
        Object value = session.getAttribute(ACCESS_TOKEN);
        return value == null ? "" : value.toString();
    }

    static Long chatSessionId(HttpSession session) {
        Object value = session.getAttribute(CHAT_SESSION_ID);
        return value instanceof Long ? (Long) value : null;
    }

    static void saveChatSession(HttpSession session, Long sessionId) {
        if (sessionId == null) session.removeAttribute(CHAT_SESSION_ID);
        else session.setAttribute(CHAT_SESSION_ID, sessionId);
    }

    static void saveUsername(HttpSession session, String username) {
        session.setAttribute(USERNAME, username == null ? "" : username);
    }

    static String chatMode(HttpSession session) {
        Object value = session.getAttribute(CHAT_MODE);
        String mode = value == null ? "chat" : value.toString();
        return "agent".equals(mode) ? "agent" : "chat";
    }

    static void saveChatMode(HttpSession session, String mode) {
        session.setAttribute(CHAT_MODE, "agent".equals(mode) ? "agent" : "chat");
    }

    static String language(HttpSession session) {
        Object value = session.getAttribute(LANGUAGE);
        String lang = value == null ? "en" : value.toString();
        return "zh".equals(lang) ? "zh" : "en";
    }

    static void saveLanguage(HttpSession session, String language) {
        session.setAttribute(LANGUAGE, "zh".equals(language) ? "zh" : "en");
    }

    static void saveLogin(HttpSession session, Long userId, String username, String accessToken) {
        session.setAttribute(USER_ID, userId);
        session.setAttribute(USERNAME, username);
        session.setAttribute(ACCESS_TOKEN, accessToken);
        if (session.getAttribute(CHAT_MODE) == null) session.setAttribute(CHAT_MODE, "chat");
        if (session.getAttribute(LANGUAGE) == null) session.setAttribute(LANGUAGE, "en");
    }

    static void addCommonModel(HttpSession session, Model model, String activePage) {
        model.addAttribute("loggedIn", isLoggedIn(session));
        model.addAttribute("username", username(session));
        model.addAttribute("activePage", activePage);
        model.addAttribute("chatMode", chatMode(session));
        model.addAttribute("language", language(session));
    }

    static String redirectToLogin() {
        return "redirect:/web/login";
    }
}

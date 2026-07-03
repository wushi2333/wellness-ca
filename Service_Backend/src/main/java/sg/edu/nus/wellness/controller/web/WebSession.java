// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

final class WebSession {
    static final String USER_ID = "WEB_USER_ID";
    static final String USERNAME = "WEB_USERNAME";
    static final String ACCESS_TOKEN = "WEB_ACCESS_TOKEN";

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

    static void saveLogin(HttpSession session, Long userId, String username, String accessToken) {
        session.setAttribute(USER_ID, userId);
        session.setAttribute(USERNAME, username);
        session.setAttribute(ACCESS_TOKEN, accessToken);
    }

    static void addCommonModel(HttpSession session, Model model, String activePage) {
        model.addAttribute("loggedIn", isLoggedIn(session));
        model.addAttribute("username", username(session));
        model.addAttribute("activePage", activePage);
    }

    static String redirectToLogin() {
        return "redirect:/web/login";
    }
}

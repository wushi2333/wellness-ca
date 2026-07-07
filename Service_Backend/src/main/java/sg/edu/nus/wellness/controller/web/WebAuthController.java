// Author: Guo Jiali, Xia Zihang
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.AuthResponse;
import sg.edu.nus.wellness.service.AuthService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class WebAuthController {
    private static final String GOOGLE_CLIENT_ID = "375016829980-l1gpslqj0cltlc5aqf2f403c52oepeg7.apps.googleusercontent.com";
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String SESSION_Google_idToken = "WEB_GOOGLE_ID_TOKEN";
    private static final String SESSION_Google_email = "WEB_GOOGLE_EMAIL";

    private final AuthService authService;

    @Value("${app.web.base-url:http://localhost:8000}")
    private String webBaseUrl;

    public WebAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"/", "/web", "/web/", "/web/login"})
    public String loginPage(HttpSession session, Model model) {
        if (WebSession.isLoggedIn(session)) {
            return "redirect:/web/dashboard";
        }
        WebSession.addCommonModel(session, model, "login");
        return "web/login";
    }

    /** Kick off Google OAuth: redirect the browser to Google's consent screen. */
    @GetMapping("/web/auth/google")
    public String googleStart() {
        String redirectUri = webBaseUrl + "/web/auth/google/callback";
        String url = GOOGLE_AUTH_URL
                + "?client_id=" + GOOGLE_CLIENT_ID
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("email profile openid", StandardCharsets.UTF_8)
                + "&access_type=offline&prompt=select_account";
        return "redirect:" + url;
    }

    /** Google redirects back here with ?code=... — exchange it via AuthService and log in / resolve. */
    @GetMapping("/web/auth/google/callback")
    public String googleCallback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String error,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (error != null || code == null || code.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Google sign-in was cancelled.");
            return "redirect:/web/login";
        }
        String redirectUri = webBaseUrl + "/web/auth/google/callback";
        Map<String, Object> res = authService.googleLogin(code, "", "", redirectUri);

        // Direct login success.
        if (res.containsKey("accessToken")) {
            completeLogin(session, res);
            return "redirect:/web/dashboard";
        }
        // Email already bound to a local account → ask the user to confirm logging in as that user.
        if (Boolean.TRUE.equals(res.get("conflict"))) {
            session.setAttribute(SESSION_Google_idToken, res.get("idToken"));
            session.setAttribute(SESSION_Google_email, res.get("email"));
            redirectAttributes.addFlashAttribute("conflictExistingUsername", res.get("existingUsername"));
            redirectAttributes.addFlashAttribute("conflictEmail", res.get("email"));
            return "redirect:/web/google-resolve";
        }
        // New Google user → ask for a username.
        if (Boolean.TRUE.equals(res.get("newUser"))) {
            session.setAttribute(SESSION_Google_idToken, res.get("idToken"));
            session.setAttribute(SESSION_Google_email, res.get("email"));
            redirectAttributes.addFlashAttribute("suggestedUsername", res.get("suggestedUsername"));
            redirectAttributes.addFlashAttribute("googleEmail", res.get("email"));
            if ("username_taken".equals(res.get("error"))) {
                redirectAttributes.addFlashAttribute("error", "That username is taken — try another.");
            }
            return "redirect:/web/google-username";
        }
        redirectAttributes.addFlashAttribute("error", "Google authentication failed.");
        return "redirect:/web/login";
    }

    /** Confirm logging in as the existing account that owns the conflicting email. */
    @PostMapping("/web/google-resolve")
    public String googleResolveConfirm(HttpSession session, RedirectAttributes redirectAttributes) {
        String idToken = (String) session.getAttribute(SESSION_Google_idToken);
        String email = (String) session.getAttribute(SESSION_Google_email);
        if (idToken == null) {
            redirectAttributes.addFlashAttribute("error", "Google session expired. Please try again.");
            return "redirect:/web/login";
        }
        // The existing user's email is known; googleLogin with empty username finds them by idToken+email.
        // To log in as the existing user, we pass the idToken and let the service match by email.
        Map<String, Object> res = authService.googleLogin("", idToken, "", webBaseUrl + "/web/auth/google/callback");
        if (res.containsKey("accessToken")) {
            completeLogin(session, res);
            return "redirect:/web/dashboard";
        }
        redirectAttributes.addFlashAttribute("error", "Could not complete Google sign-in. Please try again.");
        return "redirect:/web/login";
    }

    @GetMapping("/web/google-resolve")
    public String googleResolvePage(HttpSession session, Model model) {
        if (WebSession.isLoggedIn(session)) return "redirect:/web/dashboard";
        WebSession.addCommonModel(session, model, "login");
        return "web/google-resolve";
    }

    /** New Google user picks a username; create the account. */
    @PostMapping("/web/google-username")
    public String googleUsernameConfirm(@RequestParam String username,
                                        HttpSession session,
                                        RedirectAttributes redirectAttributes) {
        String idToken = (String) session.getAttribute(SESSION_Google_idToken);
        if (idToken == null) {
            redirectAttributes.addFlashAttribute("error", "Google session expired. Please try again.");
            return "redirect:/web/login";
        }
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.length() < 3) {
            redirectAttributes.addFlashAttribute("error", "Username must be at least 3 characters.");
            redirectAttributes.addFlashAttribute("suggestedUsername", cleanUsername);
            return "redirect:/web/google-username";
        }
        Map<String, Object> res = authService.googleLogin("", idToken, cleanUsername, webBaseUrl + "/web/auth/google/callback");
        if (res.containsKey("accessToken")) {
            completeLogin(session, res);
            return "redirect:/web/dashboard";
        }
        if (Boolean.TRUE.equals(res.get("newUser")) && "username_taken".equals(res.get("error"))) {
            redirectAttributes.addFlashAttribute("error", "That username is taken — try another.");
            redirectAttributes.addFlashAttribute("suggestedUsername", res.get("suggestedUsername"));
            return "redirect:/web/google-username";
        }
        redirectAttributes.addFlashAttribute("error", "Could not create account. Please try again.");
        return "redirect:/web/login";
    }

    @GetMapping("/web/google-username")
    public String googleUsernamePage(HttpSession session, Model model) {
        if (WebSession.isLoggedIn(session)) return "redirect:/web/dashboard";
        WebSession.addCommonModel(session, model, "login");
        return "web/google-username";
    }

    private void completeLogin(HttpSession session, Map<String, Object> res) {
        Object userIdObj = res.get("userId");
        Long userId = userIdObj instanceof Number n ? n.longValue()
                : userIdObj != null ? Long.valueOf(userIdObj.toString()) : null;
        WebSession.saveLogin(session, userId,
                String.valueOf(res.getOrDefault("username", "")),
                String.valueOf(res.getOrDefault("accessToken", "")));
        session.removeAttribute(SESSION_Google_idToken);
        session.removeAttribute(SESSION_Google_email);
    }

    @PostMapping("/web/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanPassword = password == null ? "" : password.trim();

        if (cleanUsername.isEmpty() || cleanPassword.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter username and password.");
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            return "redirect:/web/login";
        }

        try {
            AuthResponse response = authService.login(cleanUsername, cleanPassword);
            WebSession.saveLogin(session, response.userId, response.username, response.accessToken);
            redirectAttributes.addFlashAttribute("success", "Login successful.");
            return "redirect:/web/dashboard";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Incorrect username or password.");
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            return "redirect:/web/login";
        }
    }

    @GetMapping("/web/register")
    public String registerPage(HttpSession session, Model model) {
        if (WebSession.isLoggedIn(session)) {
            return "redirect:/web/dashboard";
        }
        WebSession.addCommonModel(session, model, "register");
        return "web/register";
    }

    @PostMapping("/web/register")
    public String register(@RequestParam String username,
                           @RequestParam(required = false) String email,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim();
        String cleanPassword = password == null ? "" : password.trim();

        if (cleanUsername.length() < 3) {
            redirectAttributes.addFlashAttribute("error", "Username must be at least 3 characters.");
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            redirectAttributes.addFlashAttribute("emailInput", cleanEmail);
            return "redirect:/web/register";
        }
        if (!cleanEmail.isEmpty() && !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid email address.");
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            redirectAttributes.addFlashAttribute("emailInput", cleanEmail);
            return "redirect:/web/register";
        }
        if (cleanPassword.length() < 8 || !cleanPassword.matches(".*[A-Za-z].*") || !cleanPassword.matches(".*\\d.*")) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters and include a letter and a digit.");
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            redirectAttributes.addFlashAttribute("emailInput", cleanEmail);
            return "redirect:/web/register";
        }

        try {
            authService.register(cleanUsername, cleanPassword, cleanEmail.isEmpty() ? null : cleanEmail);
            redirectAttributes.addFlashAttribute("success", "Register successful. Please log in.");
            return "redirect:/web/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            redirectAttributes.addFlashAttribute("emailInput", cleanEmail);
            return "redirect:/web/register";
        }
    }

    @PostMapping("/web/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("success", "You have logged out.");
        return "redirect:/web/login";
    }
}

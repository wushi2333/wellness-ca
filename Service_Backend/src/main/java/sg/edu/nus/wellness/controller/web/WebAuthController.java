// Author: Wellness CA Team
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.AuthResponse;
import sg.edu.nus.wellness.service.AuthService;

@Controller
public class WebAuthController {
    private final AuthService authService;

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
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanPassword = password == null ? "" : password.trim();

        if (cleanUsername.length() < 3 || cleanPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Username must be at least 3 characters and password at least 6 characters.");
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
            return "redirect:/web/register";
        }

        try {
            authService.register(cleanUsername, cleanPassword);
            redirectAttributes.addFlashAttribute("success", "Register successful. Please log in.");
            return "redirect:/web/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("usernameInput", cleanUsername);
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

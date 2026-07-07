// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.UserResponse;
import sg.edu.nus.wellness.service.UserService;

@Controller
public class WebSettingsController {
    private final UserService userService;

    public WebSettingsController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/web/settings")
    public String settings(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        WebSession.addCommonModel(session, model, "settings");
        model.addAttribute("profile", userService.getCombinedUser(userId));
        return "web/settings";
    }

    @PostMapping("/web/settings/language")
    public String language(@RequestParam String language,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        WebSession.saveLanguage(session, language);
        // Redirect back to login if not logged in, settings page otherwise.
        if (!WebSession.isLoggedIn(session)) return "redirect:/web/login";
        return "redirect:/web/settings";
    }

    @GetMapping("/web/change-password")
    public String changePasswordPage(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        WebSession.addCommonModel(session, model, "settings");
        UserResponse profile = userService.getCombinedUser(userId);
        model.addAttribute("profile", profile);
        model.addAttribute("isGoogleUser", "GOOGLE".equals(profile.provider));
        return "web/change-password";
    }

    @PostMapping("/web/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/web/change-password";
        }
        try {
            userService.changePassword(userId, oldPassword, newPassword);
            redirectAttributes.addFlashAttribute("success", "Password updated.");
            return "redirect:/web/settings";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/web/change-password";
        }
    }

    @PostMapping("/web/account/delete")
    public String deleteAccount(@RequestParam(required = false) String confirm,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        if (!"DELETE".equals(confirm)) {
            redirectAttributes.addFlashAttribute("error", "Type DELETE to confirm account deletion.");
            return "redirect:/web/settings";
        }
        try {
            userService.deleteAccount(userId);
            session.invalidate();
            redirectAttributes.addFlashAttribute("success", "Account deleted.");
            return "redirect:/web/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/web/settings";
        }
    }
}

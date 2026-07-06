// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.UserProfileRequest;
import sg.edu.nus.wellness.dto.UserResponse;
import sg.edu.nus.wellness.service.UserService;

@Controller
public class WebProfileController {
    private final UserService userService;

    public WebProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/web/profile")
    public String profile(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        addProfileModel(session, model, userId);
        return "web/profile";
    }

    @PostMapping("/web/profile/metrics")
    public String updateMetrics(@RequestParam(required = false) String nickname,
                                @RequestParam(required = false) Integer heightCm,
                                @RequestParam(required = false) Integer age,
                                @RequestParam(required = false) Double weightKg,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            UserProfileRequest body = new UserProfileRequest();
            body.nickname = clean(nickname);
            body.heightCm = heightCm;
            body.age = age;
            body.weightKg = weightKg;
            userService.updateProfile(userId, body);
            redirectAttributes.addFlashAttribute("success", "Profile updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/web/profile";
    }

    @PostMapping("/web/profile/username")
    public String changeUsername(@RequestParam String username,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            userService.changeUsername(userId, username);
            WebSession.saveUsername(session, username == null ? "" : username.trim());
            redirectAttributes.addFlashAttribute("success", "Username updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/web/profile";
    }

    @PostMapping("/web/profile/email")
    public String bindEmail(@RequestParam String email,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            userService.bindEmail(userId, email);
            redirectAttributes.addFlashAttribute("success", "Email updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/web/profile";
    }

    @PostMapping("/web/profile/avatar")
    public String uploadAvatar(@RequestParam("file") MultipartFile file,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            userService.uploadAvatar(userId, file);
            redirectAttributes.addFlashAttribute("success", "Avatar updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/web/profile";
    }

    private void addProfileModel(HttpSession session, Model model, Long userId) {
        WebSession.addCommonModel(session, model, "profile");
        UserResponse profile = userService.getCombinedUser(userId);
        model.addAttribute("profile", profile);
        model.addAttribute("isGoogleUser", "GOOGLE".equals(profile.provider));
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

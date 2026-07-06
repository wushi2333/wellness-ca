// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sg.edu.nus.wellness.dto.DailyWellnessResponse;
import sg.edu.nus.wellness.dto.PagedResponse;
import sg.edu.nus.wellness.dto.UserResponse;
import sg.edu.nus.wellness.service.UserService;
import sg.edu.nus.wellness.service.WellnessService;

@Controller
public class WebDashboardController {
    private final WellnessService wellnessService;
    private final UserService userService;

    public WebDashboardController(WellnessService wellnessService, UserService userService) {
        this.wellnessService = wellnessService;
        this.userService = userService;
    }

    @GetMapping("/web/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }
        WebSession.addCommonModel(session, model, "dashboard");
        PagedResponse<DailyWellnessResponse> records = wellnessService.listDailyPaged(userId, 0, 90);
        UserResponse profile = userService.getCombinedUser(userId);
        model.addAttribute("profile", profile);
        model.addAttribute("summary", WebViewModels.dashboard(records.content));
        return "web/dashboard";
    }
}

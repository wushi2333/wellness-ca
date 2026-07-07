// Author: Guo Jiali, Xia Zihang
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sg.edu.nus.wellness.dto.DailyWellnessResponse;
import sg.edu.nus.wellness.dto.PagedResponse;
import sg.edu.nus.wellness.dto.UserResponse;
import sg.edu.nus.wellness.service.UserService;
import sg.edu.nus.wellness.service.WellnessService;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
public class WebDashboardController {
    private final WellnessService wellnessService;
    private final UserService userService;
    private final MessageSource messageSource;

    public WebDashboardController(WellnessService wellnessService, UserService userService, MessageSource messageSource) {
        this.wellnessService = wellnessService;
        this.userService = userService;
        this.messageSource = messageSource;
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
        int hour = LocalTime.now().getHour();
        String greetKey = hour < 12 ? "dash.greeting.morning" : hour < 18 ? "dash.greeting.afternoon" : "dash.greeting.evening";
        model.addAttribute("greeting", messageSource.getMessage(greetKey, null, LocaleContextHolder.getLocale()));
        int tipIdx = Math.floorMod(LocalDate.now().getDayOfYear(), 5);
        String tip = messageSource.getMessage("dash.tip." + tipIdx, null, LocaleContextHolder.getLocale());
        model.addAttribute("profile", profile);
        model.addAttribute("summary", WebViewModels.dashboard(records.content, tip));
        return "web/dashboard";
    }
}

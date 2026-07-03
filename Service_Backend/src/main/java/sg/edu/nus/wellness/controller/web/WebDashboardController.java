// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebDashboardController {
    @GetMapping("/web/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) {
            return WebSession.redirectToLogin();
        }
        WebSession.addCommonModel(session, model, "dashboard");
        return "web/dashboard";
    }
}

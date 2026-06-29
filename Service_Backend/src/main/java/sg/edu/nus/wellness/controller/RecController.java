// Author: Xia Zihang
package sg.edu.nus.wellness.controller;
import sg.edu.nus.wellness.service.RecService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class RecController {
    private final RecService rs;
    public RecController(RecService r) { rs=r; }

    @GetMapping("/recommendations")
    public Map<String,?> get(HttpServletRequest req) {
        return Map.of("recommendations", rs.generate((Long)req.getAttribute("userId")));
    }
}

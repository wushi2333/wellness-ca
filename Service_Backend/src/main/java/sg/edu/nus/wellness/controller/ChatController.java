// Author: Xia Zihang
package sg.edu.nus.wellness.controller;
import sg.edu.nus.wellness.dto.ChatDTO;
import sg.edu.nus.wellness.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChatController {
    private final ChatService cs;
    public ChatController(ChatService c) { cs=c; }

    @PostMapping("/chat")
    public ChatDTO.Resp chat(@RequestBody ChatDTO.Req req, HttpServletRequest r) {
        return new ChatDTO.Resp(cs.chat((Long)r.getAttribute("userId"), req.message));
    }
}

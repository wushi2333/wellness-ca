// Author: Wang Songyu, Xia Zihang
package sg.edu.nus.wellness.controller;

import sg.edu.nus.wellness.dto.CharacterDTO;
import sg.edu.nus.wellness.service.CharacterAsrService;
import sg.edu.nus.wellness.service.CharacterService;
import sg.edu.nus.wellness.service.CharacterTtsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CharacterController {

    private final CharacterService service;
    private final CharacterTtsService ttsService;
    private final CharacterAsrService asrService;

    public CharacterController(CharacterService service, CharacterTtsService ttsService,
                               CharacterAsrService asrService) {
        this.service = service;
        this.ttsService = ttsService;
        this.asrService = asrService;
    }

    @PostMapping("/character/chat")
    public ResponseEntity<CharacterDTO.Resp> chat(
            @RequestBody CharacterDTO.Req req,
            HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        return ResponseEntity.ok(service.chat(userId, req.sessionId, req.message, "chat"));
    }

    @PostMapping("/character/agent")
    public ResponseEntity<CharacterDTO.Resp> agent(
            @RequestBody CharacterDTO.Req req,
            HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        return ResponseEntity.ok(service.chat(userId, req.sessionId, req.message, "agent"));
    }

    @PostMapping("/character/tts")
    public ResponseEntity<byte[]> tts(@RequestBody Map<String, String> body) {
        byte[] audio = ttsService.synthesize(
            body.get("text"), body.getOrDefault("emotion", ""));
        return audio != null
            ? ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audio)
            : ResponseEntity.status(502).build();
    }

    @PostMapping("/character/asr")
    public ResponseEntity<Map<String, String>> asr(@RequestBody Map<String, String> body) {
        String text = asrService.recognize(body.get("audio"));
        return text != null
            ? ResponseEntity.ok(Map.of("text", text))
            : ResponseEntity.status(502).body(Map.of("text", ""));
    }
}

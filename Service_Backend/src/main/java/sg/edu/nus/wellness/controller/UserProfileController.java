// Author: Xia Zihang
package sg.edu.nus.wellness.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import sg.edu.nus.wellness.dto.UserProfileRequest;
import sg.edu.nus.wellness.dto.UserProfileResponse;
import sg.edu.nus.wellness.dto.UsernameRequest;
import sg.edu.nus.wellness.model.UserProfile;
import sg.edu.nus.wellness.repository.UserProfileRepo;
import sg.edu.nus.wellness.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserProfileRepo repo;
    private final UserService userService;
    private final RequestUserExtractor userExt;

    public UserProfileController(UserProfileRepo repo, UserService us, RequestUserExtractor u) {
        this.repo = repo;
        this.userService = us;
        this.userExt = u;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> get(HttpServletRequest req) {
        Long userId = userExt.userId(req);
        UserProfile p = repo.findById(userId).orElse(null);
        if (p == null) return ResponseEntity.ok(Map.of("exists", false));
        return ResponseEntity.ok(UserProfileResponse.from(p));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> save(@RequestBody UserProfileRequest body, HttpServletRequest req) {
        try {
            Long userId = userExt.userId(req);
            userService.updateProfile(userId, body);
            UserProfile p = repo.findById(userId).orElse(null);
            return ResponseEntity.ok(UserProfileResponse.from(p));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }

    /** Upload avatar image via multipart form. */
    @PostMapping("/profile/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        Long userId = null;
        try {
            userId = userExt.userId(req);
            String avatarUrl = userService.uploadAvatar(userId, file);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        } catch (Exception e) {
            log.error("Avatar upload failed for userId={}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to upload avatar"));
        }
    }

    /** Get combined User + UserProfile info. */
    @GetMapping("/user")
    public ResponseEntity<?> getUser(HttpServletRequest req) {
        try {
            Long userId = userExt.userId(req);
            return ResponseEntity.ok(userService.getCombinedUser(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
    }

    /** Change username. */
    @PutMapping("/user/username")
    public ResponseEntity<?> changeUsername(@RequestBody UsernameRequest body, HttpServletRequest req) {
        try {
            Long userId = userExt.userId(req);
            userService.changeUsername(userId, body.username);
            return ResponseEntity.ok(Map.of("message", "Username updated", "username", body.username));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("already taken")) {
                return ResponseEntity.status(409).body(Map.of("detail", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("detail", msg));
        }
    }
}

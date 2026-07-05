// Author: Xia Zihang
package sg.edu.nus.wellness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.wellness.dto.UserProfileRequest;
import sg.edu.nus.wellness.dto.UserResponse;
import sg.edu.nus.wellness.model.CharacterSession;
import sg.edu.nus.wellness.model.User;
import sg.edu.nus.wellness.model.UserProfile;
import sg.edu.nus.wellness.repository.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB

    private final UserRepo userRepo;
    private final UserProfileRepo profileRepo;
    private final WellnessService wellnessService;
    private final ChatHistoryRepo chatHistoryRepo;
    private final RecRepo recRepo;
    private final CharacterSessionRepo characterSessionRepo;
    private final CharacterMessageRepo characterMessageRepo;
    private final CharacterUserProfileRepo characterUserProfileRepo;
    private final PasswordEncoder encoder;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public UserService(UserRepo userRepo, UserProfileRepo profileRepo, WellnessService wellnessService,
                       ChatHistoryRepo chatHistoryRepo, RecRepo recRepo,
                       CharacterSessionRepo characterSessionRepo, CharacterMessageRepo characterMessageRepo,
                       CharacterUserProfileRepo characterUserProfileRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.wellnessService = wellnessService;
        this.chatHistoryRepo = chatHistoryRepo;
        this.recRepo = recRepo;
        this.characterSessionRepo = characterSessionRepo;
        this.characterMessageRepo = characterMessageRepo;
        this.characterUserProfileRepo = characterUserProfileRepo;
        this.encoder = encoder;
    }

    private Path resolveAvatarDir() {
        return Paths.get(uploadDir).toAbsolutePath().resolve("avatars");
    }

    /** Change password — verify old password, set new one. Google users blocked. */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if ("GOOGLE".equals(user.getProvider())) {
            throw new IllegalArgumentException("Google accounts do not use password authentication");
        }

        if (!encoder.matches(oldPassword, user.getHashedPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        AuthService.validatePassword(newPassword);

        user.setHashedPassword(encoder.encode(newPassword));
        userRepo.save(user);
    }

    /** Bind or change email. Blocks if email already belongs to another account, or if user is Google-authenticated. */
    public void bindEmail(Long userId, String email) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("GOOGLE".equals(user.getProvider())) {
            throw new IllegalArgumentException("Google accounts cannot change email");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        email = email.trim().toLowerCase();

        User existing = userRepo.findByEmail(email).orElse(null);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new EmailConflictException("Email already registered to another account");
        }

        user.setEmail(email);
        userRepo.save(user);
    }

    /** Change username. Checks uniqueness and updates both User + UserProfile nickname. */
    public void changeUsername(Long userId, String newUsername) {
        if (newUsername == null || newUsername.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        final String trimmed = newUsername.trim();

        User existing = userRepo.findByUsername(trimmed).orElse(null);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setUsername(trimmed);
        userRepo.save(user);

        // Also update profile nickname if profile exists
        profileRepo.findById(userId).ifPresent(p -> {
            p.setNickname(trimmed);
            profileRepo.save(p);
        });
    }

    /** Return combined User + UserProfile info. */
    public UserResponse getCombinedUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserProfile profile = profileRepo.findById(userId).orElse(null);
        return UserResponse.from(user, profile);
    }

    /** Update profile fields (partial update). */
    public void updateProfile(Long userId, UserProfileRequest body) {
        UserProfile p = profileRepo.findById(userId).orElseGet(() -> {
            UserProfile np = new UserProfile();
            np.setUserId(userId);
            return np;
        });
        if (body.avatarUrl != null) p.setAvatarUrl(body.avatarUrl);
        if (body.nickname != null) p.setNickname(body.nickname);
        if (body.heightCm != null) p.setHeightCm(body.heightCm);
        if (body.age != null) p.setAge(body.age);
        if (body.weightKg != null) p.setWeightKg(body.weightKg);
        profileRepo.save(p);
    }

    /** Upload avatar image, save to disk, update UserProfile.avatarUrl. */
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") &&
                !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are allowed");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        // Generate unique filename
        String originalName = file.getOriginalFilename();
        String ext = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String filename = userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        Path avatarDir = null;
        try {
            // Ensure upload directory exists
            avatarDir = resolveAvatarDir();
            Files.createDirectories(avatarDir);

            // Delete old avatar file if exists
            UserProfile profile = profileRepo.findById(userId).orElse(null);
            if (profile != null && profile.getAvatarUrl() != null) {
                String oldUrl = profile.getAvatarUrl();
                try {
                    String oldFilename = oldUrl.substring(oldUrl.lastIndexOf('/') + 1);
                    Path oldFile = avatarDir.resolve(oldFilename);
                    Files.deleteIfExists(oldFile);
                } catch (Exception e) {
                    log.warn("Failed to delete old avatar for user {}: {}", userId, e.getMessage());
                }
            }

            // Save new file
            Path target = avatarDir.resolve(filename);
            file.transferTo(target.toFile());

            // Build public URL
            // Use localhost as fallback — the client already knows the server IP
            String avatarUrl = "/uploads/avatars/" + filename;

            // Update profile
            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
            }
            profile.setAvatarUrl(avatarUrl);
            profileRepo.save(profile);

            log.info("Avatar uploaded for user {}: {}", userId, avatarUrl);
            return avatarUrl;
        } catch (IOException e) {
            log.error("Failed to save avatar file to {}: {}", avatarDir, e.getMessage(), e);
            throw new RuntimeException("Failed to save avatar file", e);
        }
    }

    /** Delete user account and ALL associated data. Destructive — cannot be undone. */
    @Transactional
    public void deleteAccount(Long userId) {
        // 1. Delete avatar file from disk
        UserProfile profile = profileRepo.findById(userId).orElse(null);
        if (profile != null && profile.getAvatarUrl() != null) {
            try {
                String filename = profile.getAvatarUrl().substring(profile.getAvatarUrl().lastIndexOf('/') + 1);
                Path file = resolveAvatarDir().resolve(filename);
                Files.deleteIfExists(file);
            } catch (Exception e) {
                log.warn("Failed to delete avatar for user {}: {}", userId, e.getMessage());
            }
        }

        // 2. Delete character messages (for each session, then sessions themselves)
        try {
            java.util.List<CharacterSession> sessions = characterSessionRepo.findByUserIdOrderByUpdatedAtDesc(userId);
            for (CharacterSession s : sessions) {
                characterMessageRepo.deleteAllBySessionId(s.id);
            }
            characterSessionRepo.deleteAllByUserId(userId);
        } catch (Exception e) {
            log.error("Failed to delete character data for user {}: {}", userId, e.getMessage(), e);
        }

        // 3. Delete wellness records (cascades to sleep_records + exercise_records + RAG)
        try {
            wellnessService.deleteAllByUserId(userId);
        } catch (Exception e) {
            log.error("Failed to delete wellness data for user {}: {}", userId, e.getMessage(), e);
        }

        // 4. Delete chat history
        chatHistoryRepo.deleteAllByUserId(userId);

        // 5. Delete recommendations
        recRepo.deleteAllByUserId(userId);

        // 6. Delete character user profile
        characterUserProfileRepo.findById(userId).ifPresent(p -> characterUserProfileRepo.delete(p));

        // 7. Delete user profile
        profileRepo.deleteById(userId);

        // 8. Delete user entity
        userRepo.deleteById(userId);

        log.info("Account deleted for userId={}", userId);
    }

    /** Custom runtime exception for 409 email conflict. */
    public static class EmailConflictException extends RuntimeException {
        public EmailConflictException(String message) {
            super(message);
        }
    }
}

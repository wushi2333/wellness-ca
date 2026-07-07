// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.dto.AuthResponse;
import sg.edu.nus.wellness.model.User;
import sg.edu.nus.wellness.repository.UserRepo;
import sg.edu.nus.wellness.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepo users; private final JwtTokenProvider jwt; private final PasswordEncoder encoder;
    private final RestTemplate http;

    @org.springframework.beans.factory.annotation.Value("${app.google.client.secret:}")
    private String googleClientSecret;

    public AuthService(UserRepo u, JwtTokenProvider j, PasswordEncoder e, RestTemplate rt) { users=u; jwt=j; encoder=e; http=rt; }

    public Long register(String username, String password, String email) {
        username = username.trim();
        validatePassword(password);
        if (users.findByUsername(username).isPresent()) throw new IllegalArgumentException("Username exists");
        if (email != null && !email.isBlank()) {
            email = email.trim().toLowerCase();
            if (users.findByEmail(email).isPresent()) throw new IllegalArgumentException("Email already registered");
        }
        User u = new User(username, encoder.encode(password));
        if (email != null && !email.isBlank()) u.setEmail(email.trim().toLowerCase());
        return users.save(u).getId();
    }

    /**
     * Validates password strength.
     * Requirements: minimum 8 characters, must contain at least one digit and one letter.
     */
    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        boolean hasDigit = false;
        boolean hasLetter = false;
        for (char c : password.toCharArray()) {
            if (Character.isDigit(c)) hasDigit = true;
            if (Character.isLetter(c)) hasLetter = true;
            if (hasDigit && hasLetter) break;
        }
        if (!hasDigit || !hasLetter) {
            throw new IllegalArgumentException("Password must contain at least one digit and one letter");
        }
    }

    public AuthResponse login(String username, String password) {
        username = username.trim();
        // Support login with either username or email
        User u;
        if (username.contains("@")) {
            u = users.findByEmail(username.toLowerCase()).orElseThrow(()->new IllegalArgumentException("Bad credentials"));
        } else {
            u = users.findByUsername(username).orElseThrow(()->new IllegalArgumentException("Bad credentials"));
        }
        if (!encoder.matches(password, u.getHashedPassword())) throw new IllegalArgumentException("Bad credentials");
        return AuthResponse.of(jwt.createToken(u.getId().toString()), u.getId(), u.getUsername());
    }

    /**
     * Google Sign-In: supports both authCode (mobile) and idToken (web/desktop).
     * If the Google email is already bound to a different user, returns a conflict response.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> googleLogin(String authCode, String idToken, String username, String redirectUri) {
        String email;
        String googleId;
        try {
            // Route 1: authCode → exchange for tokens (Android Chrome Tabs / Web)
            if (authCode != null && !authCode.isBlank()) {
                String clientId = "375016829980-l1gpslqj0cltlc5aqf2f403c52oepeg7.apps.googleusercontent.com";
                String clientSecret = googleClientSecret != null ? googleClientSecret : "";
                String uri = (redirectUri != null && !redirectUri.isBlank())
                    ? redirectUri : "http://localhost/oauth2callback";
                String tokenBody = "code=" + authCode +
                    "&client_id=" + clientId +
                    "&client_secret=" + clientSecret +
                    "&redirect_uri=" + uri +
                    "&grant_type=authorization_code";

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
                org.springframework.http.HttpEntity<String> tokenRequest =
                    new org.springframework.http.HttpEntity<>(tokenBody, headers);

                ResponseEntity<Map> tokenResp = http.postForEntity(
                    "https://oauth2.googleapis.com/token", tokenRequest, Map.class);
                if (tokenResp.getBody() == null || tokenResp.getBody().containsKey("error")) {
                    log.warn("Google token exchange failed: {}", tokenResp.getBody());
                    throw new IllegalArgumentException("Google authentication failed");
                }
                idToken = (String) tokenResp.getBody().get("id_token");
                if (idToken == null) throw new IllegalArgumentException("No id_token in exchange response");
            }

            // Route 2: verify idToken directly
            if (idToken == null || idToken.isBlank()) {
                throw new IllegalArgumentException("No authCode or idToken provided (idToken is null or blank)");
            }
            log.info("Google login — idToken prefix: {}..., length: {}", idToken.substring(0, Math.min(20, idToken.length())), idToken.length());

            // Try v3 endpoint first (more lenient), fall back to v1
            String tokenInfoUrl = "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=" + idToken;
            ResponseEntity<Map> resp = http.getForEntity(tokenInfoUrl, Map.class);
            if (resp.getBody() == null || resp.getBody().containsKey("error")) {
                log.warn("v3 tokeninfo failed: {} — trying v1", resp.getBody());
                resp = http.getForEntity(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken, Map.class);
            }
            if (resp.getBody() == null || resp.getBody().containsKey("error")) {
                log.warn("Both tokeninfo endpoints failed. v3={}", resp.getBody());
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            email = (String) resp.getBody().get("email");
            googleId = (String) resp.getBody().get("sub");
            if (email == null) throw new IllegalArgumentException("Google account has no email");
        } catch (IllegalArgumentException e) { throw e;
        } catch (Exception e) {
            log.warn("Google authentication failed: {}", e.getMessage());
            throw new IllegalArgumentException("Google authentication failed");
        }

        // Check: email already bound to existing account?
        User existingByEmail = users.findByEmail(email.toLowerCase()).orElse(null);
        if (existingByEmail != null) {
            if (username.isEmpty() || !existingByEmail.getUsername().equals(username)) {
                return Map.of("conflict", true,
                    "existingUsername", existingByEmail.getUsername(),
                    "email", email,
                    "idToken", idToken);
            }
            // Same person: just log them in without changing provider
            return Map.of("conflict", false,
                "accessToken", jwt.createToken(existingByEmail.getId().toString()),
                "tokenType", "bearer",
                "userId", existingByEmail.getId(),
                "username", existingByEmail.getUsername(),
                "email", email);
        }

        // Check: Google ID already registered?
        User existingByGoogle = users.findByProviderAndProviderId("GOOGLE", googleId).orElse(null);
        if (existingByGoogle != null) {
            // Existing Google user: just log them in
            return Map.of("conflict", false,
                "accessToken", jwt.createToken(existingByGoogle.getId().toString()),
                "tokenType", "bearer",
                "userId", existingByGoogle.getId(),
                "username", existingByGoogle.getUsername(),
                "email", email);
        }

        // New Google user — generate suggested username from email
        String suggested = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "_");
        if (suggested.length() < 3) suggested = "user_" + suggested;
        // Ensure suggested username is unique
        String base = suggested;
        int suffix = 1;
        while (users.findByUsername(suggested).isPresent()) {
            suggested = base + suffix;
            suffix++;
        }

        // If client provided a username, this is a confirmation — create the account
        username = username.trim();
        if (!username.isEmpty() && username.length() >= 3) {
            if (users.findByUsername(username).isPresent()) {
                // Username already taken — regenerate suggestion and ask again
                String alt = base;
                int s2 = 1;
                while (users.findByUsername(alt).isPresent()) {
                    alt = base + s2;
                    s2++;
                }
                return Map.of("newUser", true,
                    "email", email,
                    "suggestedUsername", alt,
                    "error", "username_taken",
                    "idToken", idToken);
            }
            User u = new User(username, "");
            u.setEmail(email.toLowerCase());
            u.setProvider("GOOGLE");
            u.setProviderId(googleId);
            u = users.save(u);
            return Map.of("conflict", false,
                "accessToken", jwt.createToken(u.getId().toString()),
                "tokenType", "bearer",
                "userId", u.getId(),
                "username", u.getUsername(),
                "email", email);
        }

        // New user not yet confirmed — return hint so client can show confirmation dialog
        return Map.of("newUser", true,
            "email", email,
            "suggestedUsername", suggested,
            "idToken", idToken);
    }
}

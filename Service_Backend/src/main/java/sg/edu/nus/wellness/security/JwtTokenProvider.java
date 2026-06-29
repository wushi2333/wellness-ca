package sg.edu.nus.wellness.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.expiration}") long expirationMin) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMin * 60_000;
    }

    public String createToken(String userId) {
        return Jwts.builder().subject(userId)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key).compact();
    }

    public String validateAndGetUserId(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }
}

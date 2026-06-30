// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.dto.AuthResponse;
import sg.edu.nus.wellness.model.User;
import sg.edu.nus.wellness.repository.UserRepo;
import sg.edu.nus.wellness.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepo users; private final JwtTokenProvider jwt; private final PasswordEncoder encoder;
    public AuthService(UserRepo u, JwtTokenProvider j, PasswordEncoder e) { users=u; jwt=j; encoder=e; }

    public Long register(String username, String password) {
        username = username.trim();
        if (users.findByUsername(username).isPresent()) throw new RuntimeException("Username exists");
        return users.save(new User(username, encoder.encode(password))).getId();
    }

    public AuthResponse login(String username, String password) {
        username = username.trim();
        User u = users.findByUsername(username).orElseThrow(()->new RuntimeException("Bad credentials"));
        if (!encoder.matches(password, u.getHashedPassword())) throw new RuntimeException("Bad credentials");
        return AuthResponse.of(jwt.createToken(u.getId().toString()), u.getId(), u.getUsername());
    }
}

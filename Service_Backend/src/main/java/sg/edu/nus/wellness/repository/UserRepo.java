// Author: Xia Zihang
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepo extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String u);
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}

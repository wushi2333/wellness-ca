// Author: Xia Zihang
package sg.edu.nus.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.wellness.model.CharacterSession;
import java.util.List;
import java.util.Optional;

public interface CharacterSessionRepo extends JpaRepository<CharacterSession, Long> {
    List<CharacterSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<CharacterSession> findByIdAndUserId(Long id, Long userId);
    void deleteAllByUserId(Long userId);
}

// Author: Xia Zihang
package sg.edu.nus.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.wellness.model.CharacterMessage;
import java.util.List;

public interface CharacterMessageRepo extends JpaRepository<CharacterMessage, Long> {
    List<CharacterMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    int countBySessionId(Long sessionId);
    List<CharacterMessage> findTop20BySessionIdAndIsCompressedFalseOrderByCreatedAtAsc(Long sessionId);
}

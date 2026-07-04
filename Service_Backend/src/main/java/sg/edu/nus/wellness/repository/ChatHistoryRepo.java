// Author: Xia Zihang
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ChatHistoryRepo extends JpaRepository<ChatHistory,Long> {
    List<ChatHistory> findByUserId(Long userId);
    @Transactional void deleteAllByUserId(Long userId);
}

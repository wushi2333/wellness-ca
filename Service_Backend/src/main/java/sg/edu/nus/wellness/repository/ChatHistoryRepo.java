// Author: Xia Zihang
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChatHistoryRepo extends JpaRepository<ChatHistory,Long> {}

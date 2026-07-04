// Author: Xia Zihang, Cai Peilin
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RecRepo extends JpaRepository<Recommendation,Long> {
    List<Recommendation> findTop10ByUserIdOrderByCreatedAtDesc(Long uid);
    void deleteAllByUserId(Long userId);
}

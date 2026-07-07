// Author: Xia Zihang, Cai Peilin
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface RecRepo extends JpaRepository<Recommendation,Long> {
    List<Recommendation> findTop10ByUserIdOrderByCreatedAtDesc(Long uid);
    // Full history (newest first) so the desktop client can paginate through everything.
    List<Recommendation> findByUserIdOrderByCreatedAtDesc(Long uid);
    Optional<Recommendation> findByIdAndUserId(Long id, Long userId);
    void deleteAllByUserId(Long userId);
}

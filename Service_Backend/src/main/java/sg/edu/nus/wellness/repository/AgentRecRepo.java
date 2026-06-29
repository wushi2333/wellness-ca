// Author: Cai Peilin
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.AgentRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AgentRecRepo extends JpaRepository<AgentRecommendation,Long> {
    List<AgentRecommendation> findTop10ByUserIdOrderByCreatedAtDesc(Long uid);
}

// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.WellnessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WellnessRepo extends JpaRepository<WellnessRecord,Long> {
    List<WellnessRecord> findByUserIdOrderByRecordDateDesc(Long uid);
    List<WellnessRecord> findTop7ByUserIdOrderByRecordDateDesc(Long uid);
    Optional<WellnessRecord> findByIdAndUserId(Long id, Long uid);
}

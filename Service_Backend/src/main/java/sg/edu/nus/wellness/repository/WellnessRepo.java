package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.WellnessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WellnessRepo extends JpaRepository<WellnessRecord,Long> {
    List<WellnessRecord> findByUserIdOrderByRecordDateDesc(Long uid);
    List<WellnessRecord> findTop7ByUserIdOrderByRecordDateDesc(Long uid);
}

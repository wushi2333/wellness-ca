// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.repository;
import sg.edu.nus.wellness.model.WellnessRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WellnessRepo extends JpaRepository<WellnessRecord,Long> {
    List<WellnessRecord> findByUserIdOrderByRecordDateDesc(Long uid);
    Page<WellnessRecord> findByUserIdOrderByRecordDateDesc(Long uid, Pageable pageable);
    List<WellnessRecord> findTop7ByUserIdOrderByRecordDateDesc(Long uid);
    Optional<WellnessRecord> findByIdAndUserId(Long id, Long uid);
    Optional<WellnessRecord> findFirstByUserIdAndSleepRecordId(Long userId, Long sleepRecordId);
    Optional<WellnessRecord> findFirstByUserIdAndRecordDateOrderByIdAsc(Long userId, LocalDate recordDate);
    void deleteAllByUserId(Long userId);
}

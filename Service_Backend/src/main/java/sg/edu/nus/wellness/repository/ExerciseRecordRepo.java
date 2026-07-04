// Author: Xia Zihang
package sg.edu.nus.wellness.repository;

import sg.edu.nus.wellness.model.ExerciseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExerciseRecordRepo extends JpaRepository<ExerciseRecord, Long> {
    List<ExerciseRecord> findByDailyRecordId(Long dailyRecordId);
    List<ExerciseRecord> findByDailyRecordIdIn(List<Long> dailyRecordIds);
    void deleteAllByDailyRecordId(Long dailyRecordId);
}

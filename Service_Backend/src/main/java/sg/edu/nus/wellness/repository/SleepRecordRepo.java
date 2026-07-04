// Author: Xia Zihang
package sg.edu.nus.wellness.repository;

import sg.edu.nus.wellness.model.SleepRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepRecordRepo extends JpaRepository<SleepRecord, Long> {
}

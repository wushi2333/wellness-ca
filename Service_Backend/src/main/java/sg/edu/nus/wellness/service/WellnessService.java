package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.dto.WellnessRequest;
import sg.edu.nus.wellness.model.WellnessRecord;
import sg.edu.nus.wellness.repository.WellnessRepo;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class WellnessService {
    private final WellnessRepo repo;
    public WellnessService(WellnessRepo r) { repo=r; }

    public List<WellnessRecord> list(Long userId) { return repo.findByUserIdOrderByRecordDateDesc(userId); }

    public Long create(Long userId, WellnessRequest req) {
        return repo.save(toEntity(userId, req)).getId();
    }

    public void update(Long userId, Long id, WellnessRequest req) {
        WellnessRecord r = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        if (!r.getUserId().equals(userId)) throw new RuntimeException("Not found");
        apply(r, req); repo.save(r);
    }

    public void delete(Long userId, Long id) {
        WellnessRecord r = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        if (!r.getUserId().equals(userId)) throw new RuntimeException("Not found");
        repo.delete(r);
    }

    public List<WellnessRecord> last7(Long userId) { return repo.findTop7ByUserIdOrderByRecordDateDesc(userId); }

    private WellnessRecord toEntity(Long userId, WellnessRequest req) {
        WellnessRecord r = new WellnessRecord();
        r.setUserId(userId); apply(r, req); return r;
    }

    private void apply(WellnessRecord r, WellnessRequest req) {
        r.setSleepHours(req.sleepHours); r.setExerciseActivity(req.exerciseActivity);
        r.setExerciseDuration(req.exerciseDuration); r.setRecordDate(LocalDate.parse(req.recordDate));
        r.setNotes(req.notes);
    }
}

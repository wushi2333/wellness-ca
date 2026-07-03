// Author: Huang Qianer, Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.dto.WellnessRequest;
import sg.edu.nus.wellness.dto.WellnessResponse;
import sg.edu.nus.wellness.exception.NotFoundException;
import sg.edu.nus.wellness.model.WellnessRecord;
import sg.edu.nus.wellness.repository.WellnessRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.util.*;

@Service
public class WellnessService {
    private final WellnessRepo repo;
    private final RestTemplate http = new RestTemplate();
    private final String ragUrl;

    public WellnessService(WellnessRepo r, @Value("${app.rag.url:http://localhost:8001}") String rag) {
        repo=r; ragUrl=rag;
    }

    public List<WellnessResponse> list(Long userId) {
        return repo.findByUserIdOrderByRecordDateDesc(userId)
                .stream()
                .map(WellnessResponse::from)
                .toList();
    }

    public Long create(Long userId, WellnessRequest req) {
        Long id = repo.save(toEntity(userId, req)).getId();
        syncRag(id, userId, req);
        return id;
    }

    public void update(Long userId, Long id, WellnessRequest req) {
        WellnessRecord r = findOwnedRecord(userId, id);
        apply(r, req); repo.save(r);
        syncRag(id, userId, req);
    }

    public void delete(Long userId, Long id) {
        WellnessRecord r = findOwnedRecord(userId, id);
        repo.delete(r);
        try { http.delete(ragUrl+"/sync/"+id); } catch (Exception ignored) {}
    }

    public List<WellnessRecord> last7(Long userId) { return repo.findTop7ByUserIdOrderByRecordDateDesc(userId); }

    private WellnessRecord findOwnedRecord(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Record not found"));
    }

    private WellnessRecord toEntity(Long userId, WellnessRequest req) {
        WellnessRecord r = new WellnessRecord();
        r.setUserId(userId); apply(r, req); return r;
    }

    private void apply(WellnessRecord r, WellnessRequest req) {
        r.setSleepHours(req.sleepHours); r.setExerciseActivity(req.exerciseActivity);
        r.setExerciseDuration(req.exerciseDuration); r.setMoodScore(req.moodScore);
        r.setRecordDate(LocalDate.parse(req.recordDate));
        r.setNotes(req.notes);
    }

    private void syncRag(Long recordId, Long userId, WellnessRequest req) {
        try {
            Map<String,Object> sync = new HashMap<>();
            sync.put("record_id", recordId.intValue());
            sync.put("user_id", userId.intValue());
            sync.put("sleep_hours", req.sleepHours);
            sync.put("exercise_activity", req.exerciseActivity);
            sync.put("exercise_duration", req.exerciseDuration);
            sync.put("mood_score", req.moodScore);
            sync.put("record_date", req.recordDate);
            sync.put("notes", req.notes);
            http.postForEntity(ragUrl+"/sync", sync, String.class);
        } catch (Exception ignored) {}
    }
}

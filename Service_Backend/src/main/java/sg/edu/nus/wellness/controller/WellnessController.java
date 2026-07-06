// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.controller;

import sg.edu.nus.wellness.dto.*;
import sg.edu.nus.wellness.service.WellnessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class WellnessController {

    private final WellnessService ws;
    private final RequestUserExtractor userExt;

    public WellnessController(WellnessService w, RequestUserExtractor u) {
        ws = w; userExt = u;
    }

    // ── daily aggregated query ──────────────────────────────────────────

    @GetMapping("/records")
    public ResponseEntity<?> listDaily(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       HttpServletRequest req) {
        PagedResponse<DailyWellnessResponse> result = ws.listDailyPaged(userExt.userId(req), page, size);
        return ResponseEntity.ok(result);
    }

    /** Backward-compat: old POST /records delegates to new split endpoints. */
    @PostMapping("/records")
    public ResponseEntity<?> createCompat(@RequestBody Map<String, Object> body,
                                          HttpServletRequest req) {
        Long userId = userExt.userId(req);
        double sleepHours = body.containsKey("sleepHours") ? ((Number) body.get("sleepHours")).doubleValue() : 0.0;
        int exDuration = body.containsKey("exerciseDuration") ? ((Number) body.get("exerciseDuration")).intValue() : 0;
        String recordDate = (String) body.getOrDefault("recordDate", java.time.LocalDate.now().toString());

        if (sleepHours > 0) {
            SleepRecordRequest sr = new SleepRecordRequest();
            sr.sleepHours = sleepHours;
            sr.sleepTime = (String) body.getOrDefault("sleepTime", "");
            sr.wakeTime = (String) body.getOrDefault("wakeTime", "");
            sr.moodScore = body.containsKey("moodScore") ? ((Number) body.get("moodScore")).intValue() : 0;
            sr.recordDate = recordDate;
            sr.notes = (String) body.getOrDefault("notes", "");
            Long id = ws.createSleep(userId, sr);
            return ResponseEntity.status(201).body(Map.of("message", "Created", "id", id));
        }
        if (exDuration > 0) {
            ExerciseRecordRequest er = new ExerciseRecordRequest();
            er.exerciseActivity = (String) body.getOrDefault("exerciseActivity", "");
            er.exerciseDuration = exDuration;
            er.recordDate = recordDate;
            er.notes = (String) body.getOrDefault("notes", "");
            Long id = ws.createExercise(userId, er);
            return ResponseEntity.status(201).body(Map.of("message", "Created", "id", id));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "No sleep or exercise data"));
    }

    /** Backward-compat: old DELETE /records/{id} deletes the daily record. */
    @DeleteMapping("/records/{id}")
    public ResponseEntity<?> deleteCompat(@PathVariable Long id, HttpServletRequest req) {
        ws.deleteCompat(userExt.userId(req), id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    /** Backward-compat: old PUT /records/{id} updates daily, sleep, or exercise records. */
    @PutMapping("/records/{id}")
    public ResponseEntity<?> updateCompat(@PathVariable Long id,
                                          @Valid @RequestBody WellnessRequest r,
                                          HttpServletRequest req) {
        ws.updateCompat(userExt.userId(req), id, r);
        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    // ── sleep CRUD ──────────────────────────────────────────────────────

    @PostMapping("/sleep-records")
    public ResponseEntity<?> createSleep(@Valid @RequestBody SleepRecordRequest r,
                                          HttpServletRequest req) {
        Long id = ws.createSleep(userExt.userId(req), r);
        return ResponseEntity.status(201).body(Map.of("message", "Created", "id", id));
    }

    @PutMapping("/sleep-records/{id}")
    public ResponseEntity<?> updateSleep(@PathVariable Long id,
                                          @Valid @RequestBody SleepRecordRequest r,
                                          HttpServletRequest req) {
        ws.updateSleep(userExt.userId(req), id, r);
        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    @DeleteMapping("/sleep-records/{id}")
    public ResponseEntity<?> deleteSleep(@PathVariable Long id,
                                          HttpServletRequest req) {
        ws.deleteSleep(userExt.userId(req), id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // ── exercise CRUD ───────────────────────────────────────────────────

    @PostMapping("/exercise-records")
    public ResponseEntity<?> createExercise(@Valid @RequestBody ExerciseRecordRequest r,
                                             HttpServletRequest req) {
        Long id = ws.createExercise(userExt.userId(req), r);
        return ResponseEntity.status(201).body(Map.of("message", "Created", "id", id));
    }

    @PutMapping("/exercise-records/{id}")
    public ResponseEntity<?> updateExercise(@PathVariable Long id,
                                             @Valid @RequestBody ExerciseRecordRequest r,
                                             HttpServletRequest req) {
        ws.updateExercise(userExt.userId(req), id, r);
        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    @DeleteMapping("/exercise-records/{id}")
    public ResponseEntity<?> deleteExercise(@PathVariable Long id,
                                             HttpServletRequest req) {
        ws.deleteExercise(userExt.userId(req), id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}

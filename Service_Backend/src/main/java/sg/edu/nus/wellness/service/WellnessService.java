// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.service;

import sg.edu.nus.wellness.dto.*;
import sg.edu.nus.wellness.exception.NotFoundException;
import sg.edu.nus.wellness.model.*;
import sg.edu.nus.wellness.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
public class WellnessService {
    private static final Logger log = LoggerFactory.getLogger(WellnessService.class);
    private final WellnessRepo dailyRepo;
    private final SleepRecordRepo sleepRepo;
    private final ExerciseRecordRepo exerciseRepo;
    private final RagClient ragClient;

    public WellnessService(WellnessRepo dr, SleepRecordRepo sr, ExerciseRecordRepo er,
                           RagClient rc) {
        dailyRepo = dr; sleepRepo = sr; exerciseRepo = er;
        ragClient = rc;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private WellnessRecord getOrCreateDaily(Long userId, LocalDate date) {
        return dailyRepo.findFirstByUserIdAndRecordDateOrderByIdAsc(userId, date)
                .orElseGet(() -> {
                    WellnessRecord r = new WellnessRecord();
                    r.setUserId(userId);
                    r.setRecordDate(date);
                    return dailyRepo.save(r);
                });
    }

    // ── sleep CRUD ──────────────────────────────────────────────────────

    @CacheEvict(value = "records", key = "#userId")
    public Long createSleep(Long userId, SleepRecordRequest req) {
        LocalDate date = LocalDate.parse(req.recordDate);
        WellnessRecord daily = getOrCreateDaily(userId, date);

        if (daily.getSleepRecordId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Sleep data already exists for " + req.recordDate + ". Use PUT to edit.");
        }

        SleepRecord s = new SleepRecord();
        applySleep(s, req);
        s = sleepRepo.save(s);

        daily.setSleepRecordId(s.getId());
        dailyRepo.save(daily);

        ragClient.syncSleep(s.getId(), userId, s.getSleepHours(),
                s.getSleepTime(), s.getWakeTime(),
                s.getMoodScore() != null ? s.getMoodScore() : 0,
                date.toString(), s.getNotes());
        log.info("Sleep created id={} for user={} date={}", s.getId(), userId, date);
        return s.getId();
    }

    @CacheEvict(value = "records", key = "#userId")
    public void updateSleep(Long userId, Long sleepId, SleepRecordRequest req) {
        WellnessRecord daily = findOwnedDailyBySleepId(userId, sleepId);
        SleepRecord s = sleepRepo.findById(sleepId)
                .orElseThrow(() -> new NotFoundException("Sleep record not found"));
        applySleep(s, req);
        sleepRepo.save(s);
        ragClient.syncSleep(s.getId(), userId, s.getSleepHours(),
                s.getSleepTime(), s.getWakeTime(),
                s.getMoodScore() != null ? s.getMoodScore() : 0,
                daily.getRecordDate().toString(), s.getNotes());
        log.info("Sleep updated id={} for user={}", sleepId, userId);
    }

    @CacheEvict(value = "records", key = "#userId")
    public void deleteSleep(Long userId, Long sleepId) {
        WellnessRecord daily = findOwnedDailyBySleepId(userId, sleepId);
        daily.setSleepRecordId(null);
        dailyRepo.save(daily);
        sleepRepo.deleteById(sleepId);
        ragClient.deleteSleep(sleepId);
        log.info("Sleep deleted id={} for user={}", sleepId, userId);
    }

    private void applySleep(SleepRecord s, SleepRecordRequest req) {
        s.setSleepHours(req.sleepHours);
        if (req.sleepTime != null && !req.sleepTime.isEmpty()) s.setSleepTime(req.sleepTime);
        if (req.wakeTime != null && !req.wakeTime.isEmpty()) s.setWakeTime(req.wakeTime);
        if (req.moodScore != null && req.moodScore > 0) s.setMoodScore(req.moodScore);
        if (req.notes != null && !req.notes.isEmpty()) s.setNotes(req.notes);
    }

    // ── exercise CRUD ───────────────────────────────────────────────────

    @CacheEvict(value = "records", key = "#userId")
    public Long createExercise(Long userId, ExerciseRecordRequest req) {
        LocalDate date = LocalDate.parse(req.recordDate);
        WellnessRecord daily = getOrCreateDaily(userId, date);

        ExerciseRecord e = new ExerciseRecord();
        e.setDailyRecordId(daily.getId());
        applyExercise(e, req);
        e = exerciseRepo.save(e);

        ragClient.syncExercise(e.getId(), userId, e.getExerciseActivity(),
                e.getExerciseDuration(), date.toString(), e.getNotes());
        log.info("Exercise created id={} for user={} date={}", e.getId(), userId, date);
        return e.getId();
    }

    @CacheEvict(value = "records", key = "#userId")
    public void updateExercise(Long userId, Long exerciseId, ExerciseRecordRequest req) {
        ExerciseRecord e = findOwnedExercise(userId, exerciseId);
        applyExercise(e, req);
        exerciseRepo.save(e);
        ragClient.syncExercise(e.getId(), userId, e.getExerciseActivity(),
                e.getExerciseDuration(), "", e.getNotes());
        log.info("Exercise updated id={} for user={}", exerciseId, userId);
    }

    @CacheEvict(value = "records", key = "#userId")
    public void deleteExercise(Long userId, Long exerciseId) {
        findOwnedExercise(userId, exerciseId);
        exerciseRepo.deleteById(exerciseId);
        ragClient.deleteExercise(exerciseId);
        log.info("Exercise deleted id={} for user={}", exerciseId, userId);
    }

    private void applyExercise(ExerciseRecord e, ExerciseRecordRequest req) {
        e.setExerciseActivity(req.exerciseActivity);
        e.setExerciseDuration(req.exerciseDuration);
        if (req.notes != null && !req.notes.isEmpty()) e.setNotes(req.notes);
    }

    private WellnessRecord findOwnedDaily(Long userId, Long dailyRecordId) {
        return dailyRepo.findByIdAndUserId(dailyRecordId, userId)
                .orElseThrow(() -> new NotFoundException("Record not found"));
    }

    private WellnessRecord findOwnedDailyBySleepId(Long userId, Long sleepId) {
        return dailyRepo.findFirstByUserIdAndSleepRecordId(userId, sleepId)
                .orElseThrow(() -> new NotFoundException("Sleep record not found"));
    }

    private ExerciseRecord findOwnedExercise(Long userId, Long exerciseId) {
        ExerciseRecord exercise = exerciseRepo.findById(exerciseId)
                .orElseThrow(() -> new NotFoundException("Exercise record not found"));
        findOwnedDaily(userId, exercise.getDailyRecordId());
        return exercise;
    }

    // ── aggregated daily query ─────────────────────────────────────────

    @Cacheable(value = "records", key = "#userId")
    public List<DailyWellnessResponse> listDaily(Long userId) {
        List<WellnessRecord> dailies = dailyRepo.findByUserIdOrderByRecordDateDesc(userId);
        return buildDailyResponses(dailies);
    }

    public PagedResponse<DailyWellnessResponse> listDailyPaged(Long userId, int page, int size) {
        Page<WellnessRecord> p = dailyRepo.findByUserIdOrderByRecordDateDesc(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordDate")));
        List<DailyWellnessResponse> content = buildDailyResponses(p.getContent());
        return new PagedResponse<>(content, p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages(), p.isLast());
    }

    private List<DailyWellnessResponse> buildDailyResponses(List<WellnessRecord> dailies) {
        // Batch-load exercises for all daily records
        List<Long> dailyIds = dailies.stream().map(WellnessRecord::getId).toList();
        Map<Long, List<ExerciseRecord>> exMap = new HashMap<>();
        if (!dailyIds.isEmpty()) {
            List<ExerciseRecord> allEx = exerciseRepo.findByDailyRecordIdIn(dailyIds);
            for (ExerciseRecord e : allEx) {
                exMap.computeIfAbsent(e.getDailyRecordId(), k -> new ArrayList<>()).add(e);
            }
        }

        // Collect sleep IDs to batch-load
        List<Long> sleepIds = dailies.stream()
                .map(WellnessRecord::getSleepRecordId).filter(Objects::nonNull).toList();
        Map<Long, SleepRecord> sleepMap = new HashMap<>();
        if (!sleepIds.isEmpty()) {
            sleepRepo.findAllById(sleepIds).forEach(s -> sleepMap.put(s.getId(), s));
        }

        return dailies.stream().map(d -> {
            DailyWellnessResponse.SleepSummary sleep = null;
            if (d.getSleepRecordId() != null) {
                SleepRecord s = sleepMap.get(d.getSleepRecordId());
                if (s != null) {
                    sleep = new DailyWellnessResponse.SleepSummary(
                            s.getId(), s.getSleepHours(), s.getSleepTime(),
                            s.getWakeTime(), s.getMoodScore(), s.getNotes());
                }
            }
            List<DailyWellnessResponse.ExerciseSummary> exercises =
                    exMap.getOrDefault(d.getId(), List.of()).stream()
                            .map(e -> new DailyWellnessResponse.ExerciseSummary(
                                    e.getId(), e.getExerciseActivity(),
                                    e.getExerciseDuration(), e.getNotes()))
                            .toList();
            return new DailyWellnessResponse(d.getId(),
                    d.getRecordDate().toString(), sleep, exercises);
        }).toList();
    }

    // ── backward-compat for WebWellnessController ───────────────────────

    /** Old-style flat list → converts new model back to WellnessResponse. */
    public List<WellnessResponse> list(Long userId) {
        return listDaily(userId).stream().map(d -> {
            WellnessResponse r = new WellnessResponse();
            r.id = d.dailyRecordId;
            r.recordDate = LocalDate.parse(d.recordDate);
            if (d.sleep != null) {
                r.sleepHours = d.sleep.sleepHours;
                r.sleepTime = d.sleep.sleepTime;
                r.wakeTime = d.sleep.wakeTime;
                r.moodScore = d.sleep.moodScore;
                r.notes = d.sleep.notes;
            }
            if (d.exercises != null && !d.exercises.isEmpty()) {
                r.exerciseDuration = d.exercises.stream().mapToInt(e -> e.exerciseDuration).sum();
                r.exerciseActivity = d.exercises.stream()
                        .map(e -> e.exerciseActivity + " " + e.exerciseDuration + "min")
                        .reduce((a, b) -> a + ", " + b).orElse("");
            }
            return r;
        }).toList();
    }

    /** Old-style create — delegates based on request content. */
    public Long create(Long userId, WellnessRequest req) {
        if (req.sleepHours != null && req.sleepHours > 0) {
            SleepRecordRequest sr = new SleepRecordRequest();
            sr.sleepHours = req.sleepHours;
            sr.sleepTime = req.sleepTime;
            sr.wakeTime = req.wakeTime;
            sr.moodScore = req.moodScore;
            sr.recordDate = req.recordDate;
            sr.notes = req.notes;
            return createSleep(userId, sr);
        }
        if (req.exerciseDuration != null && req.exerciseDuration > 0) {
            ExerciseRecordRequest er = new ExerciseRecordRequest();
            er.exerciseActivity = req.exerciseActivity;
            er.exerciseDuration = req.exerciseDuration;
            er.recordDate = req.recordDate;
            er.notes = req.notes;
            return createExercise(userId, er);
        }
        // Empty record — just create a daily entry
        return getOrCreateDaily(userId, LocalDate.parse(req.recordDate)).getId();
    }

    /** Old-style update by daily record ID. */
    public void update(Long userId, Long id, WellnessRequest req) {
        WellnessRecord d = findOwnedDaily(userId, id);
        if (req.sleepHours != null && req.sleepHours > 0 && d.getSleepRecordId() != null) {
            SleepRecordRequest sr = new SleepRecordRequest();
            sr.sleepHours = req.sleepHours;
            sr.sleepTime = req.sleepTime;
            sr.wakeTime = req.wakeTime;
            sr.moodScore = req.moodScore;
            sr.recordDate = req.recordDate;
            sr.notes = req.notes;
            updateSleep(userId, d.getSleepRecordId(), sr);
        }
        if (req.exerciseDuration != null && req.exerciseDuration > 0) {
            ExerciseRecordRequest er = new ExerciseRecordRequest();
            er.exerciseActivity = req.exerciseActivity;
            er.exerciseDuration = req.exerciseDuration;
            er.recordDate = req.recordDate;
            er.notes = req.notes;
            // Legacy: treat as replacing all exercise data for the day
            exerciseRepo.deleteAllByDailyRecordId(id);
            createExercise(userId, er);
        }
    }

    /** Old-style delete — removes the entire daily record and linked data. */
    @CacheEvict(value = "records", key = "#userId")
    public void delete(Long userId, Long id) {
        WellnessRecord d = findOwnedDaily(userId, id);
        if (d.getSleepRecordId() != null) sleepRepo.deleteById(d.getSleepRecordId());
        exerciseRepo.deleteAllByDailyRecordId(d.getId());
        dailyRepo.delete(d);
    }

    // ── account cleanup ─────────────────────────────────────────────────

    @CacheEvict(value = "records", key = "#userId")
    public void deleteAllByUserId(Long userId) {
        List<WellnessRecord> dailies = dailyRepo.findByUserIdOrderByRecordDateDesc(userId);
        for (WellnessRecord d : dailies) {
            if (d.getSleepRecordId() != null) {
                ragClient.deleteSleep(d.getSleepRecordId());
                sleepRepo.deleteById(d.getSleepRecordId());
            }
            List<ExerciseRecord> exList = exerciseRepo.findByDailyRecordId(d.getId());
            for (ExerciseRecord ex : exList) {
                ragClient.deleteExercise(ex.getId());
            }
            exerciseRepo.deleteAllByDailyRecordId(d.getId());
        }
        dailyRepo.deleteAllByUserId(userId);
        log.info("All wellness data deleted for user={}", userId);
    }
}

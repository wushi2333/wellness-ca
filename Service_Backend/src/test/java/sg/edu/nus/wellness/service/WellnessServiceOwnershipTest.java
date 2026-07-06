// Author: Yutong Luo
package sg.edu.nus.wellness.service;

import org.junit.jupiter.api.Test;
import sg.edu.nus.wellness.dto.SleepRecordRequest;
import sg.edu.nus.wellness.dto.WellnessRequest;
import sg.edu.nus.wellness.exception.NotFoundException;
import sg.edu.nus.wellness.model.ExerciseRecord;
import sg.edu.nus.wellness.model.SleepRecord;
import sg.edu.nus.wellness.model.WellnessRecord;
import sg.edu.nus.wellness.repository.ExerciseRecordRepo;
import sg.edu.nus.wellness.repository.SleepRecordRepo;
import sg.edu.nus.wellness.repository.WellnessRepo;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WellnessServiceOwnershipTest {

    @Test
    void updateSleepRejectsRecordOwnedByAnotherUser() {
        WellnessRepo dailyRepo = mock(WellnessRepo.class);
        SleepRecordRepo sleepRepo = mock(SleepRecordRepo.class);
        ExerciseRecordRepo exerciseRepo = mock(ExerciseRecordRepo.class);
        RagClient ragClient = mock(RagClient.class);
        WellnessService service = new WellnessService(dailyRepo, sleepRepo, exerciseRepo, ragClient);

        Long userB = 2L;
        Long userASleepId = 10L;
        when(dailyRepo.findFirstByUserIdAndSleepRecordId(userB, userASleepId))
                .thenReturn(Optional.empty());

        SleepRecordRequest req = new SleepRecordRequest();
        req.sleepHours = 8.0;
        req.recordDate = "2026-07-06";

        assertThrows(NotFoundException.class, () -> service.updateSleep(userB, userASleepId, req));

        verify(sleepRepo, never()).save(any());
        verifyNoInteractions(ragClient);
    }

    @Test
    void deleteExerciseRejectsRecordOwnedByAnotherUser() {
        WellnessRepo dailyRepo = mock(WellnessRepo.class);
        SleepRecordRepo sleepRepo = mock(SleepRecordRepo.class);
        ExerciseRecordRepo exerciseRepo = mock(ExerciseRecordRepo.class);
        RagClient ragClient = mock(RagClient.class);
        WellnessService service = new WellnessService(dailyRepo, sleepRepo, exerciseRepo, ragClient);

        Long userB = 2L;
        Long userAExerciseId = 20L;
        Long userADailyRecordId = 100L;
        ExerciseRecord exercise = new ExerciseRecord();
        exercise.setId(userAExerciseId);
        exercise.setDailyRecordId(userADailyRecordId);

        when(exerciseRepo.findById(userAExerciseId)).thenReturn(Optional.of(exercise));
        when(dailyRepo.findByIdAndUserId(userADailyRecordId, userB)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteExercise(userB, userAExerciseId));

        verify(exerciseRepo, never()).deleteById(anyLong());
        verifyNoInteractions(ragClient);
    }

    @Test
    void updateCompatSupportsOldSleepRecordIdEndpoint() {
        WellnessRepo dailyRepo = mock(WellnessRepo.class);
        SleepRecordRepo sleepRepo = mock(SleepRecordRepo.class);
        ExerciseRecordRepo exerciseRepo = mock(ExerciseRecordRepo.class);
        RagClient ragClient = mock(RagClient.class);
        WellnessService service = new WellnessService(dailyRepo, sleepRepo, exerciseRepo, ragClient);

        Long userId = 32L;
        Long sleepId = 3L;
        WellnessRecord daily = new WellnessRecord();
        daily.setId(59L);
        daily.setUserId(userId);
        daily.setRecordDate(LocalDate.parse("2026-07-06"));
        daily.setSleepRecordId(sleepId);
        WellnessRecord collidingDaily = new WellnessRecord();
        collidingDaily.setId(sleepId);
        collidingDaily.setUserId(userId);
        collidingDaily.setRecordDate(LocalDate.parse("2026-07-05"));

        SleepRecord sleep = new SleepRecord();
        sleep.setId(sleepId);

        when(dailyRepo.findByIdAndUserId(sleepId, userId)).thenReturn(Optional.of(collidingDaily));
        when(dailyRepo.findFirstByUserIdAndSleepRecordId(userId, sleepId)).thenReturn(Optional.of(daily));
        when(sleepRepo.findById(sleepId)).thenReturn(Optional.of(sleep));

        WellnessRequest req = new WellnessRequest();
        req.sleepHours = 8.0;
        req.sleepTime = "23:00";
        req.wakeTime = "07:00";
        req.moodScore = 5;
        req.recordDate = "2026-07-06";
        req.notes = "Updated through old endpoint";
        req.exerciseDuration = 0;

        service.updateCompat(userId, sleepId, req);

        verify(sleepRepo).save(sleep);
        verify(ragClient).syncSleep(eq(sleepId), eq(userId), eq(8.0), eq("23:00"),
                eq("07:00"), eq(5), eq("2026-07-06"), eq("Updated through old endpoint"));
    }
}

// Author: Yutong Luo
package sg.edu.nus.wellness.service;

import org.junit.jupiter.api.Test;
import sg.edu.nus.wellness.dto.SleepRecordRequest;
import sg.edu.nus.wellness.exception.NotFoundException;
import sg.edu.nus.wellness.model.ExerciseRecord;
import sg.edu.nus.wellness.repository.ExerciseRecordRepo;
import sg.edu.nus.wellness.repository.SleepRecordRepo;
import sg.edu.nus.wellness.repository.WellnessRepo;

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
}

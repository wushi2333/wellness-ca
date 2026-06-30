// Author: Yutong Luo
package sg.edu.nus.wellness.dto;

import sg.edu.nus.wellness.model.WellnessRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class WellnessResponse {
    public Long id;
    public Double sleepHours;
    public String exerciseActivity;
    public Integer exerciseDuration;
    public Integer moodScore;
    public LocalDate recordDate;
    public String notes;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static WellnessResponse from(WellnessRecord record) {
        WellnessResponse response = new WellnessResponse();
        response.id = record.getId();
        response.sleepHours = record.getSleepHours();
        response.exerciseActivity = record.getExerciseActivity();
        response.exerciseDuration = record.getExerciseDuration();
        response.moodScore = record.getMoodScore();
        response.recordDate = record.getRecordDate();
        response.notes = record.getNotes();
        response.createdAt = record.getCreatedAt();
        response.updatedAt = record.getUpdatedAt();
        return response;
    }
}

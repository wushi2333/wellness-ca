// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

import java.util.List;

/** Aggregated daily wellness data returned by GET /records. */
public class DailyWellnessResponse {

    public Long dailyRecordId;
    public String recordDate;
    public SleepSummary sleep;
    public List<ExerciseSummary> exercises;

    public DailyWellnessResponse(Long dailyRecordId, String recordDate,
                                  SleepSummary sleep, List<ExerciseSummary> exercises) {
        this.dailyRecordId = dailyRecordId;
        this.recordDate = recordDate;
        this.sleep = sleep;
        this.exercises = exercises;
    }

    public static class SleepSummary {
        public Long id;
        public Double sleepHours;
        public String sleepTime;
        public String wakeTime;
        public Integer moodScore;
        public String notes;

        public SleepSummary(Long id, Double sleepHours, String sleepTime,
                            String wakeTime, Integer moodScore, String notes) {
            this.id = id; this.sleepHours = sleepHours;
            this.sleepTime = sleepTime; this.wakeTime = wakeTime;
            this.moodScore = moodScore; this.notes = notes;
        }
    }

    public static class ExerciseSummary {
        public Long id;
        public String exerciseActivity;
        public Integer exerciseDuration;
        public String notes;

        public ExerciseSummary(Long id, String exerciseActivity,
                               Integer exerciseDuration, String notes) {
            this.id = id; this.exerciseActivity = exerciseActivity;
            this.exerciseDuration = exerciseDuration; this.notes = notes;
        }
    }
}

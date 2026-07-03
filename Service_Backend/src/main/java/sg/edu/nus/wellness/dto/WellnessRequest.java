// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class WellnessRequest {
    @NotNull @DecimalMin("0.0") @DecimalMax("24.0")
    public Double sleepHours;
    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double v) { this.sleepHours = v; }

    @Size(max = 100)
    public String exerciseActivity = "";
    public String getExerciseActivity() { return exerciseActivity; }
    public void setExerciseActivity(String v) { this.exerciseActivity = v; }

    @NotNull @Min(0)
    public Integer exerciseDuration = 0;
    public Integer getExerciseDuration() { return exerciseDuration; }
    public void setExerciseDuration(Integer v) { this.exerciseDuration = v; }

    @Min(1) @Max(5)
    public Integer moodScore;
    public Integer getMoodScore() { return moodScore; }
    public void setMoodScore(Integer v) { this.moodScore = v; }

    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "recordDate must use yyyy-MM-dd format")
    public String recordDate;
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String v) { this.recordDate = v; }

    @Size(max = 1000)
    public String notes = "";
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}

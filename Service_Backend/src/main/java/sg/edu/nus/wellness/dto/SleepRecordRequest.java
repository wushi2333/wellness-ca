// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

import jakarta.validation.constraints.*;

public class SleepRecordRequest {

    @NotNull @DecimalMin("0.0") @DecimalMax("24.0")
    public Double sleepHours;

    @Size(max = 5)
    public String sleepTime;

    @Size(max = 5)
    public String wakeTime;

    @Min(1) @Max(5)
    public Integer moodScore;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    public String recordDate = "";

    @Size(max = 1000)
    public String notes = "";

    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double v) { sleepHours = v; }
    public String getSleepTime() { return sleepTime; }
    public void setSleepTime(String v) { sleepTime = v; }
    public String getWakeTime() { return wakeTime; }
    public void setWakeTime(String v) { wakeTime = v; }
    public Integer getMoodScore() { return moodScore; }
    public void setMoodScore(Integer v) { moodScore = v; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String v) { recordDate = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}

// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

import jakarta.validation.constraints.*;

public class ExerciseRecordRequest {

    @NotBlank @Size(max = 100)
    public String exerciseActivity;

    @NotNull @Min(1) @Max(1440)
    public Integer exerciseDuration;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    public String recordDate = "";

    @Size(max = 1000)
    public String notes = "";

    public String getExerciseActivity() { return exerciseActivity; }
    public void setExerciseActivity(String v) { exerciseActivity = v; }
    public Integer getExerciseDuration() { return exerciseDuration; }
    public void setExerciseDuration(Integer v) { exerciseDuration = v; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String v) { recordDate = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}

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
}

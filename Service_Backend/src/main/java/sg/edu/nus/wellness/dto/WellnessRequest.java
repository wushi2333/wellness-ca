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
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("24.0")
    public Double sleepHours;

    @Size(max = 100)
    public String exerciseActivity = "";

    @NotNull
    @Min(0)
    public Integer exerciseDuration = 0;

    @Min(1)
    @Max(5)
    public Integer moodScore;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "recordDate must use yyyy-MM-dd format")
    public String recordDate;

    @Size(max = 1000)
    public String notes = "";
}

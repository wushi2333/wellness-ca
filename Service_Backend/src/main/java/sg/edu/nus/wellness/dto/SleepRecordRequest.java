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
}

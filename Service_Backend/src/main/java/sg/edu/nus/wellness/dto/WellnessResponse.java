// Author: Yutong Luo
package sg.edu.nus.wellness.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Old-style flat response used by WebWellnessController.
 * Populated manually by WellnessService.list() from the new split model.
 */
public class WellnessResponse {
    public Long id;
    public Double sleepHours;
    public String sleepTime;
    public String wakeTime;
    public String exerciseActivity;
    public Integer exerciseDuration;
    public Integer moodScore;
    public LocalDate recordDate;
    public String notes;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}

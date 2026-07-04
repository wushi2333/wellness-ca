// Author: Xia Zihang
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_records")
public class ExerciseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → wellness_records.id */
    @Column(name = "daily_record_id", nullable = false)
    private Long dailyRecordId;

    @Column(length = 100)
    private String exerciseActivity;

    private Integer exerciseDuration;

    @Column(length = 1000)
    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // region getters/setters
    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public Long getDailyRecordId() { return dailyRecordId; }
    public void setDailyRecordId(Long v) { this.dailyRecordId = v; }

    public String getExerciseActivity() { return exerciseActivity; }
    public void setExerciseActivity(String v) { this.exerciseActivity = v; }

    public Integer getExerciseDuration() { return exerciseDuration; }
    public void setExerciseDuration(Integer v) { this.exerciseDuration = v; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    // endregion
}

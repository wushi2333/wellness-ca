// Author: Xia Zihang
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sleep_records")
public class SleepRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double sleepHours;

    @Column(length = 5)
    private String sleepTime;

    @Column(length = 5)
    private String wakeTime;

    private Integer moodScore;

    @Column(length = 1000)
    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // region getters/setters
    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double v) { this.sleepHours = v; }

    public String getSleepTime() { return sleepTime; }
    public void setSleepTime(String v) { this.sleepTime = v; }

    public String getWakeTime() { return wakeTime; }
    public void setWakeTime(String v) { this.wakeTime = v; }

    public Integer getMoodScore() { return moodScore; }
    public void setMoodScore(Integer v) { this.moodScore = v; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // endregion
}

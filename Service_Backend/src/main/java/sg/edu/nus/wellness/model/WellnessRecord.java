// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily journal — one row per user per date.
 * Links to a {@link SleepRecord} (0..1) via {@code sleepRecordId}.
 * {@link ExerciseRecord}s link back here via {@code dailyRecordId}.
 */
@Entity
@Table(name = "wellness_records",
       indexes = {@Index(name = "idx_user_record_date", columnList = "userId, recordDate DESC")},
       uniqueConstraints = {@UniqueConstraint(columnNames = {"userId", "recordDate"})})
public class WellnessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate recordDate;

    /** FK → sleep_records.id (nullable — a day may have no sleep entry). */
    private Long sleepRecordId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    private User user;

    public WellnessRecord() {}

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // region getters/setters
    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate v) { this.recordDate = v; }

    public Long getSleepRecordId() { return sleepRecordId; }
    public void setSleepRecordId(Long v) { this.sleepRecordId = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // endregion
}

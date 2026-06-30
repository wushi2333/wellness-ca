// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name="wellness_records")
public class WellnessRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) 
    private Long userId;
    private Double sleepHours;
    private String exerciseActivity;
    private Integer exerciseDuration;
    private Integer moodScore;
    private LocalDate recordDate;
    @Column(columnDefinition="TEXT")
    private String notes;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    private User user;

    public WellnessRecord(){}

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

    public Long getId(){return id;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Double getSleepHours(){return sleepHours;} public void setSleepHours(Double v){sleepHours=v;}
    public String getExerciseActivity(){return exerciseActivity;} public void setExerciseActivity(String v){exerciseActivity=v;}
    public Integer getExerciseDuration(){return exerciseDuration;} public void setExerciseDuration(Integer v){exerciseDuration=v;}
    public Integer getMoodScore(){return moodScore;} public void setMoodScore(Integer v){moodScore=v;}
    public LocalDate getRecordDate(){return recordDate;} public void setRecordDate(LocalDate v){recordDate=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
}

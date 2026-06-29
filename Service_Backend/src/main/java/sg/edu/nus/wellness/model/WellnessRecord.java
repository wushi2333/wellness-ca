// Author: Xia Zihang
package sg.edu.nus.wellness.model;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="wellness_records")
public class WellnessRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId;
    private Double sleepHours;
    private String exerciseActivity;
    private Integer exerciseDuration;
    private LocalDate recordDate;
    private String notes;
    public WellnessRecord(){}
    public Long getId(){return id;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Double getSleepHours(){return sleepHours;} public void setSleepHours(Double v){sleepHours=v;}
    public String getExerciseActivity(){return exerciseActivity;} public void setExerciseActivity(String v){exerciseActivity=v;}
    public Integer getExerciseDuration(){return exerciseDuration;} public void setExerciseDuration(Integer v){exerciseDuration=v;}
    public LocalDate getRecordDate(){return recordDate;} public void setRecordDate(LocalDate v){recordDate=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
}

// Author: Xia Zihang
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    private Long userId; // maps 1:1 to User.id

    @Column(length = 512)
    private String avatarUrl;

    @Column(length = 50)
    private String nickname;

    private Integer heightCm;
    private Integer age;
    private Double weightKg;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public UserProfile() {}

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

    // Getters / Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String v) { avatarUrl = v; }
    public String getNickname() { return nickname; }
    public void setNickname(String v) { nickname = v; }
    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer v) { heightCm = v; }
    public Integer getAge() { return age; }
    public void setAge(Integer v) { age = v; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double v) { weightKg = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity @Table(name="users")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false) private String username;
    @Column(nullable=false) private String hashedPassword;
    @Column(nullable=false, length=20) private String role = "USER";
    @Column(updatable=false) private LocalDateTime createdAt;

    public User(){}
    public User(String u,String p){username=u;hashedPassword=p;}

    @PrePersist
    public void prePersist() {
        if (role == null || role.isBlank()) role = "USER";
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId(){return id;}
    public String getUsername(){return username;}
    public String getHashedPassword(){return hashedPassword;}
    public String getRole(){return role;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setRole(String role){this.role=role;}
}

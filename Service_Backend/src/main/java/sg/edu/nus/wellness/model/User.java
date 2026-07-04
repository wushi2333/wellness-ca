// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity @Table(name="users")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false) private String username;
    @Column(unique=true) private String email;
    @Column(nullable=false) private String hashedPassword;
    @Column(nullable=false, length=20) private String role = "USER";
    @Column(length=20) private String provider = "LOCAL";
    private String providerId;
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
    public String getEmail(){return email;}
    public String getHashedPassword(){return hashedPassword;}
    public String getRole(){return role;}
    public String getProvider(){return provider;}
    public String getProviderId(){return providerId;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setRole(String role){this.role=role;}
    public void setEmail(String email){this.email=email;}
    public void setProvider(String provider){this.provider=provider;}
    public void setProviderId(String providerId){this.providerId=providerId;}
    public void setUsername(String v){username=v;}
    public void setHashedPassword(String v){hashedPassword=v;}
}

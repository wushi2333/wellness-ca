// Author: Xia Zihang
package sg.edu.nus.wellness.model;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false) private String username;
    @Column(nullable=false) private String hashedPassword;
    public User(){}
    public User(String u,String p){username=u;hashedPassword=p;}
    public Long getId(){return id;}
    public String getUsername(){return username;}
    public String getHashedPassword(){return hashedPassword;}
}

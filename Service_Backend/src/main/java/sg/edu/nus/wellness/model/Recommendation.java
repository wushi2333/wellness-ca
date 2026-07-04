// Author: Xia Zihang, Cai Peilin
package sg.edu.nus.wellness.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="recommendations")
public class Recommendation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; @Column(columnDefinition="TEXT") private String content;
    @Column(columnDefinition="JSON") private String evidence;
    private Integer iterations;
    private LocalDateTime createdAt;
    public Recommendation(){}
    public Recommendation(Long uid,String c){userId=uid;content=c;}
    public Recommendation(Long uid,String c,String e,Integer i){userId=uid;content=c;evidence=e;iterations=i;}
    public Long getId(){return id;}
    public String getContent(){return content;}
    public String getEvidence(){return evidence;}
    public Integer getIterations(){return iterations;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime v){createdAt=v;}

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

package sg.edu.nus.wellness.model;
import jakarta.persistence.*;
@Entity @Table(name="recommendations")
public class Recommendation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; @Column(columnDefinition="TEXT") private String content;
    public Recommendation(){}
    public Recommendation(Long uid,String c){userId=uid;content=c;}
    public String getContent(){return content;}
}

// Author: Xia Zihang
package sg.edu.nus.wellness.model;
import jakarta.persistence.*;
@Entity @Table(name="chat_history")
public class ChatHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; @Column(columnDefinition="TEXT") private String question;
    @Column(columnDefinition="TEXT") private String answer;
    public ChatHistory(){}
    public ChatHistory(Long uid,String q,String a){userId=uid;question=q;answer=a;}
}

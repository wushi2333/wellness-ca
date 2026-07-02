// Author: Xia Zihang
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "character_messages")
public class CharacterMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long sessionId;

    @Column(length = 10, nullable = false)
    public String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(length = 20)
    public String emotion;

    @Column(columnDefinition = "TEXT")
    public String tools;

    @Column(nullable = false)
    public boolean isCompressed = false;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}

// Author: Xia Zihang
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "character_sessions")
public class CharacterSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long userId;

    @Column(length = 100)
    public String title;

    @Column(length = 10)
    public String mode = "chat";

    @Column(columnDefinition = "TEXT")
    public String compressedContext;

    @Column(nullable = false)
    public int messageCount = 0;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();
}

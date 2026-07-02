// Author: Xia Zihang
package sg.edu.nus.wellness.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "character_user_profile")
public class CharacterUserProfile {
    @Id
    public Long userId;

    @Column(columnDefinition = "JSON", nullable = false)
    public String facts = "{}";

    @Column(nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();
}

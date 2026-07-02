// Author: Xia Zihang
package sg.edu.nus.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.wellness.model.CharacterUserProfile;

public interface CharacterUserProfileRepo extends JpaRepository<CharacterUserProfile, Long> {
}

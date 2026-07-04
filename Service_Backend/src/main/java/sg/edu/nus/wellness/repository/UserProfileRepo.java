// Author: Xia Zihang
package sg.edu.nus.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.wellness.model.UserProfile;

public interface UserProfileRepo extends JpaRepository<UserProfile, Long> {
}

// Author: Yutong Luo
package sg.edu.nus.wellness.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import sg.edu.nus.wellness.repository.*;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class UserServiceAccountDeletionTest {

    @Test
    void deleteAccountDeletesAgentSidecarRecommendationsBeforeUser() {
        UserRepo userRepo = mock(UserRepo.class);
        UserProfileRepo profileRepo = mock(UserProfileRepo.class);
        WellnessService wellnessService = mock(WellnessService.class);
        ChatHistoryRepo chatHistoryRepo = mock(ChatHistoryRepo.class);
        RecRepo recRepo = mock(RecRepo.class);
        CharacterSessionRepo characterSessionRepo = mock(CharacterSessionRepo.class);
        CharacterMessageRepo characterMessageRepo = mock(CharacterMessageRepo.class);
        CharacterUserProfileRepo characterUserProfileRepo = mock(CharacterUserProfileRepo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        UserService service = new UserService(userRepo, profileRepo, wellnessService,
                chatHistoryRepo, recRepo, characterSessionRepo, characterMessageRepo,
                characterUserProfileRepo, jdbcTemplate, mock(org.springframework.security.crypto.password.PasswordEncoder.class));

        Long userId = 32L;
        when(profileRepo.findById(userId)).thenReturn(Optional.empty());
        when(characterSessionRepo.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of());
        when(jdbcTemplate.update("DELETE FROM agent_recommendations WHERE user_id = ?", userId)).thenReturn(4);

        service.deleteAccount(userId);

        verify(recRepo).deleteAllByUserId(userId);
        verify(jdbcTemplate).update("DELETE FROM agent_recommendations WHERE user_id = ?", userId);
        verify(userRepo).deleteById(userId);
    }
}

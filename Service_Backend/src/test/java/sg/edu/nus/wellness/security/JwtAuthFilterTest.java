// Author: Yutong Luo
package sg.edu.nus.wellness.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import sg.edu.nus.wellness.repository.UserRepo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    @Test
    void rejectsTokenWhenUserNoLongerExists() throws Exception {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        UserRepo users = mock(UserRepo.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwt, users);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/records");
        request.setServletPath("/records");
        request.addHeader("Authorization", "Bearer valid-token-for-deleted-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwt.validateAndGetUserId("valid-token-for-deleted-user")).thenReturn("42");
        when(users.existsById(42L)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }
}

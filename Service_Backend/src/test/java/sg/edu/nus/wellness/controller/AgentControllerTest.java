// Author: Yutong Luo
package sg.edu.nus.wellness.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import sg.edu.nus.wellness.model.Recommendation;
import sg.edu.nus.wellness.repository.RecRepo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void recommendReturnsLocalRecommendationIdAsSavedId() {
        RestTemplate http = mock(RestTemplate.class);
        RecRepo recRepo = mock(RecRepo.class);
        AgentController controller = new AgentController("http://agent", http, recRepo);

        Map<String, Object> agentResponse = new HashMap<>();
        agentResponse.put("recommendation", "Sleep earlier tonight.");
        agentResponse.put("evidence", List.of());
        agentResponse.put("iterations", 3);
        agentResponse.put("saved_id", 75);

        when(http.exchange(eq("http://agent/recommend"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(agentResponse));

        Recommendation saved = new Recommendation(32L, "Sleep earlier tonight.", "[]", 3);
        ReflectionTestUtils.setField(saved, "id", 58L);
        when(recRepo.save(any(Recommendation.class))).thenReturn(saved);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-API-Token", "team-wellness-2025");
        request.setAttribute("userId", 32L);

        ResponseEntity<?> response = controller.recommend(Map.of("language", "en"), request);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(58L, body.get("saved_id"));
    }
}

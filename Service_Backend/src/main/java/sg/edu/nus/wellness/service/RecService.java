// Author: Xia Zihang
package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.model.*;
import sg.edu.nus.wellness.repository.RecRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class RecService {
    private final RestTemplate http = new RestTemplate();
    private final RecRepo repo; private final WellnessService ws;
    private final String key, url = "https://api.deepseek.com/chat/completions";
    private final ObjectMapper om = new ObjectMapper();

    public RecService(RecRepo r, WellnessService w, @Value("${app.deepseek.key}") String k) { repo=r; ws=w; key=k; }

    @SuppressWarnings("unchecked")
    public List<String> generate(Long userId) {
        List<WellnessRecord> records = ws.last7(userId);
        if (records.isEmpty()) return List.of("Start logging your wellness data to get tips!");
        StringBuilder sb = new StringBuilder();
        for (var r : records) sb.append(String.format("- %s: sleep %.1fh, %s (%d min)\n",
            r.getRecordDate(), r.getSleepHours(),
            r.getExerciseActivity()!=null?r.getExerciseActivity():"no exercise",
            r.getExerciseDuration()!=null?r.getExerciseDuration():0));

        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(key); h.setContentType(MediaType.APPLICATION_JSON);
        Map<String,Object> body = Map.of("model","deepseek-chat","messages",List.of(
            Map.of("role","system","content","You are a wellness coach. Give 3 short, personalized tips. Return ONLY a JSON array: [\"tip1\",\"tip2\",\"tip3\"]"),
            Map.of("role","user","content","Recent data:\n"+sb.toString())
        ),"temperature",0.8,"max_tokens",400);
        ResponseEntity<Map> resp = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body,h), Map.class);
        String raw = ((Map<String,String>)((Map)((List)resp.getBody().get("choices")).get(0)).get("message")).get("content");
        try { List<String> tips = om.readValue(raw, List.class);
            for (String t : tips) repo.save(new Recommendation(userId, t));
            return tips;
        } catch (Exception e) { repo.save(new Recommendation(userId, raw)); return List.of(raw); }
    }
}

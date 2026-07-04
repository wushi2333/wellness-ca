// Author: Xia Zihang
package sg.edu.nus.wellness.service;
import sg.edu.nus.wellness.model.*;
import sg.edu.nus.wellness.repository.RecRepo;
import sg.edu.nus.wellness.dto.DailyWellnessResponse;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class RecService {
    private final RecRepo repo;
    private final WellnessService ws;
    private final DeepSeekClient llm;
    private final ObjectMapper om = new ObjectMapper();

    public RecService(RecRepo r, WellnessService w, DeepSeekClient llm) {
        repo = r; ws = w; this.llm = llm;
    }

    public List<String> generate(Long userId) {
        List<DailyWellnessResponse> dailies = ws.listDaily(userId);
        if (dailies.isEmpty()) return List.of("Start logging your wellness data to get tips!");

        StringBuilder sb = new StringBuilder();
        for (var d : dailies) {
            double sleepH = d.sleep != null ? d.sleep.sleepHours : 0;
            String sleepStr = d.sleep != null ? String.format("sleep %.1fh", sleepH) : "no sleep";
            int exTotal = 0;
            StringBuilder exStr = new StringBuilder();
            if (d.exercises != null && !d.exercises.isEmpty()) {
                for (var e : d.exercises) {
                    exTotal += e.exerciseDuration;
                    exStr.append(e.exerciseActivity).append(" ").append(e.exerciseDuration).append("min, ");
                }
                if (exStr.length() > 2) exStr.setLength(exStr.length() - 2);
            }
            if (exStr.isEmpty()) exStr.append("no exercise");
            sb.append(String.format("- %s: %s, %s (%d min total)\n",
                d.recordDate, sleepStr, exStr.toString(), exTotal));
        }

        List<Map<String,String>> messages = List.of(
            Map.of("role","system","content","You are a wellness coach. Give 3 short, personalized tips. Return ONLY a JSON array: [\"tip1\",\"tip2\",\"tip3\"]"),
            Map.of("role","user","content","Recent data:\n"+sb.toString())
        );
        String raw = llm.complete(messages, 0.8, 400);
        try {
            @SuppressWarnings("unchecked")
            List<String> tips = om.readValue(raw, List.class);
            for (String t : tips) repo.save(new Recommendation(userId, t));
            return tips;
        } catch (Exception e) {
            repo.save(new Recommendation(userId, raw));
            return List.of(raw);
        }
    }
}

// Author: Guo Jiali
package sg.edu.nus.wellness.controller.web;

import sg.edu.nus.wellness.dto.DailyWellnessResponse;
import sg.edu.nus.wellness.model.CharacterMessage;
import sg.edu.nus.wellness.model.CharacterSession;
import sg.edu.nus.wellness.model.Recommendation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WebViewModels {
    static final List<String> EXERCISE_TYPES = List.of(
            "Running", "Cycling", "Swimming", "Yoga", "Strength Training",
            "Cardio", "Walking", "HIIT", "Pilates", "Other");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private WebViewModels() {}

    static DashboardSummary dashboard(List<DailyWellnessResponse> dailies) {
        LocalDate thisMonday = currentWeekMonday();
        LocalDate prevMonday = thisMonday.minusDays(7);
        List<Double> thisSleep = weekSleepValues(thisMonday, dailies);
        List<Double> prevSleep = weekSleepValues(prevMonday, dailies);
        List<Integer> thisExercise = weekExerciseValues(thisMonday, dailies);

        double avgSleep = averageDoubles(thisSleep);
        double prevAvgSleep = averageDoubles(prevSleep);
        double sleepTrend = prevAvgSleep > 0 ? avgSleep - prevAvgSleep : 0.0;
        List<Integer> activeExercise = thisExercise.stream().filter(v -> v != null && v > 0).toList();
        double avgExercise = activeExercise.isEmpty()
                ? 0.0
                : activeExercise.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        List<String> tips = List.of(
                "Drink water before you feel thirsty.",
                "A 10 minute walk can reset your energy.",
                "Keep sleep and wake times consistent.",
                "Stretch your neck and shoulders today.",
                "Take one slow breathing break.");
        int idx = Math.floorMod(LocalDate.now().getDayOfYear(), tips.size());

        return new DashboardSummary(
                avgSleep,
                sleepTrend,
                avgExercise,
                activeExercise.size(),
                thisSleep.stream().map(v -> v == null ? 0.0 : v).toList(),
                thisExercise.stream().map(v -> v == null ? 0 : v).toList(),
                tips.get(idx));
    }

    static DetailSummary sleepDetail(List<DailyWellnessResponse> dailies, LocalDate monday) {
        LocalDate week = monday == null ? currentWeekMonday() : monday;
        List<Double> sleep = weekSleepValues(week, dailies);
        double avg = averageDoubles(sleep);
        double best = sleep.stream().filter(v -> v != null && v > 0).mapToDouble(Double::doubleValue).max().orElse(0.0);
        List<Integer> moods = weekMoodValues(week, dailies);
        double avgMood = averageInts(moods);
        return new DetailSummary(week, weekLabel(week), dayLabels(week), sleep, List.of(),
                avg, best, avgMood, 0.0, 0, Map.of());
    }

    static DetailSummary exerciseDetail(List<DailyWellnessResponse> dailies, LocalDate monday) {
        LocalDate week = monday == null ? currentWeekMonday() : monday;
        List<Integer> exercise = weekExerciseValues(week, dailies);
        List<Integer> active = exercise.stream().filter(v -> v != null && v > 0).toList();
        double avg = active.isEmpty() ? 0.0 : active.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        Map<String, Integer> breakdown = exerciseBreakdown(dailies, week);
        return new DetailSummary(week, weekLabel(week), dayLabels(week), List.of(), exercise,
                0.0, 0.0, 0.0, avg, active.size(), breakdown);
    }

    static List<RecordSection> recordSections(List<DailyWellnessResponse> dailies, String type) {
        Map<LocalDate, List<RecordItem>> grouped = new LinkedHashMap<>();
        for (DailyWellnessResponse daily : dailies) {
            LocalDate date = parseDate(daily.recordDate);
            LocalDate monday = weekMonday(date);
            if ("sleep".equals(type) && daily.sleep != null) {
                grouped.computeIfAbsent(monday, k -> new ArrayList<>())
                        .add(RecordItem.sleep(daily.recordDate, daily.sleep));
            } else if ("exercise".equals(type) && daily.exercises != null) {
                for (DailyWellnessResponse.ExerciseSummary exercise : daily.exercises) {
                    grouped.computeIfAbsent(monday, k -> new ArrayList<>())
                            .add(RecordItem.exercise(daily.recordDate, exercise));
                }
            }
        }

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<RecordItem>>comparingByKey().reversed())
                .map(e -> new RecordSection(weekLabel(e.getKey()), e.getValue()))
                .toList();
    }

    static List<ChatMessageView> chatMessages(List<CharacterMessage> messages) {
        List<ChatMessageView> result = new ArrayList<>();
        for (CharacterMessage message : messages) {
            result.add(new ChatMessageView(message.id, message.role, message.content,
                    message.emotion == null ? "" : message.emotion, parseStringList(message.tools),
                    message.createdAt == null ? "" : message.createdAt.toString()));
        }
        return result;
    }

    static List<ChatSessionView> chatSessions(List<CharacterSession> sessions, Long currentSessionId) {
        return sessions.stream()
                .sorted(Comparator.comparing((CharacterSession s) -> s.updatedAt).reversed())
                .map(s -> new ChatSessionView(s.id, s.title, s.mode, s.messageCount,
                        s.updatedAt == null ? "" : s.updatedAt.toString(),
                        currentSessionId != null && currentSessionId.equals(s.id)))
                .toList();
    }

    static List<RecommendationView> recommendations(List<Recommendation> recommendations) {
        return recommendations.stream().map(r -> new RecommendationView(
                r.getId(),
                r.getContent(),
                parseEvidence(r.getEvidence()),
                r.getIterations() == null ? 0 : r.getIterations(),
                r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()))
                .toList();
    }

    static LocalDate currentWeekMonday() {
        return weekMonday(LocalDate.now());
    }

    static LocalDate parseWeek(String value) {
        if (value == null || value.isBlank()) return currentWeekMonday();
        try {
            return LocalDate.parse(value, DATE);
        } catch (RuntimeException ex) {
            return currentWeekMonday();
        }
    }

    static LocalDate previousWeek(LocalDate monday) {
        return monday.minusDays(7);
    }

    static LocalDate nextWeek(LocalDate monday) {
        LocalDate next = monday.plusDays(7);
        return next.isAfter(currentWeekMonday()) ? monday : next;
    }

    private static List<Double> weekSleepValues(LocalDate monday, List<DailyWellnessResponse> dailies) {
        return weekValues(monday, dailies, daily -> daily != null && daily.sleep != null ? daily.sleep.sleepHours : null);
    }

    private static List<Integer> weekMoodValues(LocalDate monday, List<DailyWellnessResponse> dailies) {
        return weekValues(monday, dailies, daily -> daily != null && daily.sleep != null ? daily.sleep.moodScore : null);
    }

    private static List<Integer> weekExerciseValues(LocalDate monday, List<DailyWellnessResponse> dailies) {
        return weekValues(monday, dailies, daily -> {
            if (daily == null || daily.exercises == null) return null;
            int total = daily.exercises.stream()
                    .map(e -> e.exerciseDuration == null ? 0 : e.exerciseDuration)
                    .mapToInt(Integer::intValue)
                    .sum();
            return total;
        });
    }

    private interface DailyMapper<T> {
        T map(DailyWellnessResponse daily);
    }

    private static <T> List<T> weekValues(LocalDate monday, List<DailyWellnessResponse> dailies, DailyMapper<T> mapper) {
        Map<String, DailyWellnessResponse> byDate = new LinkedHashMap<>();
        for (DailyWellnessResponse daily : dailies) byDate.put(daily.recordDate, daily);
        List<T> values = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            DailyWellnessResponse daily = byDate.get(monday.plusDays(i).toString());
            values.add(mapper.map(daily));
        }
        return values;
    }

    private static Map<String, Integer> exerciseBreakdown(List<DailyWellnessResponse> dailies, LocalDate monday) {
        Map<String, Integer> map = new LinkedHashMap<>();
        LocalDate sunday = monday.plusDays(6);
        for (DailyWellnessResponse daily : dailies) {
            LocalDate date = parseDate(daily.recordDate);
            if (date.isBefore(monday) || date.isAfter(sunday) || daily.exercises == null) continue;
            for (DailyWellnessResponse.ExerciseSummary exercise : daily.exercises) {
                String activity = exercise.exerciseActivity == null ? "Other" : exercise.exerciseActivity;
                map.put(activity, map.getOrDefault(activity, 0) + (exercise.exerciseDuration == null ? 0 : exercise.exerciseDuration));
            }
        }
        return map;
    }

    private static List<String> dayLabels(LocalDate monday) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            labels.add(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        }
        return labels;
    }

    private static LocalDate weekMonday(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private static String weekLabel(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        return monday + " - " + sunday;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE);
        } catch (RuntimeException ex) {
            return LocalDate.now();
        }
    }

    private static double averageDoubles(List<Double> values) {
        return values.stream().filter(v -> v != null && v > 0).mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double averageInts(List<Integer> values) {
        return values.stream().filter(v -> v != null && v > 0).mapToInt(Integer::intValue).average().orElse(0.0);
    }

    private static List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return MAPPER.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of(raw);
        }
    }

    private static List<Map<String, Object>> parseEvidence(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return MAPPER.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return List.of(Map.of("summary", raw));
        }
    }

    public record DashboardSummary(double avgSleep, double sleepTrend, double avgExercise,
                            int exerciseDays, List<Double> sleepSeries,
                            List<Integer> exerciseSeries, String tip) {}

    public record DetailSummary(LocalDate monday, String weekLabel, List<String> dayLabels,
                         List<Double> sleepSeries, List<Integer> exerciseSeries,
                         double avgSleep, double bestSleep, double avgMood,
                         double avgExercise, int activeDays,
                         Map<String, Integer> exerciseBreakdown) {}

    public record RecordSection(String label, List<RecordItem> items) {}

    public record RecordItem(String type, Long id, String date, String primary, String secondary,
                      String notes, Double sleepHours, String sleepTime, String wakeTime,
                      Integer moodScore, String exerciseActivity, Integer exerciseDuration) {
        static RecordItem sleep(String date, DailyWellnessResponse.SleepSummary sleep) {
            String primary = String.format(Locale.US, "%.1fh", sleep.sleepHours == null ? 0.0 : sleep.sleepHours);
            String secondary = ((sleep.sleepTime == null ? "" : sleep.sleepTime)
                    + " - " + (sleep.wakeTime == null ? "" : sleep.wakeTime)).trim();
            return new RecordItem("sleep", sleep.id, date, primary, secondary, sleep.notes,
                    sleep.sleepHours, sleep.sleepTime, sleep.wakeTime, sleep.moodScore,
                    null, null);
        }

        static RecordItem exercise(String date, DailyWellnessResponse.ExerciseSummary exercise) {
            String primary = exercise.exerciseActivity == null ? "Other" : exercise.exerciseActivity;
            String secondary = (exercise.exerciseDuration == null ? 0 : exercise.exerciseDuration) + " min";
            return new RecordItem("exercise", exercise.id, date, primary, secondary, exercise.notes,
                    null, null, null, null, exercise.exerciseActivity, exercise.exerciseDuration);
        }
    }

    public record ChatMessageView(Long id, String role, String content, String emotion,
                           List<String> tools, String createdAt) {}

    public record ChatSessionView(Long id, String title, String mode, int messageCount,
                           String updatedAt, boolean active) {}

    public record RecommendationView(Long id, String content, List<Map<String, Object>> evidence,
                              int iterations, String createdAt) {}
}

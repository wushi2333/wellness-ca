// Author: Guo Jiali, Xia Zihang
package sg.edu.nus.wellness.controller.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.DailyWellnessResponse;
import sg.edu.nus.wellness.dto.ExerciseRecordRequest;
import sg.edu.nus.wellness.dto.PagedResponse;
import sg.edu.nus.wellness.dto.SleepRecordRequest;
import sg.edu.nus.wellness.service.WellnessService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class WebWellnessController {
    private final WellnessService wellnessService;

    public WebWellnessController(WellnessService wellnessService) {
        this.wellnessService = wellnessService;
    }

    @GetMapping("/web/records")
    public String records(HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();

        List<DailyWellnessResponse> dailies = wellnessService.listDailyPaged(userId, 0, 200).content;
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("dailies", dailies);
        model.addAttribute("sleepSections", WebViewModels.recordSections(dailies, "sleep"));
        model.addAttribute("exerciseSections", WebViewModels.recordSections(dailies, "exercise"));
        return "web/records";
    }

    @GetMapping("/web/records/new")
    public String chooseRecordType(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) return WebSession.redirectToLogin();
        WebSession.addCommonModel(session, model, "records");
        return "web/record-new";
    }

    @GetMapping("/web/records/sleep/new")
    public String newSleep(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) return WebSession.redirectToLogin();
        SleepRecordRequest form = new SleepRecordRequest();
        form.recordDate = LocalDate.now().toString();
        form.sleepTime = "23:00";
        form.wakeTime = "07:00";
        form.sleepHours = 8.0;
        form.moodScore = 3;
        addSleepFormModel(session, model, form, false, null);
        return "web/sleep-form";
    }

    @PostMapping("/web/records/sleep")
    public String createSleep(@Valid @ModelAttribute("sleep") SleepRecordRequest form,
                              BindingResult result,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        normalizeSleepHours(form);
        if (result.hasErrors()) {
            addSleepFormModel(session, model, form, false, null);
            return "web/sleep-form";
        }
        try {
            wellnessService.createSleep(userId, form);
            redirectAttributes.addFlashAttribute("success", "Sleep record saved.");
            return "redirect:/web/sleep-detail";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addSleepFormModel(session, model, form, false, null);
            return "web/sleep-form";
        }
    }

    @GetMapping("/web/records/sleep/{id}/edit")
    public String editSleep(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        SleepMatch match = findSleep(userId, id);
        if (match == null) {
            redirectAttributes.addFlashAttribute("error", "Sleep record not found.");
            return "redirect:/web/records";
        }
        SleepRecordRequest form = new SleepRecordRequest();
        form.sleepHours = match.sleep.sleepHours;
        form.sleepTime = match.sleep.sleepTime;
        form.wakeTime = match.sleep.wakeTime;
        form.moodScore = match.sleep.moodScore;
        form.recordDate = match.date;
        form.notes = match.sleep.notes;
        addSleepFormModel(session, model, form, true, id);
        return "web/sleep-form";
    }

    @PostMapping("/web/records/sleep/{id}")
    public String updateSleep(@PathVariable Long id,
                              @Valid @ModelAttribute("sleep") SleepRecordRequest form,
                              BindingResult result,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        normalizeSleepHours(form);
        if (result.hasErrors()) {
            addSleepFormModel(session, model, form, true, id);
            return "web/sleep-form";
        }
        try {
            wellnessService.updateSleep(userId, id, form);
            redirectAttributes.addFlashAttribute("success", "Sleep record updated.");
            return "redirect:/web/sleep-detail";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addSleepFormModel(session, model, form, true, id);
            return "web/sleep-form";
        }
    }

    @PostMapping("/web/records/sleep/{id}/delete")
    public String deleteSleep(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            wellnessService.deleteSleep(userId, id);
            redirectAttributes.addFlashAttribute("success", "Sleep record deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Sleep record not found.");
        }
        return "redirect:/web/records";
    }

    @GetMapping("/web/records/exercise/new")
    public String newExercise(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) return WebSession.redirectToLogin();
        ExerciseRecordRequest form = new ExerciseRecordRequest();
        form.recordDate = LocalDate.now().toString();
        form.exerciseActivity = "Running";
        form.exerciseDuration = 30;
        addExerciseFormModel(session, model, form, false, null);
        return "web/exercise-form";
    }

    @PostMapping("/web/records/exercise")
    public String createExercise(@Valid @ModelAttribute("exercise") ExerciseRecordRequest form,
                                 BindingResult result,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        if (result.hasErrors()) {
            addExerciseFormModel(session, model, form, false, null);
            return "web/exercise-form";
        }
        try {
            wellnessService.createExercise(userId, form);
            redirectAttributes.addFlashAttribute("success", "Exercise record saved.");
            return "redirect:/web/exercise-detail";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addExerciseFormModel(session, model, form, false, null);
            return "web/exercise-form";
        }
    }

    @GetMapping("/web/records/exercise/{id}/edit")
    public String editExercise(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        ExerciseMatch match = findExercise(userId, id);
        if (match == null) {
            redirectAttributes.addFlashAttribute("error", "Exercise record not found.");
            return "redirect:/web/records";
        }
        ExerciseRecordRequest form = new ExerciseRecordRequest();
        form.exerciseActivity = match.exercise.exerciseActivity;
        form.exerciseDuration = match.exercise.exerciseDuration;
        form.recordDate = match.date;
        form.notes = match.exercise.notes;
        addExerciseFormModel(session, model, form, true, id);
        return "web/exercise-form";
    }

    @PostMapping("/web/records/exercise/{id}")
    public String updateExercise(@PathVariable Long id,
                                 @Valid @ModelAttribute("exercise") ExerciseRecordRequest form,
                                 BindingResult result,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        if (result.hasErrors()) {
            addExerciseFormModel(session, model, form, true, id);
            return "web/exercise-form";
        }
        try {
            wellnessService.updateExercise(userId, id, form);
            redirectAttributes.addFlashAttribute("success", "Exercise record updated.");
            return "redirect:/web/exercise-detail";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addExerciseFormModel(session, model, form, true, id);
            return "web/exercise-form";
        }
    }

    @PostMapping("/web/records/exercise/{id}/delete")
    public String deleteExercise(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        try {
            wellnessService.deleteExercise(userId, id);
            redirectAttributes.addFlashAttribute("success", "Exercise record deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Exercise record not found.");
        }
        return "redirect:/web/records";
    }

    @GetMapping("/web/sleep-detail")
    public String sleepDetail(@RequestParam(required = false) String week, HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        List<DailyWellnessResponse> dailies = wellnessService.listDailyPaged(userId, 0, 200).content;
        LocalDate monday = WebViewModels.parseWeek(week);
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("detail", WebViewModels.sleepDetail(dailies, monday));
        model.addAttribute("prevWeek", WebViewModels.previousWeek(monday));
        model.addAttribute("nextWeek", WebViewModels.nextWeek(monday));
        model.addAttribute("prevDisabled", !WebViewModels.hasSleepBefore(dailies, monday));
        model.addAttribute("nextDisabled", WebViewModels.isLatestWeek(monday));
        return "web/sleep-detail";
    }

    @GetMapping("/web/exercise-detail")
    public String exerciseDetail(@RequestParam(required = false) String week, HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        List<DailyWellnessResponse> dailies = wellnessService.listDailyPaged(userId, 0, 200).content;
        LocalDate monday = WebViewModels.parseWeek(week);
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("detail", WebViewModels.exerciseDetail(dailies, monday));
        model.addAttribute("prevWeek", WebViewModels.previousWeek(monday));
        model.addAttribute("nextWeek", WebViewModels.nextWeek(monday));
        model.addAttribute("prevDisabled", !WebViewModels.hasExerciseBefore(dailies, monday));
        model.addAttribute("nextDisabled", WebViewModels.isLatestWeek(monday));
        return "web/exercise-detail";
    }

    @GetMapping("/web/records/manage/{type}")
    public String manage(@PathVariable String type, HttpSession session, Model model) {
        Long userId = WebSession.userId(session);
        if (userId == null) return WebSession.redirectToLogin();
        String normalized = "exercise".equals(type) ? "exercise" : "sleep";
        List<DailyWellnessResponse> dailies = wellnessService.listDailyPaged(userId, 0, 200).content;
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("type", normalized);
        model.addAttribute("sections", WebViewModels.recordSections(dailies, normalized));
        return "web/record-manage";
    }

    private void addSleepFormModel(HttpSession session, Model model, SleepRecordRequest form, boolean editMode, Long id) {
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("sleep", form);
        model.addAttribute("editMode", editMode);
        model.addAttribute("recordId", id);
        model.addAttribute("formAction", editMode ? "/web/records/sleep/" + id : "/web/records/sleep");
    }

    private void addExerciseFormModel(HttpSession session, Model model, ExerciseRecordRequest form, boolean editMode, Long id) {
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("exercise", form);
        model.addAttribute("exerciseTypes", WebViewModels.EXERCISE_TYPES);
        model.addAttribute("editMode", editMode);
        model.addAttribute("recordId", id);
        model.addAttribute("formAction", editMode ? "/web/records/exercise/" + id : "/web/records/exercise");
    }

    private void normalizeSleepHours(SleepRecordRequest form) {
        if (form.sleepHours != null && form.sleepHours > 0) return;
        try {
            LocalTime sleep = LocalTime.parse(form.sleepTime, DateTimeFormatter.ofPattern("H:mm"));
            LocalTime wake = LocalTime.parse(form.wakeTime, DateTimeFormatter.ofPattern("H:mm"));
            double hours = java.time.Duration.between(sleep, wake).toMinutes() / 60.0;
            if (hours < 0) hours += 24;
            form.sleepHours = hours;
        } catch (RuntimeException ex) {
            form.sleepHours = 0.0;
        }
    }

    private SleepMatch findSleep(Long userId, Long sleepId) {
        PagedResponse<DailyWellnessResponse> records = wellnessService.listDailyPaged(userId, 0, 500);
        for (DailyWellnessResponse daily : records.content) {
            if (daily.sleep != null && sleepId.equals(daily.sleep.id)) return new SleepMatch(daily.recordDate, daily.sleep);
        }
        return null;
    }

    private ExerciseMatch findExercise(Long userId, Long exerciseId) {
        PagedResponse<DailyWellnessResponse> records = wellnessService.listDailyPaged(userId, 0, 500);
        for (DailyWellnessResponse daily : records.content) {
            if (daily.exercises == null) continue;
            for (DailyWellnessResponse.ExerciseSummary exercise : daily.exercises) {
                if (exerciseId.equals(exercise.id)) return new ExerciseMatch(daily.recordDate, exercise);
            }
        }
        return null;
    }

    private record SleepMatch(String date, DailyWellnessResponse.SleepSummary sleep) {}
    private record ExerciseMatch(String date, DailyWellnessResponse.ExerciseSummary exercise) {}
}

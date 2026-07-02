// Author: Wellness CA Team
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.wellness.dto.WellnessRequest;
import sg.edu.nus.wellness.dto.WellnessResponse;
import sg.edu.nus.wellness.service.WellnessService;

import java.time.LocalDate;
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
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("records", wellnessService.list(userId));
        return "web/records";
    }

    @GetMapping("/web/records/new")
    public String newRecord(HttpSession session, Model model) {
        if (!WebSession.isLoggedIn(session)) {
            return WebSession.redirectToLogin();
        }

        WellnessRequest form = new WellnessRequest();
        form.exerciseActivity = "";
        form.exerciseDuration = 0;
        form.recordDate = LocalDate.now().toString();
        form.notes = "";
        addFormModel(session, model, form, false, null);
        return "web/record-form";
    }

    @PostMapping("/web/records")
    public String createRecord(@Valid @ModelAttribute("record") WellnessRequest form,
                               BindingResult result,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        if (result.hasErrors()) {
            addFormModel(session, model, form, false, null);
            return "web/record-form";
        }

        try {
            Long id = wellnessService.create(userId, form);
            redirectAttributes.addFlashAttribute("success", "Record saved.");
            return "redirect:/web/records/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", "Could not save record.");
            addFormModel(session, model, form, false, null);
            return "web/record-form";
        }
    }

    @GetMapping("/web/records/{id}")
    public String recordDetail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        WellnessResponse record = findRecord(userId, id);
        if (record == null) {
            redirectAttributes.addFlashAttribute("error", "Record not found.");
            return "redirect:/web/records";
        }

        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("record", record);
        return "web/record-detail";
    }

    @GetMapping("/web/records/{id}/edit")
    public String editRecord(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        WellnessResponse record = findRecord(userId, id);
        if (record == null) {
            redirectAttributes.addFlashAttribute("error", "Record not found.");
            return "redirect:/web/records";
        }

        WellnessRequest form = toRequest(record);
        addFormModel(session, model, form, true, id);
        return "web/record-form";
    }

    @PostMapping("/web/records/{id}")
    public String updateRecord(@PathVariable Long id,
                               @Valid @ModelAttribute("record") WellnessRequest form,
                               BindingResult result,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        if (result.hasErrors()) {
            addFormModel(session, model, form, true, id);
            return "web/record-form";
        }

        try {
            wellnessService.update(userId, id, form);
            redirectAttributes.addFlashAttribute("success", "Record updated.");
            return "redirect:/web/records/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", "Record not found.");
            addFormModel(session, model, form, true, id);
            return "web/record-form";
        }
    }

    @PostMapping("/web/records/{id}/delete")
    public String deleteRecord(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = WebSession.userId(session);
        if (userId == null) {
            return WebSession.redirectToLogin();
        }

        try {
            wellnessService.delete(userId, id);
            redirectAttributes.addFlashAttribute("success", "Record deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Record not found.");
        }
        return "redirect:/web/records";
    }

    private WellnessResponse findRecord(Long userId, Long id) {
        List<WellnessResponse> records = wellnessService.list(userId);
        return records.stream()
                .filter(record -> id.equals(record.id))
                .findFirst()
                .orElse(null);
    }

    private WellnessRequest toRequest(WellnessResponse record) {
        WellnessRequest form = new WellnessRequest();
        form.sleepHours = record.sleepHours;
        form.exerciseActivity = record.exerciseActivity == null ? "" : record.exerciseActivity;
        form.exerciseDuration = record.exerciseDuration == null ? 0 : record.exerciseDuration;
        form.moodScore = record.moodScore;
        form.recordDate = record.recordDate == null ? LocalDate.now().toString() : record.recordDate.toString();
        form.notes = record.notes == null ? "" : record.notes;
        return form;
    }

    private void addFormModel(HttpSession session, Model model, WellnessRequest form, boolean editMode, Long recordId) {
        WebSession.addCommonModel(session, model, "records");
        model.addAttribute("record", form);
        model.addAttribute("editMode", editMode);
        model.addAttribute("recordId", recordId);
        model.addAttribute("formAction", editMode ? "/web/records/" + recordId : "/web/records");
    }
}

package com.ecoexpress.ai.api;

import com.ecoexpress.ai.domain.MealGoal;
import com.ecoexpress.ai.domain.MealPlan;
import com.ecoexpress.ai.domain.MealPlanEntry;
import com.ecoexpress.ai.service.MealPlannerService;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Weekly Meal Planner (PRD §5.4). Requires a signed-in user; needs the Gemini key configured. */
@Tag(name = "Meal Planner")
@RestController
@RequestMapping("/api/v1/meal-plans")
@RequiredArgsConstructor
public class MealPlannerController {

    private final MealPlannerService mealPlannerService;

    public record GenerateRequest(@NotNull MealGoal goal, Integer targetCaloriesPerDay) {}

    public record MealEntryView(int day, String mealType, String title, int servings) {}

    public record MealPlanView(
            String id, String goal, LocalDate weekStart, String status,
            int entryCount, List<MealEntryView> meals) {}

    @Operation(summary = "Generate an AI weekly meal plan for a goal")
    @PostMapping("/generate")
    public ResponseEntity<MealPlanView> generate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody GenerateRequest r) {
        MealPlan plan = mealPlannerService.generate(user.getId(), r.goal(), r.targetCaloriesPerDay());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(plan));
    }

    private MealPlanView view(MealPlan plan) {
        List<MealEntryView> meals = plan.getEntries().stream()
                .sorted((a, b) -> {
                    int d = Integer.compare(a.getDayOfWeek(), b.getDayOfWeek());
                    return d != 0 ? d : a.getMealType().compareTo(b.getMealType());
                })
                .map(this::entryView)
                .toList();
        return new MealPlanView(plan.getId().toString(), plan.getGoal().name(),
                plan.getWeekStart(), plan.getStatus().name(), meals.size(), meals);
    }

    private MealEntryView entryView(MealPlanEntry e) {
        return new MealEntryView(e.getDayOfWeek(), e.getMealType().name(),
                e.getCustomTitle(), e.getServings());
    }
}

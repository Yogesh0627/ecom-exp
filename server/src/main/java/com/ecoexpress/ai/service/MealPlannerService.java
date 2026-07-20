package com.ecoexpress.ai.service;

import com.ecoexpress.ai.domain.AiFeature;
import com.ecoexpress.ai.domain.MealGoal;
import com.ecoexpress.ai.domain.MealPlan;
import com.ecoexpress.ai.domain.MealPlanEntry;
import com.ecoexpress.ai.domain.MealPlanStatus;
import com.ecoexpress.ai.domain.MealType;
import com.ecoexpress.ai.domain.PantryItem;
import com.ecoexpress.ai.exception.AiException;
import com.ecoexpress.ai.repository.MealPlanRepository;
import com.ecoexpress.ai.repository.PantryItemRepository;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.identity.domain.User;
import com.ecoexpress.identity.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Weekly Meal Planner (PRD §5.4).
 *
 * <p>Text-only AI. It grounds the prompt in the user's goal AND their pantry, so the plan uses
 * what they already have — the "consume pantry before suggesting a purchase" principle applied to
 * meals. The model is asked for strict JSON; the response is validated and parsed into
 * {@code meal_plan_entries}. A malformed reply throws a clean 503 rather than persisting garbage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealPlannerService {

    private static final String SYSTEM = """
            You are a practical Indian meal-planning assistant. You produce simple, home-cookable
            vegetarian-friendly meal plans using widely available Indian ingredients. You always
            reply with STRICT JSON and nothing else.""";

    private final AiService aiService;
    private final MealPlanRepository mealPlanRepository;
    private final PantryItemRepository pantryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper;

    @Transactional
    public MealPlan generate(UUID userId, MealGoal goal, Integer targetCalories) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user " + userId));

        // Week starts on the coming Monday (meal_plans_week_start_chk requires a Monday).
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        List<PantryItem> pantry = pantryRepository.findActiveForUser(userId);
        String pantryList = pantry.isEmpty() ? "(empty)"
                : pantry.stream().map(PantryItem::getIngredientName).distinct()
                        .collect(Collectors.joining(", "));

        String prompt = buildPrompt(goal, targetCalories, pantryList);

        // Reserve the plan id first so the AI call can be logged against it (ref_type/ref_id).
        MealPlan plan = MealPlan.builder()
                .user(user)
                .goal(goal)
                .weekStart(weekStart)
                .status(MealPlanStatus.DRAFT)
                .targetCaloriesPerDay(targetCalories)
                .generatedBy("AI")
                .build();
        plan = mealPlanRepository.save(plan);

        AiService.AiResult result = aiService.generateText(
                AiFeature.MEAL_PLAN, userId, SYSTEM, prompt, true, "MEAL_PLAN", plan.getId());

        parseInto(plan, result.text());
        plan.setStatus(MealPlanStatus.ACTIVE);
        log.info("Meal plan {} generated for {} ({} entries, {} tokens, ~{} INR)",
                plan.getId(), goal, plan.getEntries().size(),
                result.tokensIn() + result.tokensOut(), result.costInr());
        return plan;
    }

    private String buildPrompt(MealGoal goal, Integer calories, String pantry) {
        return """
                Create a 7-day meal plan for this goal: %s.
                %s
                The person already has these pantry items — prefer using them: %s.

                Reply with STRICT JSON in exactly this shape, no prose, no markdown:
                {
                  "days": [
                    {
                      "day": 1,
                      "meals": [
                        {"type": "BREAKFAST", "title": "Poha with peanuts", "servings": 1},
                        {"type": "LUNCH", "title": "...", "servings": 1},
                        {"type": "SNACK", "title": "...", "servings": 1},
                        {"type": "DINNER", "title": "...", "servings": 1}
                      ]
                    }
                  ]
                }
                Rules: day is 1..7 (1=Monday). type is one of BREAKFAST, LUNCH, SNACK, DINNER.
                Provide all 7 days. Keep titles short (max 8 words).""".formatted(
                goal.name().toLowerCase().replace('_', ' '),
                calories == null ? "" : "Target roughly " + calories + " kcal per day.",
                pantry);
    }

    /**
     * Parses the model's JSON into meal-plan entries. Defensive throughout: an unknown meal type
     * or an out-of-range day is skipped rather than allowed to hit a DB constraint, and a reply
     * that is not valid JSON fails cleanly.
     */
    private void parseInto(MealPlan plan, String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new AiException("The meal planner returned an unreadable response. Please retry.");
        }

        JsonNode days = root.path("days");
        if (!days.isArray() || days.isEmpty()) {
            throw new AiException("The meal planner returned no days. Please retry.");
        }

        for (JsonNode day : days) {
            int dayNum = day.path("day").asInt(0);
            if (dayNum < 1 || dayNum > 7) {
                continue;
            }
            for (JsonNode meal : day.path("meals")) {
                MealType type = parseMealType(meal.path("type").asText(null));
                if (type == null) {
                    continue;
                }
                String title = meal.path("title").asText(null);
                if (title == null || title.isBlank()) {
                    continue;
                }
                int servings = Math.max(1, meal.path("servings").asInt(1));

                // Guard the UNIQUE(plan, day, type) slot: skip a duplicate the model may emit.
                boolean slotTaken = plan.getEntries().stream().anyMatch(e ->
                        e.getDayOfWeek() == (short) dayNum && e.getMealType() == type);
                if (slotTaken) {
                    continue;
                }

                plan.getEntries().add(MealPlanEntry.builder()
                        .mealPlan(plan)
                        .dayOfWeek((short) dayNum)
                        .mealType(type)
                        .servings(servings)
                        .customTitle(title.trim())
                        .build());
            }
        }

        if (plan.getEntries().isEmpty()) {
            throw new AiException("The meal planner produced no usable meals. Please retry.");
        }
    }

    private MealType parseMealType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return MealType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package com.ecoexpress.cart.service;

import com.ecoexpress.cart.domain.CartItem;
import com.ecoexpress.cart.dto.CartDtos.NutrientTotals;
import com.ecoexpress.cart.dto.CartDtos.NutritionSummaryResponse;
import com.ecoexpress.cart.dto.CartDtos.Warning;
import com.ecoexpress.catalog.domain.NutritionFacts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Smart Cart Nutrition (PRD §5.2): cart totals, a health score, and imbalance warnings.
 *
 * <p><b>This is a shopping aid, not dietary advice.</b> The thresholds below are the UK
 * FSA front-of-pack "traffic light" bands per 100g — a widely used, published reference,
 * chosen so the scoring is explainable rather than invented. They are not tailored to any
 * individual, and nothing here should be presented to a user as a health assessment.
 *
 * <p><b>Incomplete data yields no score.</b> If any line has no nutrition row, totals are
 * a lower bound and {@code healthScore} is null. Scoring a basket while silently ignoring
 * the items we know nothing about would produce a confident number that is simply wrong —
 * and a cart of unmeasured items would score best of all, because absent data reads as
 * "no sugar, no sodium". That failure mode is worse than showing nothing.
 */
@Component
public class NutritionCalculator {

    // UK FSA per-100g thresholds for solid foods.
    private static final BigDecimal SUGAR_LOW = new BigDecimal("5.0");
    private static final BigDecimal SUGAR_HIGH = new BigDecimal("22.5");
    private static final BigDecimal FAT_LOW = new BigDecimal("3.0");
    private static final BigDecimal FAT_HIGH = new BigDecimal("17.5");
    private static final BigDecimal SODIUM_LOW = new BigDecimal("120");
    private static final BigDecimal SODIUM_HIGH = new BigDecimal("600");
    // "High fibre" claim threshold (EU Reg. 1924/2006): 6g/100g.
    private static final BigDecimal FIBER_GOOD = new BigDecimal("6.0");
    // "High protein" claim: 20% of energy from protein; 12g/100g is the common proxy.
    private static final BigDecimal PROTEIN_GOOD = new BigDecimal("12.0");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public NutritionSummaryResponse summarise(List<CartItem> items) {
        List<String> missing = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (CartItem item : items) {
            BigDecimal lineWeight = item.getVariant().getWeightGrams()
                    .multiply(BigDecimal.valueOf(item.getQty()));
            totalWeight = totalWeight.add(lineWeight);
            if (item.getVariant().getNutritionFacts() == null) {
                missing.add(item.getVariant().getProduct().getName());
            }
        }

        NutrientTotals totals = new NutrientTotals(
                sum(items, NutritionFacts::getCaloriesKcal),
                sum(items, NutritionFacts::getProteinG),
                sum(items, NutritionFacts::getFatG),
                sum(items, NutritionFacts::getCarbohydratesG),
                sum(items, NutritionFacts::getFiberG),
                sum(items, NutritionFacts::getSugarG),
                sum(items, NutritionFacts::getIronMg),
                sum(items, NutritionFacts::getVitaminAMcg),
                sum(items, NutritionFacts::getVitaminCMg),
                sum(items, NutritionFacts::getVitaminDMcg),
                sum(items, NutritionFacts::getPotassiumMg),
                sum(items, NutritionFacts::getSodiumMg));

        boolean complete = missing.isEmpty() && !items.isEmpty();

        // Per-100g profile of the basket, used for scoring. Weighed only over the lines
        // we actually have data for — dividing known nutrients by the FULL basket weight
        // would dilute the profile with unmeasured items and make everything look healthy.
        BigDecimal measuredWeight = measuredWeight(items);
        NutrientTotals per100g = measuredWeight.signum() > 0
                ? per100g(totals, measuredWeight)
                : null;

        List<Warning> warnings = per100g == null ? List.of() : buildWarnings(per100g);
        Integer score = complete && per100g != null ? score(per100g) : null;

        String basis = complete
                ? "UK FSA per-100g traffic-light bands across the basket. A shopping aid, not dietary advice."
                : "Not scored: " + missing.size() + " item(s) have no nutrition data, so totals are a lower bound.";

        return new NutritionSummaryResponse(
                complete, missing, scale(totalWeight, 2), totals, per100g, score, basis, warnings);
    }

    /** Total weight of only those lines that have nutrition data. */
    private BigDecimal measuredWeight(List<CartItem> items) {
        return items.stream()
                .filter(i -> i.getVariant().getNutritionFacts() != null)
                .map(i -> i.getVariant().getWeightGrams().multiply(BigDecimal.valueOf(i.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Scales each line's per-basis figure by its real weight and sums.
     *
     * <p>A null nutrient contributes nothing rather than zero: "not measured" must not be
     * asserted as "contains none". The {@code complete} flag is what tells the caller the
     * difference.
     */
    private BigDecimal sum(List<CartItem> items, Function<NutritionFacts, BigDecimal> field) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            NutritionFacts nf = item.getVariant().getNutritionFacts();
            if (nf == null) {
                continue;
            }
            BigDecimal value = field.apply(nf);
            if (value == null || nf.getBasisGrams() == null || nf.getBasisGrams().signum() <= 0) {
                continue;
            }
            BigDecimal lineWeight = item.getVariant().getWeightGrams()
                    .multiply(BigDecimal.valueOf(item.getQty()));
            total = total.add(
                    value.multiply(lineWeight).divide(nf.getBasisGrams(), 6, RoundingMode.HALF_UP));
        }
        return scale(total, 2);
    }

    private NutrientTotals per100g(NutrientTotals t, BigDecimal weight) {
        Function<BigDecimal, BigDecimal> norm =
                v -> v == null ? null : scale(v.multiply(HUNDRED).divide(weight, 6, RoundingMode.HALF_UP), 2);
        return new NutrientTotals(
                norm.apply(t.caloriesKcal()), norm.apply(t.proteinG()), norm.apply(t.fatG()),
                norm.apply(t.carbohydratesG()), norm.apply(t.fiberG()), norm.apply(t.sugarG()),
                norm.apply(t.ironMg()), norm.apply(t.vitaminAMcg()), norm.apply(t.vitaminCMg()),
                norm.apply(t.vitaminDMcg()), norm.apply(t.potassiumMg()), norm.apply(t.sodiumMg()));
    }

    /**
     * 0-100, the mean of five equally weighted per-100g components. Deliberately simple
     * and explainable: an opaque weighting would be impossible to justify to a customer
     * who asks why their basket scored 40.
     */
    private int score(NutrientTotals p) {
        List<Integer> parts = new ArrayList<>();
        parts.add(lowerIsBetter(p.sugarG(), SUGAR_LOW, SUGAR_HIGH));
        parts.add(lowerIsBetter(p.fatG(), FAT_LOW, FAT_HIGH));
        parts.add(lowerIsBetter(p.sodiumMg(), SODIUM_LOW, SODIUM_HIGH));
        parts.add(higherIsBetter(p.fiberG(), FIBER_GOOD));
        parts.add(higherIsBetter(p.proteinG(), PROTEIN_GOOD));

        return (int) Math.round(parts.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /** 100 at or below {@code low}, 0 at or above {@code high}, linear between. */
    private int lowerIsBetter(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value == null) {
            return 50;
        }
        if (value.compareTo(low) <= 0) {
            return 100;
        }
        if (value.compareTo(high) >= 0) {
            return 0;
        }
        BigDecimal span = high.subtract(low);
        BigDecimal over = value.subtract(low);
        return 100 - over.multiply(HUNDRED).divide(span, 0, RoundingMode.HALF_UP).intValue();
    }

    /** 0 at zero, 100 at or above {@code good}, linear between. */
    private int higherIsBetter(BigDecimal value, BigDecimal good) {
        if (value == null) {
            return 50;
        }
        if (value.compareTo(good) >= 0) {
            return 100;
        }
        if (value.signum() <= 0) {
            return 0;
        }
        return value.multiply(HUNDRED).divide(good, 0, RoundingMode.HALF_UP).intValue();
    }

    private List<Warning> buildWarnings(NutrientTotals p) {
        List<Warning> out = new ArrayList<>();
        addBand(out, p.sugarG(), SUGAR_LOW, SUGAR_HIGH, "sugar",
                "This basket is high in sugar (%s g per 100g).",
                "This basket is moderate in sugar (%s g per 100g).");
        addBand(out, p.fatG(), FAT_LOW, FAT_HIGH, "fat",
                "This basket is high in fat (%s g per 100g).",
                "This basket is moderate in fat (%s g per 100g).");
        addBand(out, p.sodiumMg(), SODIUM_LOW, SODIUM_HIGH, "sodium",
                "This basket is high in sodium (%s mg per 100g).",
                "This basket is moderate in sodium (%s mg per 100g).");

        if (p.fiberG() != null && p.fiberG().compareTo(new BigDecimal("3.0")) < 0) {
            out.add(new Warning("INFO", "fiber",
                    "Low in fibre (" + p.fiberG() + " g per 100g). Consider adding pulses, "
                            + "whole grains or leafy vegetables."));
        }
        if (p.proteinG() != null && p.proteinG().compareTo(new BigDecimal("5.0")) < 0) {
            out.add(new Warning("INFO", "protein",
                    "Low in protein (" + p.proteinG() + " g per 100g). Consider adding dal, "
                            + "paneer or nuts."));
        }
        return out;
    }

    private void addBand(List<Warning> out, BigDecimal value, BigDecimal low, BigDecimal high,
                         String nutrient, String highMsg, String cautionMsg) {
        if (value == null) {
            return;
        }
        if (value.compareTo(high) > 0) {
            out.add(new Warning("HIGH", nutrient, String.format(highMsg, value)));
        } else if (value.compareTo(low) > 0) {
            out.add(new Warning("CAUTION", nutrient, String.format(cautionMsg, value)));
        }
    }

    private BigDecimal scale(BigDecimal v, int places) {
        return v == null ? null : v.setScale(places, RoundingMode.HALF_UP);
    }
}

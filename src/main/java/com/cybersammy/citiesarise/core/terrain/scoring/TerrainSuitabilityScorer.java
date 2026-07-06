package com.cybersammy.citiesarise.core.terrain.scoring;

import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TerrainSuitabilityScorer {
    private final List<TerrainSuitabilityRule> rules;

    public TerrainSuitabilityScorer(List<TerrainSuitabilityRule> rules) {
        Objects.requireNonNull(rules, "rules");
        rejectEmptyRules(rules);
        rejectNullRules(rules);
        this.rules = List.copyOf(rules);
    }

    public static TerrainSuitabilityScorer defaultScorer() {
        return new TerrainSuitabilityScorer(List.of(
                new WaterSuitabilityRule(),
                new TerrainCategorySuitabilityRule(),
                new SlopeSuitabilityRule()
        ));
    }

    public TerrainSuitability score(TerrainCell cell, TerrainSuitabilityContext context) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(context, "context");

        double score = 1.0;
        Set<TerrainRejectionReason> rejectionReasons = new HashSet<>();
        List<TerrainSuitabilityStep> steps = new ArrayList<>();

        for (TerrainSuitabilityRule rule : rules) {
            TerrainSuitabilityContribution contribution = rule.evaluate(cell, context);
            score *= contribution.scoreMultiplier();
            contribution.rejectionReason().ifPresent(rejectionReasons::add);
            steps.add(new TerrainSuitabilityStep(rule.name(), contribution.scoreMultiplier(), contribution.rejectionReason()));
        }

        if (!rejectionReasons.isEmpty()) {
            score = 0.0;
        }

        return new TerrainSuitability(score, rejectionReasons, steps);
    }

    private static void rejectEmptyRules(List<TerrainSuitabilityRule> rules) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
    }

    private static void rejectNullRules(List<TerrainSuitabilityRule> rules) {
        for (TerrainSuitabilityRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("rules must not contain null values");
            }
        }
    }
}

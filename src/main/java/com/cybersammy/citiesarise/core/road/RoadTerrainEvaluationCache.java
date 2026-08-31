package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class RoadTerrainEvaluationCache {
    private final List<Context> contexts = new ArrayList<>();

    Context contextFor(RoadRoutingRequest request) {
        Objects.requireNonNull(request, "request");
        for (Context context : contexts) {
            if (context.matches(request)) {
                return context;
            }
        }
        Context context = new Context(
                request.survey(),
                request.maxBuildableSlope(),
                request.terrainAdaptationPlan()
        );
        contexts.add(context);
        return context;
    }

    int size() {
        return contexts.stream().mapToInt(Context::size).sum();
    }

    static final class Context {
        private final TerrainSurvey survey;
        private final double maxBuildableSlope;
        private final TerrainAdaptationPlan terrainAdaptationPlan;
        private final Map<GridBounds, TerrainFootprintEvaluation> evaluations = new HashMap<>();

        private Context(
                TerrainSurvey survey,
                double maxBuildableSlope,
                TerrainAdaptationPlan terrainAdaptationPlan
        ) {
            this.survey = survey;
            this.maxBuildableSlope = maxBuildableSlope;
            this.terrainAdaptationPlan = terrainAdaptationPlan;
        }

        private boolean matches(RoadRoutingRequest request) {
            if (survey != request.survey()) {
                return false;
            }
            if (Double.compare(maxBuildableSlope, request.maxBuildableSlope()) != 0) {
                return false;
            }
            return terrainAdaptationPlan.equals(request.terrainAdaptationPlan());
        }

        TerrainFootprintEvaluation getOrCreate(
                GridBounds footprint,
                Supplier<TerrainFootprintEvaluation> evaluationFactory
        ) {
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(evaluationFactory, "evaluationFactory");
            return evaluations.computeIfAbsent(
                    footprint,
                    ignored -> Objects.requireNonNull(evaluationFactory.get(), "evaluationFactory result")
            );
        }

        private int size() {
            return evaluations.size();
        }
    }
}

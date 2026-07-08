package com.cybersammy.citiesarise.core.transform;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TransformPipelineTest {
    @Test
    void emptyPipelineReturnsInputPlan() {
        SettlementPlan plan = emptyPlan();

        SettlementPlan transformedPlan = TransformPipeline.empty().apply(plan, new TransformContext(42L));

        assertSame(plan, transformedPlan);
    }

    @Test
    void rejectsNullInputs() {
        TransformPipeline pipeline = TransformPipeline.empty();
        SettlementPlan plan = emptyPlan();

        assertThrows(NullPointerException.class, () -> new TransformPipeline(null));
        assertThrows(IllegalArgumentException.class, () -> TransformPipeline.of((PlanTransform) null));
        assertThrows(NullPointerException.class, () -> pipeline.apply(null, new TransformContext(42L)));
        assertThrows(NullPointerException.class, () -> pipeline.apply(plan, null));
    }

    private static SettlementPlan emptyPlan() {
        return new SettlementPlan(
                new PlanElementId("test:settlement"),
                RoadGraph.empty(),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }
}

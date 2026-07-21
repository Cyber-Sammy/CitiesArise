package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteAssessment;
import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteQuality;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SuburbPlanningResultTest {
    @Test
    void rejectsSiteAssessmentWithoutPreparationPlan() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SuburbPlanningResult(
                        Optional.of(emptyPlan()),
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(directAssessment())
                )
        );
    }

    @Test
    void rejectsSiteAssessmentForFailedResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SuburbPlanningResult(
                        Optional.empty(),
                        Optional.of(SuburbPlanningFailureReason.SURVEY_TOO_SMALL),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(directAssessment())
                )
        );
    }

    private static EarthworkSiteAssessment directAssessment() {
        return new EarthworkSiteAssessment(EarthworkSiteQuality.DIRECT, 0, 0, 0, 0, 0, 0);
    }

    private static SettlementPlan emptyPlan() {
        return new SettlementPlan(
                new PlanElementId("settlement/test"),
                RoadGraph.empty(),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }
}

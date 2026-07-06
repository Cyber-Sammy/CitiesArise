package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import org.junit.jupiter.api.Test;

final class SuburbDebugPlanResultTest {
    @Test
    void createsRejectedSummaryFromPlannerResult() {
        SuburbDebugPlanResult result = SuburbDebugPlanResult.from(
                new SettlementRegion(1, -2),
                new GridBounds(new GridPoint(10, 20), new GridSize(40, 30)),
                123L,
                SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL)
        );

        assertFalse(result.successful());
        assertEquals(
                "region=(1, -2), bounds=(10, 20, 40x30), seed=123, rejected=SURVEY_TOO_SMALL",
                result.summary()
        );
    }

    @Test
    void rejectsSuccessfulResultWithoutPlan() {
        assertThrows(IllegalArgumentException.class, () -> new SuburbDebugPlanResult(
                new SettlementRegion(0, 0),
                new GridBounds(new GridPoint(0, 0), new GridSize(40, 30)),
                1L,
                true,
                null,
                null
        ));
    }
}

package com.cybersammy.citiesarise.minecraft.worldgen;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Isolated regression: FULL must arrive in a later native tick using tickets alone. */
@GameTestHolder("cities_arise_completion")
@PrefixGameTestTemplate(false)
public final class CompletionTicketGameTests {
    @GameTest(template="empty",timeoutTicks=200)
    public static void ticketOnlyCompletionReturnsBeforeFullAndEventuallyFinishes(GameTestHelper helper) {
        PlacementReadinessGameTests.boundedCompletionCanFinishOneMissingChunkWithoutLoadingAnotherOwner(helper);
    }
}

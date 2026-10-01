package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.minecraft.placement.*;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileJsonParser;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayableSuburbAcceptanceTest {
    @Test void builtInProfileHandlesRepeatableTerrainFixturesAndPreservesPreparedEntrances() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/cities_arise/settlement_profiles/suburb.json"))).getAsJsonObject();
        var profile = new MinecraftSettlementProfileJsonParser().parse(new SettlementProfileId("cities_arise:suburb"), json);
        var bounds = new GridBounds(new GridPoint(0, 0), profile.surveySize());
        StringBuilder report = new StringBuilder("Synthetic terrain fixtures; timings are not live-world locate benchmarks.\nFixture,Seed,Accepted,PlanningMs,Operations\n");
        for (String fixture : List.of("plains", "rolling", "forest", "shoreline", "ravine")) {
            TerrainSurvey survey = TerrainSurvey.sample(bounds, point -> {
                int height = fixture.equals("rolling") ? 64 + point.x() / 30
                        : fixture.equals("ravine") ? 32 + ((point.x() / 3) % 2) * 32 : 64;
                boolean water = fixture.equals("shoreline") && point.x() < 8;
                return Optional.of(new TerrainCell(point, height, water, fixture.equals("ravine") ? 8 : 0,
                        BiomeCategory.PLAINS, water ? TerrainCategory.BLOCKED : fixture.equals("forest") ? TerrainCategory.ROUGH : TerrainCategory.BUILDABLE));
            });
            var request = new SuburbPlanningRequest(new PlanElementId("test:acceptance"), survey, 42,
                    profile.suburbPlanningSettings(), profile.terrainResponsePolicy());
            long started = System.nanoTime();
            var result = SuburbPlanner.defaults().plan(request);
            double millis = (System.nanoTime() - started) / 1_000_000.0;
            assertEquals(!fixture.equals("ravine"), result.successful(), fixture + ": " + result);
            assertEquals(result, SuburbPlanner.defaults().plan(request), "Determinism: " + fixture);
            int count = 0;
            if (result.successful()) {
                var plan = result.plan().orElseThrow();
                var preparation = result.terrainPreparationPlan().orElseThrow();
                var placed = new DebugPlacementPlanConverter().convert(plan, preparation);
                count = placed.operations().size();
                assertTrue(plan.buildingSlots().stream().allMatch(s -> s.properties().find(PlanPropertyKeys.BUILDING_ASSET).isPresent()));
                for (var transition : preparation.elevationPlan().transitions()) {
                    if (transition.type() != com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType.BUILDING_ACCESS) continue;
                    assertEquals(2, placed.operations().stream().filter(op -> op.sourceElementId().equals(transition.targetZoneId())
                            && op.point().equals(transition.anchor()) && op.role().name().startsWith("DOOR_")).count());
                }
                assertEquals(count, placed.operations().stream().map(DebugBlockPlacementOperation::position).distinct().count());
                assertTrue(placed.operations().stream().anyMatch(o -> o.role() == DebugPlacementRole.BUILDING_CEILING_LIGHT));
            }
            report.append(fixture).append(",42,").append(result.successful()).append(',').append(millis).append(',').append(count).append('\n');
        }
        Path output = Path.of("build/reports/playable-suburb/acceptance.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
    }
}

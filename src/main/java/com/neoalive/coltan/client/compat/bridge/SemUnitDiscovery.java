package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** Fixed catalog of SEM unit entity types Coltan can claim. */
public final class SemUnitDiscovery {
    private static final ResourceLocation GEO =
            new ResourceLocation("coltan", "models/bedrock/unit/unit.geo.json");

    private SemUnitDiscovery() {
    }

    public record Candidate(
            ResourceLocation entityId,
            EntityType<?> entityType,
            String textureFolder,
            String texturePrefix,
            ResourceLocation geo,
            @Nullable ResourceLocation animation
    ) {
    }

    public static List<Candidate> discover(Set<ResourceLocation> excluded) {
        List<Candidate> out = new ArrayList<>(3);
        add(out, excluded, "usunit", "us_unit", "us_unit");
        add(out, excluded, "ruunit", "ru_unit", "ru_unit");
        add(out, excluded, "pmcunit", "pmc_unit", "pmc_unit");
        return out;
    }

    private static void add(List<Candidate> out, Set<ResourceLocation> excluded, String path,
            String textureFolder, String texturePrefix) {
        ResourceLocation entityId = new ResourceLocation("simpleenemymod", path);
        if (excluded.contains(entityId)) {
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        if (type == null) {
            Coltan.LOGGER.warn("SEM unit entity missing from registry: {}", entityId);
            return;
        }
        out.add(new Candidate(entityId, type, textureFolder, texturePrefix, GEO, null));
    }

    public static Set<ResourceLocation> loadExcludeList() {
        Set<ResourceLocation> out = new HashSet<>();
        ResourceLocation id = new ResourceLocation("coltan", "sem_unit_bridge/_exclude.json");
        var optional = Minecraft.getInstance().getResourceManager().getResource(id);
        if (optional.isEmpty()) {
            return out;
        }
        try (var reader = new InputStreamReader(optional.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("exclude")) {
                return out;
            }
            for (JsonElement element : root.getAsJsonArray("exclude")) {
                ResourceLocation excluded = ResourceLocation.tryParse(element.getAsString());
                if (excluded != null) {
                    out.add(excluded);
                }
            }
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read {}", id, e);
        }
        return out;
    }
}

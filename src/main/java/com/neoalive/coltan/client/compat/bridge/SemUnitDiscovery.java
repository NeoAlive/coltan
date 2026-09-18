package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** Fixed catalog of SEM unit entity types Coltan can claim. */
public final class SemUnitDiscovery {
    private SemUnitDiscovery() {
    }

    public static List<SemUnitRigidProfile> discover(Set<ResourceLocation> excluded) {
        List<SemUnitRigidProfile> out = new ArrayList<>(3);
        add(out, excluded, "usunit", "us_unit", "us_unit");
        add(out, excluded, "ruunit", "ru_unit", "ru_unit");
        add(out, excluded, "pmcunit", "pmc_unit", "pmc_unit");
        return out;
    }

    private static void add(List<SemUnitRigidProfile> out, Set<ResourceLocation> excluded, String path,
            String textureFolder, String texturePrefix) {
        ResourceLocation entityId = new ResourceLocation("simpleenemymod", path);
        if (excluded.contains(entityId)) {
            return;
        }
        // getValue() returns the registry's default entry (minecraft:pig) for an unregistered key
        // instead of null — containsKey() is the only reliable existence check (see
        // SbwProjectileDiscovery for the bug this exact pattern caused elsewhere).
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
            Coltan.LOGGER.warn("SEM unit entity missing from registry: {}", entityId);
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        out.add(new SemUnitRigidProfile(entityId, type, textureFolder, texturePrefix));
    }

    public static Set<ResourceLocation> loadExcludeList() {
        Set<ResourceLocation> out = new HashSet<>();
        ResourceLocation id = new ResourceLocation("coltan", "sem_unit_bridge/_exclude.json");
        Optional<Resource> optional = Minecraft.getInstance().getResourceManager().getResource(id);
        if (optional.isEmpty()) {
            return out;
        }
        try (InputStreamReader reader = new InputStreamReader(optional.get().open(), StandardCharsets.UTF_8)) {
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

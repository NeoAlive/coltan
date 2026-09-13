package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Claims entity types that ship a Bedrock projectile geo at
 * {@code models/bedrock/projectile/{path}.geo.json}.
 */
public final class SbwProjectileDiscovery {
    private SbwProjectileDiscovery() {
    }

    public record Candidate(
            ResourceLocation entityId,
            EntityType<?> entityType,
            ResourceLocation geo,
            ResourceLocation texture,
            @Nullable ResourceLocation animation,
            boolean loopAnim,
            boolean hasFlare
    ) {
    }

    public static List<Candidate> discover(Set<ResourceLocation> excluded) {
        List<Candidate> out = new ArrayList<>();
        var resources = Minecraft.getInstance().getResourceManager();

        for (var entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            ResourceLocation entityId = entry.getKey().location();
            if (excluded.contains(entityId)) {
                continue;
            }
            // Piloted vehicles use vehicle/geo paths; skip anything already claimed as a vehicle.
            if (VehicleBridgeCache.profile(entityId) != null) {
                continue;
            }

            String path = entityId.getPath();
            ResourceLocation geo = new ResourceLocation(entityId.getNamespace(),
                    "models/bedrock/projectile/" + path + ".geo.json");
            if (resources.getResource(geo).isEmpty()) {
                continue;
            }

            ResourceLocation texture = new ResourceLocation(entityId.getNamespace(),
                    "textures/bedrock/projectile/" + path + ".png");
            if (resources.getResource(texture).isEmpty()) {
                Coltan.LOGGER.warn("Skipping projectile {}: geo present but texture {} missing",
                        entityId, texture);
                continue;
            }

            ResourceLocation animation = new ResourceLocation(entityId.getNamespace(),
                    "animations/bedrock/projectile/" + path + ".animation.json");
            if (resources.getResource(animation).isEmpty()) {
                animation = null;
            }

            boolean loopAnim = path.equals("swarm_drone");
            boolean hasFlare = geoHasBone(geo, "flare");

            out.add(new Candidate(entityId, entry.getValue(), geo, texture, animation, loopAnim,
                    hasFlare));
        }
        return out;
    }

    private static boolean geoHasBone(ResourceLocation geo, String boneName) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(geo);
        if (resource.isEmpty()) {
            return false;
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return findBoneName(root, boneName);
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not scan {} for bone '{}'", geo, boneName, e);
            return false;
        }
    }

    private static boolean findBoneName(JsonElement element, String boneName) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("name") && obj.get("name").isJsonPrimitive()
                    && boneName.equals(obj.get("name").getAsString())) {
                return true;
            }
            for (var entry : obj.entrySet()) {
                if (findBoneName(entry.getValue(), boneName)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (findBoneName(child, boneName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

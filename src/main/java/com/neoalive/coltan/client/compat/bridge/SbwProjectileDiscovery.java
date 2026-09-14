package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Claims SBW entity types that ship a Bedrock projectile geo under
 * {@code models/bedrock/projectile/{path}.geo.json}. Discovery walks those pack resources
 * (not the whole entity registry) so vanilla skips stay silent.
 */
public final class SbwProjectileDiscovery {
    private static final String GEO_PREFIX = "models/bedrock/projectile/";
    private static final String GEO_SUFFIX = ".geo.json";

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

        Map<ResourceLocation, Resource> geos = resources.listResources(GEO_PREFIX.substring(0, GEO_PREFIX.length() - 1),
                id -> id.getPath().endsWith(GEO_SUFFIX));

        for (ResourceLocation geo : geos.keySet()) {
            // listResources namespace is the pack namespace of the file.
            if (!"superbwarfare".equals(geo.getNamespace())) {
                continue;
            }
            String path = geo.getPath();
            if (!path.startsWith(GEO_PREFIX) || !path.endsWith(GEO_SUFFIX)) {
                continue;
            }
            String entityPath = path.substring(GEO_PREFIX.length(), path.length() - GEO_SUFFIX.length());
            if (entityPath.isEmpty() || entityPath.contains("/")) {
                // Nested paths are not 1:1 with entity registry ids.
                ColtanDebug.once(ColtanDebug.Cat.PROJECTILE, "proj-nested-" + geo,
                        "projectile geo %s skipped — nested path (no entity id mapping)", geo);
                continue;
            }

            ResourceLocation entityId = new ResourceLocation("superbwarfare", entityPath);
            if (excluded.contains(entityId)) {
                ColtanDebug.once(ColtanDebug.Cat.PROJECTILE, "proj-excluded-" + entityId,
                        "projectile %s excluded by bridge list", entityId);
                continue;
            }
            if (VehicleBridgeCache.profile(entityId) != null) {
                ColtanDebug.once(ColtanDebug.Cat.PROJECTILE, "proj-is-vehicle-" + entityId,
                        "projectile geo %s skipped — entity is a bridged vehicle", entityId);
                continue;
            }

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (type == null) {
                ColtanDebug.once(ColtanDebug.Cat.PROJECTILE, "proj-no-type-" + entityId,
                        "projectile geo %s has no EntityType %s", geo, entityId);
                continue;
            }

            ResourceLocation texture = resolveTexture(resources, entityPath);
            if (texture == null) {
                Coltan.LOGGER.warn("Skipping projectile {}: geo present but no texture for {}",
                        entityId, entityPath);
                ColtanDebug.failOnce("proj-no-tex-" + entityId,
                        "projectile %s geo OK but texture missing (tried %s + fallbacks)",
                        entityId, entityPath);
                continue;
            }

            ResourceLocation animation = new ResourceLocation("superbwarfare",
                    "animations/bedrock/projectile/" + entityPath + ".animation.json");
            if (resources.getResource(animation).isEmpty()) {
                animation = null;
            }

            boolean loopAnim = entityPath.equals("swarm_drone");
            boolean hasFlare = geoHasBone(geo, "flare");
            out.add(new Candidate(entityId, type, geo, texture, animation, loopAnim, hasFlare));
            ColtanDebug.once(ColtanDebug.Cat.PROJECTILE, "proj-ok-" + entityId,
                    "projectile claimed %s geo=%s flare=%s anim=%s",
                    entityId, geo, hasFlare, animation != null);
        }

        ColtanDebug.log(ColtanDebug.Cat.PROJECTILE, "discovery scanned %d geo file(s) → %d candidate(s)",
                geos.size(), out.size());
        return out;
    }

    /**
     * SBW sometimes ships display geos as {@code foo_item.geo.json} that reuse {@code foo.png}
     * (see munition BEWLR for ptkm_1r). Try exact path, then strip a trailing {@code _item}.
     */
    @Nullable
    private static ResourceLocation resolveTexture(ResourceManager resources, String entityPath) {
        ResourceLocation exact = new ResourceLocation("superbwarfare",
                "textures/bedrock/projectile/" + entityPath + ".png");
        if (resources.getResource(exact).isPresent()) {
            return exact;
        }
        if (entityPath.endsWith("_item")) {
            String base = entityPath.substring(0, entityPath.length() - "_item".length());
            if (!base.isEmpty()) {
                ResourceLocation fallback = new ResourceLocation("superbwarfare",
                        "textures/bedrock/projectile/" + base + ".png");
                if (resources.getResource(fallback).isPresent()) {
                    ColtanDebug.once(ColtanDebug.Cat.PROJECTILE, "proj-tex-fallback-" + entityPath,
                            "projectile texture %s missing → using %s", exact, fallback);
                    return fallback;
                }
            }
        }
        return null;
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

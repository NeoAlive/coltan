package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neoalive.coltan.Coltan;
import com.wf.gemrender.bedrock.BedrockAnimations;
import com.wf.gemrender.bedrock.BedrockGeometry;
import com.wf.gemrender.bedrock.BedrockImporter;
import com.wf.gemrender.bedrock.BedrockSkeleton;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Skinned Bedrock load with a non-sibling animation file.
 *
 * <p>{@link BedrockImporter#load} only looks for {@code *.animation.json} next to the geo. SBW guns
 * keep clips under {@code animations/}, so we load the mesh then rebuild the record with parsed clips.
 */
public final class GunModelLoader {
    private GunModelLoader() {
    }

    public static GemRenderGltfModel load(ResourceLocation geo, ResourceLocation texture,
            @Nullable ResourceLocation animation) throws Exception {
        GemRenderGltfModel mesh = BedrockImporter.load(geo, texture);
        if (animation == null) {
            return mesh;
        }
        JsonObject clipJson = readJson(animation);
        if (clipJson == null) {
            Coltan.LOGGER.warn("Gun animation missing at {}; rest pose only", animation);
            return mesh;
        }
        JsonObject geoJson = readJson(geo);
        if (geoJson == null) {
            return mesh;
        }
        BedrockSkeleton skeleton = BedrockSkeleton.of(BedrockGeometry.parse(geoJson, geo.toString()));
        Map<String, GltfAnimation> clips =
                BedrockAnimations.parse(clipJson, skeleton, animation.toString());
        if (clips.isEmpty()) {
            return mesh;
        }
        return new GemRenderGltfModel(mesh.model(), mesh.layout(), mesh.bounds(), mesh.morphs(), clips,
                mesh.atlas(), mesh.textures(), mesh.variants());
    }

    @Nullable
    private static JsonObject readJson(ResourceLocation id) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open();
                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            Coltan.LOGGER.warn("Could not read {}", id, e);
            return null;
        }
    }
}

package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wf.gemrender.gltf.GltfMaterial;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.RigidMesh;
import com.wf.gemrender.gltf.skin.VertexSkinning;
import com.wf.gemrender.texture.ModelTextures;
import com.wf.gemrender.texture.SpriteUv;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.model.Model.ConfiguredMesh;
import dev.engine_room.flywheel.lib.model.SimpleModel;

import net.minecraft.resources.ResourceLocation;

/**
 * Rigid Flywheel meshes for SEM unit body parts, built directly from SEM's own vanilla cuboid data
 * ({@code UnitModelDefinitions.createBaseUnitBodyLayer()}) — no Bedrock geometry, no coordinate
 * conversion. All three SEM unit types share this exact geometry; only the skin texture differs.
 */
public final class SemUnitPartMesh {
    public static final String[] PARTS = {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    private static final int TEX_W = 64;
    private static final int TEX_H = 64;

    private static final Map<String, MeshGeometry> GEOMETRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Model[]> MODELS_BY_TEXTURE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> OWNED_TEXTURES = new ConcurrentHashMap<>();

    private SemUnitPartMesh() {
    }

    static {
        GEOMETRY.put("head", build(
                cube(0, 0, -4, -8, -4, 8, 8, 8, 0.0f, false),
                cube(32, 0, -4, -8, -4, 8, 8, 8, 0.5f, false)));
        GEOMETRY.put("body", build(
                cube(16, 16, -4, 0, -2, 8, 12, 4, 0.0f, true),
                cube(16, 33, -4, 0, -2, 8, 12, 4, 0.25f, false)));
        GEOMETRY.put("rightArm", build(
                cube(40, 16, -3, -2, -2, 4, 12, 4, 0.0f, false),
                cube(40, 32, -3, -2, -2, 4, 12, 4, 0.25f, false)));
        GEOMETRY.put("leftArm", build(
                cube(32, 48, -1, -2, -2, 4, 12, 4, 0.0f, false),
                cube(48, 48, -1, -2, -2, 4, 12, 4, 0.25f, false)));
        GEOMETRY.put("rightLeg", build(
                cube(0, 16, -2, 0, -2, 4, 12, 4, 0.0f, false),
                cube(0, 32, -2, 0, -2, 4, 12, 4, 0.25f, false)));
        GEOMETRY.put("leftLeg", build(
                cube(16, 48, -2, 0, -2, 4, 12, 4, 0.0f, false),
                cube(0, 48, -2, 0, -2, 4, 12, 4, 0.25f, false)));
    }

    private record CubeSpec(float texU, float texV, float x, float y, float z, float w, float h, float d,
            float inflate, boolean mirror) {
    }

    private static CubeSpec cube(float texU, float texV, float x, float y, float z, float w, float h, float d,
            float inflate, boolean mirror) {
        return new CubeSpec(texU, texV, x, y, z, w, h, d, inflate, mirror);
    }

    private static MeshGeometry build(CubeSpec... cubes) {
        List<VanillaCuboidMesh.Vertex> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (CubeSpec c : cubes) {
            VanillaCuboidMesh.addBox(verts, indices, c.x(), c.y(), c.z(), c.w(), c.h(), c.d(),
                    c.inflate(), c.mirror(), c.texU(), c.texV(), TEX_W, TEX_H);
        }

        int vertexCount = verts.size();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] texCoords = new float[vertexCount * 2];
        for (int i = 0; i < vertexCount; i++) {
            VanillaCuboidMesh.Vertex v = verts.get(i);
            positions[i * 3] = v.x();
            positions[i * 3 + 1] = v.y();
            positions[i * 3 + 2] = v.z();
            normals[i * 3] = v.nx();
            normals[i * 3 + 1] = v.ny();
            normals[i * 3 + 2] = v.nz();
            texCoords[i * 2] = v.u();
            texCoords[i * 2 + 1] = v.v();
        }
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indexArray.length; i++) {
            indexArray[i] = indices.get(i);
        }

        return MeshGeometry.of(positions, normals, texCoords, indexArray,
                VertexSkinning.rigid(vertexCount, 0), SpriteUv.IDENTITY, 0);
    }

    /** Six part models (order matches {@link #PARTS}) sharing {@code texture}, built on first use. */
    public static Model[] modelsForTexture(ResourceLocation texture) {
        return MODELS_BY_TEXTURE.computeIfAbsent(texture, SemUnitPartMesh::buildModels);
    }

    private static Model[] buildModels(ResourceLocation texture) {
        List<ResourceLocation> owned = new ArrayList<>();
        Material material = new GltfMaterial(ModelTextures.materialTexture(texture, owned),
                GltfMaterial.AlphaMode.MASK, 0.1f, false).toFlywheel();
        if (!owned.isEmpty()) {
            OWNED_TEXTURES.put(texture, owned.get(0));
        }

        Model[] models = new Model[PARTS.length];
        for (int i = 0; i < PARTS.length; i++) {
            RigidMesh mesh = new RigidMesh(GEOMETRY.get(PARTS[i]));
            models[i] = new SimpleModel(List.of(new ConfiguredMesh(material, mesh)));
        }
        return models;
    }

    /** Drops cached per-texture models/materials (e.g. on resource reload); geometry itself is static. */
    public static void clearTextureCache() {
        MODELS_BY_TEXTURE.clear();
        for (ResourceLocation owned : OWNED_TEXTURES.values()) {
            ModelTextures.release(owned);
        }
        OWNED_TEXTURES.clear();
    }
}

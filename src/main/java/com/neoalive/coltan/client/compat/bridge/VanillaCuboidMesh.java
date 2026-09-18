package com.neoalive.coltan.client.compat.bridge;

import java.util.List;

/**
 * Reproduces vanilla {@code ModelPart.Cube}/{@code Polygon}'s box-UV layout exactly (same corner
 * order, same UV unwrap, same mirror handling), so a mesh built from this matches what SEM's own
 * cuboids render as, vertex for vertex — no coordinate system, no format to get wrong.
 */
final class VanillaCuboidMesh {
    private VanillaCuboidMesh() {
    }

    record Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
    }

    /** Same parameter shape as {@code CubeListBuilder.addBox}, minus per-axis inflate (SEM never uses it). */
    static void addBox(List<Vertex> verts, List<Integer> indices,
            float originX, float originY, float originZ,
            float sizeX, float sizeY, float sizeZ,
            float inflate, boolean mirror,
            float texU, float texV, float texWidth, float texHeight) {
        float x0 = (originX - inflate) / 16.0f;
        float x1 = (originX + sizeX + inflate) / 16.0f;
        float y0 = (originY - inflate) / 16.0f;
        float y1 = (originY + sizeY + inflate) / 16.0f;
        float z0 = (originZ - inflate) / 16.0f;
        float z1 = (originZ + sizeZ + inflate) / 16.0f;
        if (mirror) {
            float t = x0;
            x0 = x1;
            x1 = t;
        }

        float[] c000 = {x0, y0, z0};
        float[] c100 = {x1, y0, z0};
        float[] c110 = {x1, y1, z0};
        float[] c010 = {x0, y1, z0};
        float[] c001 = {x0, y0, z1};
        float[] c101 = {x1, y0, z1};
        float[] c111 = {x1, y1, z1};
        float[] c011 = {x0, y1, z1};

        float f4 = texU;
        float f5 = texU + sizeZ;
        float f6 = texU + sizeZ + sizeX;
        float f7 = texU + sizeZ + sizeX + sizeX;
        float f8 = texU + sizeZ + sizeX + sizeZ;
        float f9 = texU + sizeZ + sizeX + sizeZ + sizeX;
        float f10 = texV;
        float f11 = texV + sizeZ;
        float f12 = texV + sizeZ + sizeY;

        // DOWN, UP, WEST, NORTH, EAST, SOUTH — same face order/winding/UV rects as ModelPart.Cube.
        addFace(verts, indices, new float[][]{c101, c001, c000, c100}, 0, -1, 0, f5, f10, f6, f11, texWidth, texHeight, mirror);
        addFace(verts, indices, new float[][]{c110, c010, c011, c111}, 0, 1, 0, f6, f11, f7, f10, texWidth, texHeight, mirror);
        addFace(verts, indices, new float[][]{c000, c001, c011, c010}, -1, 0, 0, f4, f11, f5, f12, texWidth, texHeight, mirror);
        addFace(verts, indices, new float[][]{c100, c000, c010, c110}, 0, 0, -1, f5, f11, f6, f12, texWidth, texHeight, mirror);
        addFace(verts, indices, new float[][]{c101, c100, c110, c111}, 1, 0, 0, f6, f11, f8, f12, texWidth, texHeight, mirror);
        addFace(verts, indices, new float[][]{c001, c101, c111, c011}, 0, 0, 1, f8, f11, f9, f12, texWidth, texHeight, mirror);
    }

    private static void addFace(List<Vertex> verts, List<Integer> indices, float[][] corners,
            float nx, float ny, float nz, float u0, float v0, float u1, float v1,
            float texWidth, float texHeight, boolean mirror) {
        float[] u = {u1 / texWidth, u0 / texWidth, u0 / texWidth, u1 / texWidth};
        float[] v = {v0 / texHeight, v0 / texHeight, v1 / texHeight, v1 / texHeight};
        float[][] c = corners;
        if (mirror) {
            c = new float[][]{corners[3], corners[2], corners[1], corners[0]};
            u = new float[]{u[3], u[2], u[1], u[0]};
            v = new float[]{v[3], v[2], v[1], v[0]};
            nx = -nx;
        }

        int base = verts.size();
        for (int i = 0; i < 4; i++) {
            verts.add(new Vertex(c[i][0], c[i][1], c[i][2], nx, ny, nz, u[i], v[i]));
        }
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }
}

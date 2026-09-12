package com.neoalive.coltan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.wf.gemrender.bedrock.BedrockCubes;
import com.wf.gemrender.bedrock.BedrockPolyMesh;

/** Flips Bedrock poly_mesh V so cutout shells are not invisible. */
@Mixin(value = BedrockPolyMesh.class, remap = false)
public class BedrockPolyMeshMixin {
    @Redirect(
            method = "add",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wf/gemrender/bedrock/BedrockCubes;emitVertex(FFFFFFFF)I",
                    remap = false
            )
    )
    private static int coltan$flipBedrockUv(BedrockCubes cubes, float x, float y, float z, float nx, float ny,
                                            float nz, float u, float v) {
        return cubes.emitVertex(x, y, z, nx, ny, nz, u, 1.0f - v);
    }
}

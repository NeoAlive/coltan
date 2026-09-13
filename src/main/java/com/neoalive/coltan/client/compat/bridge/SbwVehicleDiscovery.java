package com.neoalive.coltan.client.compat.bridge;

import java.util.ArrayList;
import java.util.List;

import com.atsuishio.superbwarfare.data.CustomData;
import com.atsuishio.superbwarfare.resource.ModelResource;
import com.atsuishio.superbwarfare.resource.vehicle.DefaultVehicleResource;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleModelPojo;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource;
import com.neoalive.coltan.Coltan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** Lists SBW vehicle visuals from CustomData.VEHICLE_RESOURCE. */
public final class SbwVehicleDiscovery {
    private SbwVehicleDiscovery() {
    }

    public record Candidate(ResourceLocation entityId, EntityType<?> entityType, ResourceLocation geo,
                            ResourceLocation texture, ResourceLocation animation) {
    }

    public static List<Candidate> discover() {
        List<Candidate> out = new ArrayList<>();
        for (String key : CustomData.VEHICLE_RESOURCE.keySet()) {
            ResourceLocation entityId = ResourceLocation.tryParse(key);
            if (entityId == null) {
                continue;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (type == null) {
                continue;
            }

            DefaultVehicleResource res = VehicleResource.getDefault(key);
            ResourceLocation geo = null;
            ResourceLocation texture = null;
            for (VehicleModelPojo pojo : res.getModels()) {
                if (pojo.distance == 0 && pojo.model != null) {
                    geo = pojo.model;
                    texture = pojo.texture;
                    break;
                }
            }
            if (geo == null) {
                for (VehicleModelPojo pojo : res.getModels()) {
                    if (pojo.model != null) {
                        geo = pojo.model;
                        texture = pojo.texture;
                        break;
                    }
                }
            }
            // Legacy / nested Model (e.g. drone.json): fall back to deprecated getModel().
            ResourceLocation animation = res.getAnimation();
            if (geo == null) {
                ModelResource nested = res.getModel();
                if (nested != null && nested.model != null) {
                    geo = nested.model;
                    texture = nested.texture;
                    if (animation == null) {
                        animation = nested.animation;
                    }
                }
            }
            if (geo == null) {
                Coltan.LOGGER.warn("Skipping {}: no model path in vehicle resource", key);
                continue;
            }

            out.add(new Candidate(entityId, type, geo, texture, animation));
        }
        return out;
    }
}

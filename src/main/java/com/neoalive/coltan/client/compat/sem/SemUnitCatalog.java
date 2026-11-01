package com.neoalive.coltan.client.compat.sem;

import com.neoalive.coltan.Coltan;
import com.neoalive.coltan.debug.ColtanDebug;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Meshes are built in code and cached per texture by SemUnitPartMesh. Nothing async to load. */
public final class SemUnitCatalog {
    private static final Map<ResourceLocation, SemUnitRigidProfile> PROFILES =
            new LinkedHashMap<>();
    private static final Map<EntityType<?>, SemUnitRigidProfile> BY_TYPE = new LinkedHashMap<>();

    private SemUnitCatalog() {}

    public static synchronized void rebuild() {
        PROFILES.clear();
        BY_TYPE.clear();
        Set<ResourceLocation> excluded = SemUnitDiscovery.loadExcludeList();
        for (SemUnitRigidProfile profile : SemUnitDiscovery.discover(excluded)) {
            PROFILES.put(profile.entityId(), profile);
            BY_TYPE.put(profile.entityType(), profile);
        }
        Coltan.LOGGER.info(
                "Coltan SEM unit bridge: {} profile(s), {} excluded id(s)",
                PROFILES.size(),
                excluded.size());
        ColtanDebug.log(
                ColtanDebug.Cat.BOOT,
                "SEM unit catalog ready profiles=%d excluded=%d",
                PROFILES.size(),
                excluded.size());
    }

    public static Collection<SemUnitRigidProfile> profiles() {
        return Collections.unmodifiableCollection(PROFILES.values());
    }

    @Nullable
    public static SemUnitRigidProfile profile(EntityType<?> type) {
        return BY_TYPE.get(type);
    }
}

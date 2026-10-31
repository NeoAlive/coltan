package com.neoalive.coltan.mixin.gemrender;

import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

/**
 * Gate for mixins into GemRender internals ({@code coltan.gemrender.mixins.json}). They patch
 * private implementation, not API, so they apply only to the GemRender line they were written
 * against: present, and {@code [0.1.20, 0.2)}. Anything else leaves GemRender untouched.
 */
public final class ColtanGemRenderMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Coltan");
    private static final ArtifactVersion MIN = new DefaultArtifactVersion("0.1.20");
    private static final ArtifactVersion BELOW = new DefaultArtifactVersion("0.2");

    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        apply = false;
        try {
            LoadingModList mods = LoadingModList.get();
            ModFileInfo file = mods == null ? null : mods.getModFileById("gemrender");
            if (file == null || file.getMods().isEmpty()) {
                LOGGER.info("Coltan: GemRender absent, GemRender internal patches skipped");
                return;
            }
            ArtifactVersion version = file.getMods().get(0).getVersion();
            apply = version.compareTo(MIN) >= 0 && version.compareTo(BELOW) < 0;
            LOGGER.info("Coltan: GemRender {} -> internal patches {}", version, apply ? "on" : "off (untested line)");
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Coltan: could not read GemRender version, internal patches skipped: {}", e.toString());
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return apply;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

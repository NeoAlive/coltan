package com.neoalive.coltan.client.compat.sbw.armor;

import com.atsuishio.superbwarfare.resource.model.ArmorModelReloadListener;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BedrockArmorInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.neoalive.coltan.Coltan;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Baked twin of each SBW armor model. SBW only keeps the tree model, so the geo file it loaded is
 * read again and baked with the armor bones kept. Keyed by the tree model, so a resource reload
 * starts over. Render thread only.
 */
public final class BakedSbmArmor {
    private static final Map<TreeBedrockModel, Optional<BedrockArmorInstance>> INSTANCES =
            new WeakHashMap<>();

    private BakedSbmArmor() {}

    @Nullable
    public static BedrockArmorInstance instance(@Nullable TreeBedrockModel tree) {
        if (tree == null) {
            return null;
        }
        return INSTANCES.computeIfAbsent(tree, BakedSbmArmor::bake).orElse(null);
    }

    private static Optional<BedrockArmorInstance> bake(TreeBedrockModel tree) {
        ResourceLocation geo = geoOf(tree);
        if (geo == null) {
            return Optional.empty();
        }
        try (InputStreamReader reader =
                new InputStreamReader(
                        Minecraft.getInstance().getResourceManager().open(geo),
                        StandardCharsets.UTF_8)) {
            BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO.class);
            return Optional.of(
                    new BedrockArmorInstance(BakedBedrockModel.bake(pojo, BakerOptions.ARMOR)));
        } catch (Exception e) {
            Coltan.LOGGER.warn(
                    "Coltan baked armor unavailable for {}, keeping the tree path", geo, e);
            return Optional.empty();
        }
    }

    @Nullable
    private static ResourceLocation geoOf(TreeBedrockModel tree) {
        for (Map.Entry<ResourceLocation, TreeBedrockModel> entry :
                ArmorModelReloadListener.INSTANCE.getModels().entrySet()) {
            if (entry.getValue() == tree) {
                return entry.getKey();
            }
        }
        return null;
    }
}

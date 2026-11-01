package com.neoalive.coltan.client.compat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;

/**
 * Hidden, always-on client pack serving files Coltan generates in memory, so published GemRender
 * importers can read them through the ResourceManager. Holds no SBW or GemRender types.
 */
public final class ColtanBridgePack implements PackResources {
    public static final String ID = "coltan_bridge";
    public static final String NAMESPACE = "coltan";

    private static volatile Map<ResourceLocation, byte[]> files = Map.of();

    private ColtanBridgePack() {}

    /** Replace every served file. Call before anything imports the new ids. */
    public static void publish(Map<ResourceLocation, byte[]> next) {
        files = Map.copyOf(next);
    }

    public static boolean serves(ResourceLocation id) {
        return files.containsKey(id);
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(
                consumer -> {
                    int format =
                            SharedConstants.getCurrentVersion()
                                    .getPackVersion(PackType.CLIENT_RESOURCES);
                    Pack.Info info =
                            new Pack.Info(
                                    Component.literal("Coltan bridge"),
                                    format,
                                    format,
                                    FeatureFlagSet.of(),
                                    true);
                    consumer.accept(
                            Pack.create(
                                    ID,
                                    Component.literal("Coltan bridge"),
                                    true,
                                    id -> new ColtanBridgePack(),
                                    info,
                                    PackType.CLIENT_RESOURCES,
                                    Pack.Position.TOP,
                                    true,
                                    PackSource.BUILT_IN));
                });
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        byte[] bytes = files.get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !NAMESPACE.equals(namespace)) {
            return;
        }
        for (Map.Entry<ResourceLocation, byte[]> entry : files.entrySet()) {
            if (entry.getKey().getPath().startsWith(path)) {
                byte[] bytes = entry.getValue();
                output.accept(entry.getKey(), () -> new ByteArrayInputStream(bytes));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(NAMESPACE) : Set.of();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer == PackMetadataSection.TYPE) {
            return (T)
                    new PackMetadataSection(
                            Component.literal("Coltan bridge"),
                            SharedConstants.getCurrentVersion()
                                    .getPackVersion(PackType.CLIENT_RESOURCES));
        }
        return null;
    }

    @Override
    public String packId() {
        return ID;
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public void close() {}
}

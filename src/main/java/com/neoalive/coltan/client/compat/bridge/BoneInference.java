package com.neoalive.coltan.client.compat.bridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atsuishio.superbwarfare.data.CustomData;
import com.atsuishio.superbwarfare.data.gun.DefaultGunData;
import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Infers GemRender gameplay bones, bound-weapon bones, and fire clip pairs from SBW assets.
 */
public final class BoneInference {
    private static final Pattern WHEEL = Pattern.compile("^wheel[LR].*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern W_WHEEL = Pattern.compile("^w_[lLrR].*$");
    private static final Pattern TRACK = Pattern.compile("^track(Mov|Rot)[LR]\\d+$");
    private static final Pattern FLARE = Pattern.compile("^flare.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVE = Pattern.compile("^move_.*");
    private static final Set<String> FIXED = Set.of("turret", "barrel", "barrel2", "base", "root",
            "passengerWeaponStation", "passengerWeaponStationYaw", "passengerWeaponStationPitch");

    private BoneInference() {
    }

    public static Set<String> gameplayBones(ResourceLocation geo, ResourceLocation entityId) {
        Set<String> bones = new LinkedHashSet<>();
        JsonObject root = readJson(geo);
        if (root == null) {
            return bones;
        }
        Set<String> present = new LinkedHashSet<>(boneNames(root));
        for (String name : present) {
            if (FIXED.contains(name) || WHEEL.matcher(name).matches() || W_WHEEL.matcher(name).matches()
                    || TRACK.matcher(name).matches() || FLARE.matcher(name).matches()
                    || MOVE.matcher(name).matches()) {
                bones.add(name);
            }
        }

        BoundBoneLists bound = boundBones(entityId);
        for (String name : bound.both()) {
            if (present.contains(name)) {
                bones.add(name);
            }
        }
        for (String name : bound.yaw()) {
            if (present.contains(name)) {
                bones.add(name);
            }
        }
        for (String name : bound.pitch()) {
            if (present.contains(name)) {
                bones.add(name);
            }
        }
        return bones;
    }

    public static BoundBoneLists boundBones(ResourceLocation entityId) {
        List<String> both = new ArrayList<>();
        List<String> yaw = new ArrayList<>();
        List<String> pitch = new ArrayList<>();
        if (entityId == null) {
            return new BoundBoneLists(both, yaw, pitch);
        }

        DefaultVehicleData data = CustomData.VEHICLE_DATA.get(entityId.toString());
        if (data == null) {
            return new BoundBoneLists(both, yaw, pitch);
        }

        try {
            for (DefaultGunData gun : data.weapons().values()) {
                addAll(both, gun.getBoundBones());
                addAll(yaw, gun.getBoundBonesYaw());
                addAll(pitch, gun.getBoundBonesPitch());
            }
        } catch (Exception ignored) {
            // Weapon decode can fail for incomplete datapacks; geo patterns still apply.
        }
        return new BoundBoneLists(List.copyOf(both), List.copyOf(yaw), List.copyOf(pitch));
    }

    private static void addAll(List<String> into, Iterable<String> from) {
        if (from == null) {
            return;
        }
        for (String name : from) {
            if (name != null && !name.isBlank()) {
                into.add(name);
            }
        }
    }

    public static List<VehicleBridgeProfile.FireClip> fireClips(ResourceLocation animation) {
        List<VehicleBridgeProfile.FireClip> out = new ArrayList<>();
        if (animation == null) {
            return out;
        }
        JsonObject root = readJson(animation);
        if (root == null || !root.has("animations")) {
            return out;
        }
        JsonObject animations = root.getAsJsonObject("animations");
        Set<String> names = animations.keySet();
        Set<String> weapons = new LinkedHashSet<>();
        for (String name : names) {
            Matcher fire = Pattern.compile("^animation\\.(.+)\\.fire(?:\\.\\d+)?$").matcher(name);
            if (fire.matches()) {
                weapons.add(fire.group(1));
            }
        }
        for (String weapon : weapons) {
            String idle = "animation." + weapon + ".idle";
            String fire = "animation." + weapon + ".fire";
            if (!names.contains(fire)) {
                continue;
            }
            out.add(new VehicleBridgeProfile.FireClip(weapon, names.contains(idle) ? idle : null, fire));
        }
        return out;
    }

    public static List<String> boneNames(JsonObject root) {
        List<String> names = new ArrayList<>();
        JsonObject geometry = firstGeometry(root);
        if (geometry == null) {
            return names;
        }
        for (JsonElement element : array(geometry, "bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (bone.has("name")) {
                names.add(bone.get("name").getAsString());
            }
        }
        return names;
    }

    private static JsonObject firstGeometry(JsonObject root) {
        if (root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray()) {
            JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
            if (!geometries.isEmpty() && geometries.get(0).isJsonObject()) {
                return geometries.get(0).getAsJsonObject();
            }
        }
        for (var entry : root.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).startsWith("geometry.") && entry.getValue().isJsonObject()) {
                return entry.getValue().getAsJsonObject();
            }
        }
        return null;
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || !value.isJsonArray() ? new JsonArray() : value.getAsJsonArray();
    }

    private static JsonObject readJson(ResourceLocation location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (var in = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    public record BoundBoneLists(List<String> both, List<String> yaw, List<String> pitch) {
    }
}

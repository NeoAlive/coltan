package com.neoalive.coltan.client.compat.sbw.fx;

import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Reflective reads of vanilla particle state by SRG name (ObfuscationReflectionHelper maps them in
 * dev), and of SBW's own private fields, which are not obfuscated. Used once per spawn.
 */
final class FxFields {
    final Field xo = base("f_107209_");
    final Field yo = base("f_107210_");
    final Field zo = base("f_107211_");
    final Field x = base("f_107212_");
    final Field y = base("f_107213_");
    final Field z = base("f_107214_");
    final Field xd = base("f_107215_");
    final Field yd = base("f_107216_");
    final Field zd = base("f_107217_");
    final Field onGround = base("f_107218_");
    final Field hasPhysics = base("f_107219_");
    final Field stopped = base("f_107205_");
    final Field removed = base("f_107220_");
    final Field bbWidth = base("f_107221_");
    final Field bbHeight = base("f_107222_");
    final Field age = base("f_107224_");
    final Field lifetime = base("f_107225_");
    final Field gravity = base("f_107226_");
    final Field rCol = base("f_107227_");
    final Field gCol = base("f_107228_");
    final Field bCol = base("f_107229_");
    final Field alpha = base("f_107230_");
    final Field roll = base("f_107231_");
    final Field oRoll = base("f_107204_");
    final Field friction = base("f_172258_");
    final Field quadSize =
            ObfuscationReflectionHelper.findField(SingleQuadParticle.class, "f_107663_");
    final Field sprite =
            ObfuscationReflectionHelper.findField(TextureSheetParticle.class, "f_108321_");

    private static Field base(String srg) {
        return ObfuscationReflectionHelper.findField(Particle.class, srg);
    }

    /** A private field of an SBW particle class, or null when that version lacks it. */
    @Nullable
    static Field own(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}

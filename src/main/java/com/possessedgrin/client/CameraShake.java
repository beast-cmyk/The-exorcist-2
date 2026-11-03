package com.possessedgrin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;

/** Your screen shaking when you use a power, get burned by holy water, or start a séance. Fades out smoothly. */
public final class CameraShake {
    private static float strength;
    private static long startTick;
    private static int duration;

    private CameraShake() {
    }

    static void start(float newStrength, int ticks, long now) {
        if (newStrength >= remaining(now)) {
            strength = newStrength;
            startTick = now;
            duration = Math.max(1, ticks);
        }
    }

    private static float remaining(long now) {
        float t = now - startTick;
        return t >= duration ? 0.0F : strength * (1.0F - t / duration);
    }

    public static void apply(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || duration == 0) {
            return;
        }
        float time = client.world.getTime() - startTick + tickDelta;
        if (time < 0.0F || time >= duration) {
            return;
        }
        float amount = strength * (1.0F - time / duration);
        matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(MathHelper.sin(time * 2.7F) * 2.2F * amount));
        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(MathHelper.cos(time * 3.4F) * 1.3F * amount));
    }
}

package ladysnake.requiem.api.v1.entity.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface MobAbility<E extends LivingEntity> {
    default void update() {
    }

    default float getCooldownProgress() {
        return 1.0F;
    }

    default void writeToPacket(PacketByteBuf buf) {
    }

    default void readFromPacket(PacketByteBuf buf) {
    }
}

package ladysnake.requiem.api.v1.event;

import net.minecraft.util.Identifier;

/**
 * COMPILE-ONLY STAND-IN for Requiem's real class (2.0.0-beta.16). Left out of the jar (see build.gradle).
 * The real one keeps handlers in a map keyed by id, so registering an existing id replaces that handler.
 */
public class IdentifyingEvent<T> {
    public void register(Identifier id, T listener) {
        throw new AssertionError("compile-time stub");
    }
}

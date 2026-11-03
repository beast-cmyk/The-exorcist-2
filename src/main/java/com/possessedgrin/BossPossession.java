package com.possessedgrin;

import com.possessedgrin.haunt.Haunting;
import ladysnake.requiem.api.v1.event.requiem.PossessionStartCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

/**
 * Lets players possess bosses.
 *
 * <p>Requiem refuses bosses through one possession rule registered as {@code requiem:blacklist}. Requiem keeps its
 * possession rules in a map keyed by id, so registering our own rule under that same id replaces it. The
 * replacement still blocks everything Requiem blocks (ghosts, morticians...) except the mobs in
 * {@code possessedgrin:possessable_bosses}, and a second rule then explicitly allows those bosses.
 *
 * <p>Pandemonium's optional "possess all mobs" rule keeps working alongside this.
 */
public final class BossPossession {
    private static final Identifier REQUIEM_BLACKLIST_RULE = new Identifier("requiem", "blacklist");
    private static final Identifier ALLOW_BOSSES_RULE = PossessedGrin.id("allow_bosses");
    private static final Identifier SEANCE_RULE = PossessedGrin.id("seance");

    private BossPossession() {
    }

    /** Safe to call more than once: each call just re-registers the same rules. */
    public static void install() {
        // During an Ouija séance, any creature accepts you (except the ones Requiem never allows, like ghosts)
        PossessionStartCallback.EVENT.register(SEANCE_RULE, (target, possessor, simulate) ->
                Haunting.inSeance(possessor) ? PossessionStartCallback.Result.ALLOW : PossessionStartCallback.Result.PASS);
        PossessionStartCallback.EVENT.register(REQUIEM_BLACKLIST_RULE, (target, possessor, simulate) -> {
            EntityType<?> type = target.getType();
            if (type.isIn(PossessedGrin.REQUIEM_POSSESSION_BLACKLIST) && !type.isIn(PossessedGrin.POSSESSABLE_BOSSES)) {
                return PossessionStartCallback.Result.DENY;
            }
            return PossessionStartCallback.Result.PASS;
        });
        PossessionStartCallback.EVENT.register(ALLOW_BOSSES_RULE, (target, possessor, simulate) ->
                target.getType().isIn(PossessedGrin.POSSESSABLE_BOSSES)
                        ? PossessionStartCallback.Result.ALLOW
                        : PossessionStartCallback.Result.PASS);
    }
}

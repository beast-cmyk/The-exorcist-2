package com.possessedgrin.power;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.mixin.MobEntityAccessor;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Facts;
import com.possessedgrin.voice.Kinship;
import com.possessedgrin.voice.Persona;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.AttackGoal;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.ai.goal.EatGrassGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.EscapeSunlightGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.PounceAtTargetGoal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * True telepathy: what a creature is thinking, in its own words, built from what its mind is really doing right
 * now: the goals it's running, its brain's current activity and memories, who it's hunting or fleeing, its
 * wounds, its loves, its fear of you, even what it thinks of you.
 */
public final class MindReader {
    private static final int MAX_THOUGHTS = 6;

    private MindReader() {
    }

    public static List<String> thoughts(ServerPlayerEntity reader, @Nullable MobEntity host, LivingEntity creature) {
        Set<String> thoughts = new LinkedHashSet<>();
        Random random = creature.getRandom();
        Persona persona = Persona.of(creature);
        String hostKind = host != null ? PossessedGrin.kind(host) : "thing";

        if (creature.isSleeping()) {
            thoughts.add("...asleep... dreaming of " + Conversation.fill(persona.line(Persona.Topic.DREAM, random), creature, host, reader) + "...");
            thoughts.add("It has no idea you're inside its head.");
            return new ArrayList<>(thoughts);
        }

        // Who it's after
        LivingEntity target = creature instanceof MobEntity mob ? mob.getTarget() : null;
        if (target != null) {
            thoughts.add(target == host || target == reader
                    ? "The " + hostKind + ". Kill the " + hostKind + ". KILL IT."
                    : "There. The " + PossessedGrin.kind(target) + ". Get it. Don't let it get away.");
        }

        // What it's doing, goal by goal
        if (creature instanceof MobEntity mob) {
            for (PrioritizedGoal running : running(mob)) {
                String thought = goalThought(running.getGoal(), mob, host, reader, target);
                if (thought != null) {
                    thoughts.add(thought);
                }
            }
        }

        // What its brain is busy with (villagers, piglins, axolotls, wardens...)
        Brain<?> brain = creature.getBrain();
        Optional<Activity> activity = brain.getFirstPossibleNonCoreActivity();
        activity.map(a -> activityThought(a.getId(), creature)).ifPresent(thoughts::add);

        // Fear of you
        if (host != null && target != host && creature != host && creature.squaredDistanceTo(host) < 144.0 && creature.canSee(host)) {
            if (creature instanceof MobEntity mob && Kinship.isKin(mob, host)) {
                thoughts.add("That's one of us... but something is wearing it. Its eyes. Oh, its eyes.");
            } else if (!persona.fighter()) {
                thoughts.add("That " + hostKind + ". It's wrong. Don't look at it. Don't let it see you looking.");
            } else if (persona != Persona.BOSS) {
                thoughts.add("That " + hostKind + " smells like burning. Even I don't want to go near it.");
            }
        }

        // Who hurt it, who it's angry at
        LivingEntity attacker = creature.getAttacker();
        if (attacker != null && attacker != target) {
            thoughts.add(attacker == host || attacker == reader
                    ? "It hurt me. The " + hostKind + " hurt me. Why would it do that?"
                    : "The " + PossessedGrin.kind(attacker) + " hurt me. I won't forget.");
        }
        Entity angry = Facts.angryAt(creature);
        if (angry != null && angry != target && angry != attacker) {
            thoughts.add("Still furious at " + (angry == host || angry == reader ? "the " + hostKind : PossessedGrin.the(angry)) + ".");
        }

        // Memories of places
        BlockPos home = Facts.remembered(creature, MemoryModuleType.HOME);
        if (home != null) {
            thoughts.add("Home. My bed, " + Facts.direction(creature.getPos(), home) + ". I want to be home.");
        }
        BlockPos job = Facts.remembered(creature, MemoryModuleType.JOB_SITE);
        if (job != null && creature instanceof VillagerEntity villager) {
            thoughts.add("My " + villager.getVillagerData().getProfession().id().replace('_', ' ') + "'s workstation, "
                    + Facts.direction(creature.getPos(), job) + ". There's work waiting.");
        }
        if (brain.hasMemoryModule(MemoryModuleType.GOLEM_DETECTED_RECENTLY)) {
            thoughts.add("The iron golem is near. Good. It keeps us safe. ...Does it keep us safe from THAT?");
        }
        if (brain.hasMemoryModule(MemoryModuleType.ADMIRING_ITEM)) {
            thoughts.add("Shiny... shiny... mine. MINE.");
        }

        // Body and heart
        if (creature.getHealth() < creature.getMaxHealth() * 0.35F) {
            thoughts.add("It hurts. Everything hurts. Am I dying? I don't want to die.");
        }
        if (creature instanceof AnimalEntity animal && animal.isInLove()) {
            thoughts.add("Love! There, that one! I feel love!");
        }
        if (creature.isBaby()) {
            thoughts.add("I'm small. Where's mother? I want my mother.");
        }
        if (creature instanceof TameableEntity pet && pet.isTamed() && pet.getOwner() != null) {
            thoughts.add(pet.getOwner().getName().getString() + ". Where's " + pet.getOwner().getName().getString()
                    + "? I'd do anything for " + pet.getOwner().getName().getString() + ".");
        }
        if (creature instanceof MerchantEntity merchant && merchant.getCustomer() != null) {
            thoughts.add("Haggling with " + merchant.getCustomer().getName().getString() + ". I'll squeeze one more emerald out of them.");
        }
        if (creature instanceof VillagerEntity villager) {
            int reputation = villager.getReputation(reader);
            String name = reader.getName().getString();
            if (reputation >= 20) {
                thoughts.add(name + " has been good to us. So why does " + name + " feel so cold today?");
            } else if (reputation <= -20) {
                thoughts.add(name + "... the one who hurt us. I'll never forgive " + name + ".");
            }
        }

        // The world around it
        if (Facts.hasSky(creature.world)) {
            boolean outside = creature.world.isSkyVisible(creature.getBlockPos());
            if (creature.world.isThundering()) {
                thoughts.add("Thunder. I hate thunder.");
            } else if (creature.world.isRaining() && outside && persona == Persona.ENDERMAN) {
                thoughts.add("Rain. Burning rain. Get out of it, get out of it!");
            }
            if (!creature.world.isDay() && persona == Persona.VILLAGER && home != null && creature.squaredDistanceTo(home.getX(), home.getY(), home.getZ()) > 64.0) {
                thoughts.add("It's dark. I should be in bed. Why am I still out here?");
            }
            if (creature.world.isDay() && outside && (persona == Persona.UNDEAD || persona == Persona.SKELETON)) {
                thoughts.add("The sun. It's burning me. Shade. Find shade.");
            }
        }

        if (thoughts.isEmpty()) {
            thoughts.add(Conversation.fill(persona.line(Persona.Topic.IDLE, random), creature, host, reader));
        }
        List<String> list = new ArrayList<>(thoughts);
        return list.size() > MAX_THOUGHTS ? list.subList(0, MAX_THOUGHTS) : list;
    }

    private static List<PrioritizedGoal> running(MobEntity mob) {
        List<PrioritizedGoal> goals = new ArrayList<>();
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            accessor.possessedgrin$getTargetSelector().getRunningGoals().forEach(goals::add);
            accessor.possessedgrin$getGoalSelector().getRunningGoals().forEach(goals::add);
        } catch (RuntimeException ignored) {
            // a modded mob with an unusual goal setup: read what we can elsewhere
        }
        return goals;
    }

    @Nullable
    private static String goalThought(Goal goal, MobEntity mob, @Nullable MobEntity host, PlayerEntity reader, @Nullable LivingEntity target) {
        String them = target == null ? "it" : target == host || target == reader ? "the possessed one" : "the " + PossessedGrin.kind(target);
        if (goal instanceof CreeperIgniteGoal) {
            return "Closer... closer... let it ALL out...";
        }
        if (goal instanceof BowAttackGoal<?> || goal instanceof CrossbowAttackGoal<?> || goal instanceof ProjectileAttackGoal) {
            return "Draw. Aim at " + them + ". Hold... hold... loose.";
        }
        if (goal instanceof MeleeAttackGoal || goal instanceof AttackGoal) {
            return "Close in on " + them + ". Hit it. Hit it again.";
        }
        if (goal instanceof PounceAtTargetGoal) {
            return "Crouch... coil... and LEAP.";
        }
        if (goal instanceof BreakDoorGoal) {
            return "The door. Break the door. They're hiding inside.";
        }
        if (goal instanceof FleeEntityGoal<?> || goal instanceof EscapeDangerGoal) {
            return "Run. Run! Don't look back!";
        }
        if (goal instanceof EscapeSunlightGoal) {
            return "The sun burns. Shade! Find shade!";
        }
        if (goal instanceof TemptGoal) {
            return "Food... that hand has food in it. Follow the food.";
        }
        if (goal instanceof AnimalMateGoal) {
            return "That one. Yes. Love.";
        }
        if (goal instanceof FollowParentGoal) {
            return "Stay close to mother. Stay close.";
        }
        if (goal instanceof EatGrassGoal) {
            return "Grass. Sweet, sweet grass.";
        }
        if (goal instanceof FollowOwnerGoal) {
            return "Keep up with my person. Don't lose them.";
        }
        if (goal instanceof SitGoal) {
            return "Sit. Stay. Wait. Good. I'm being good.";
        }
        if (goal instanceof SwimGoal && mob.isTouchingWater()) {
            return "Water! Keep my head up! Keep my head up!";
        }
        if (goal instanceof RevengeGoal) {
            return "It hurt me. Now it pays.";
        }
        if (goal instanceof ActiveTargetGoal<?>) {
            return target == null ? "Hunting. Something warm is near. I can smell it." : null;
        }
        if (goal instanceof LookAtEntityGoal) {
            return "Something's watching. I'm watching back.";
        }
        if (goal instanceof WanderAroundGoal) {
            return "Nowhere to be. Just walking. Just walking.";
        }
        return null;
    }

    @Nullable
    private static String activityThought(String activity, LivingEntity creature) {
        return switch (activity) {
            case "work" -> "Work, work. The workstation needs me.";
            case "rest" -> "So tired... bed... sleep...";
            case "meet" -> "To the bell. Everyone gathers at the bell. Did you hear what happened?";
            case "panic" -> "RUN. HIDE. Something's coming for us!";
            case "raid" -> "The raid! Ring the bell! Get inside!";
            case "pre_raid" -> "Something's wrong. The air feels like a raid.";
            case "hide" -> "Hide. Inside. Quiet. Quiet.";
            case "play" -> "Tag! You're it! Can't catch me!";
            case "fight" -> "Fight. Fight! For the bastion!";
            case "celebrate" -> "We won! We won! Dance!";
            case "admire_item" -> "Gold... let me look at it. Just a little longer.";
            case "avoid" -> "Stay away from it. Stay away.";
            case "ride" -> "Up, up, on the back. Ride!";
            case "play_dead" -> "Don't move. Don't breathe. I'm dead. I'm dead.";
            case "roar" -> "ROAR. Let them hear me.";
            case "investigate" -> "Something made a sound. There. I will find it.";
            case "sniff" -> "Sniff... sniff... where are you hiding?";
            default -> null;
        };
    }
}

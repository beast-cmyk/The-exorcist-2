# Requiem: Possessed Grin

An addon for [Requiem](https://modrinth.com/mod/requiem) on **Minecraft 1.19.2 (Quilt)** that works alongside
**Requiem: Pandemonium**.

- **Possession comes in stages**, like the novel and the film: *Infestation*, *Oppression*, then full
  *Possession*, about 30 seconds from first touch to full horror, each with its own title, sounds and powers.
- **The trapped soul talks to you**: the creature you're wearing pleads, bargains and begs forgiveness, and
  answers when you speak to it.
- **You are the demon**: what you type comes out of the body you're wearing, in a voice that isn't its own, and
  everyone nearby reacts. Villagers pray and run, animals bolt, golems attack, and cleric exorcists answer with the rite.
- **Talk to any creature** in chat: ask it anything and it answers in its own voice, from what it really knows,
  or taunt it (in chat or with the taunt key) and watch it react.
- **Its own kind won't fight you**: hit a mob of your body's kind and it backs off hurt, then comes back to help.
- **Telepathy that truly reads minds**: a stream of the creature's real thoughts, from its actual AI.
- **Possess the bosses** — the Warden, the Wither and the Ender Dragon (plus any modded boss in Requiem's boss list),
  and fight with their real attacks.
- **Every possessed mob looks possessed** — glowing white eyes, a malicious grin and fangs on every mob; by the
  last stage, blood, bruises and a corpse's pallor. It moves possessed too: twitches, convulsions, a hanging head,
  a marionette lurch.
- **Even passive mobs can attack** — villagers, cows, pigs, horses... anything you possess can punch.
- **Demonic powers** inspired by *The Exorcist* (film and novel): levitation, telekinesis, telepathy, the head
  turn, green bile, poltergeist fury, an icy presence, a demon's voice, unholy strength and the spider-walk.
- **The possessed look the part**: pale, veined, cracked skin; they float flat on their backs, scuttle face-up,
  and crawl walls with their heads bent back.
- **The haunting follows you**: knocking in the walls, lights flickering and dying, furniture rattling, your
  screen shaking.
- **Exorcism**: holy water burns the possessed, cleric villagers hunt you with it, and enough of it casts you out.
- **Ouija board**: hold a séance to start a haunting, possess any creature, and recharge powers twice as fast.

## Requirements
| | Version |
|---|---|
| Minecraft | 1.19.2 |
| Loader | Quilt Loader + Quilt Standard Libraries / Quilted Fabric API (what Requiem already needs) |
| Requiem | 2.0.0-beta.16 (the 1.19.2 release) |
| Pandemonium | optional, same Requiem release |

## The stages of a possession
A possession takes hold in three short stages. A title announces each one, the room shudders, and the trapped
soul cries out. An Ouija séance makes them come twice as fast.

| Stage | When | What happens |
|---|---|---|
| **I. Infestation** | 0 to 12 s | Knocking, cold, dying lights. The eyes flicker white now and then, like something peering out. The head twitches. You can read minds (V) and talk. |
| **II. Oppression** | 12 to 30 s | The body seizes in a convulsion. The skin sickens, the eyes stay lit, the grin and fangs come through. It stoops and breathes heavily, and its head sometimes cranes back to stare at the sky. **Awakens:** the head turn (H), poltergeist (G), levitation, the demon's voice. |
| **III. Possession** | 30 s on | A violent convulsion, then the full horror: tears of blood, a gash on the brow, blood running down the body, bruises, a corpse's pallor, pulsing eyes. The head hangs tilted, the body sways, the walk becomes a marionette lurch, limbs jerk on their own, fits come and go, and blood drips from the face. **Awakens:** telekinesis (R), bile (B), unholy strength, the spider-walk. |

Pressing a power's key too early tells you how long until it awakens. Leave the body for a moment (to change
dimension, say) and come back into the same kind of mob, and the possession picks up where it was.

## The trapped soul
The creature you're wearing is still in there. Every half minute or so it speaks up. At first it fights and
demands you leave, then it bargains and begs forgiveness, and at the end it fades to a whisper. Each kind has its
own voice: villagers and other people, the walking dead, beasts, pets (who ask for their owner), golems, bosses.

Type in chat without addressing anyone else (or say "soul") and it answers: say sorry, forgive it, promise to let
it go, greet it, taunt it, ask it who it is or how it feels.

## Your voice
You are the demon. While you possess something, what you type in chat doesn't appear as your own message: it comes
out of the body (`Villager: ...`), in its voice but wrong. In stage I it's grey and nearly normal; in stage II it turns
red, and a few letters crawl and flicker; in stage III it's deep red with more of them crawling. The body growls as
it speaks.

Creatures within earshot react, one or two at a time, more often the deeper the possession:
- **Villagers** stop and stare in stage I. From stage II they cry out, pray, beg you to give the body back, and run.
- **Animals** stare, then bolt. **Pets** growl or hide. **Spiders, endermen and creepers** back away.
- **Iron and snow golems** recognise the voice and attack (stage II on).
- **Monsters** go very still and listen.
- **Cleric exorcists** always answer you, and ready their holy water.

## The taunt key (J)
No typing needed. The body throws back its head and **laughs**, cocks its head and **hisses** through bared teeth,
or hunches and **growls** so low the ground seems to hum. You see the animation, everyone nearby hears it, and
whoever you're looking at reacts as if you'd insulted it: monsters come for you, the meek flee, exorcists falter.
Works from stage I, every 2 seconds.

## Talking to creatures
While possessing something, type in chat. Whoever you're **looking at** answers; otherwise the nearest creature
you **name** ("cow, are you scared?", "hey iron golem"); otherwise the trapped soul. They answer after a moment,
turning to look at you.

Ask about anything: who or what they are, how they feel, what they fear, their family, food, work (villagers
name their job and where their workstation is), **home** (villagers give their bed's coordinates), **trades**
(merchants list their real offers), enemies (who they're really hunting or angry at), their owner, **where you
are** (biome, dimension, coordinates, which way the village bell is), the **time** and **weather**, **what they
can see** right now, what they think of you (villagers remember your reputation), or a **secret** (they'll point
you to the nearest spawner, ancient debris, diamonds, emeralds, chest or gold). Anything else gets an answer in
character.

**Taunt** them and they react: monsters, illagers, golems and anything that can be angered come for you; the meek
flee; bosses sneer. Say sorry and a creature that was after you may let it go.

From stage III they **obey**: "follow me", "stay", and "attack" (look at the target).

Every line is original. Each kind of creature has its own voice: villagers, illagers, witches, zombies,
skeletons, piglins, creepers, spiders, endermen, golems, pets, animals, other monsters and bosses.

## Kin
Strike a creature of your body's own kind (a villager while you're a villager, any zombie while you're a zombie,
a piglin while you're a piglin brute...) and it **doesn't fight back**. It backs away, hurt and confused, then comes
back to **help**: it heals you (Regeneration II) and brings a gift (bread, bones, gold nuggets, a poppy from an iron
golem, a healing potion from a witch...). Ask your kin for "help" and they come straight over. A creature helps
once every 30 seconds. Its kin also never turn on your body, unless you taunt them.

## Demonic powers
Keys can be changed in **Options → Controls → Possessed Powers**. Each awakens at a stage (see above).

| Key | Power | What it does |
|---|---|---|
| **R** | Telekinesis | Lift whatever you look at (up to 24 blocks): a mob, a player, an item, or a block torn out of the ground. It floats in front of you. Press **R** again to hurl it; anything thrown into a wall gets slammed. |
| **G** | Poltergeist fury | Every door, trapdoor and fence gate within 8 blocks bangs open or slams shut, and a shockwave throws everything away from you. |
| **V** | Telepathy | Every creature within 64 blocks glows through walls for 10 seconds, and you read the mind of whoever you're looking at. A creature's thoughts pour out in its own words, read from its real mind: the goals its AI is running (hunting, fleeing, being tempted by food, breaking a door...), its brain's activity (working, gossiping at the bell, panicking in a raid), who it's after and who hurt it, its home and workstation, its wounds, its loves, its fear of you, what it thinks of you. With nobody in sight, your mind turns inward to the trapped soul. A player you read gives up hearts, hunger, what they hold and where they last died, and hears a voice in their head that knows things it shouldn't. |
| **H** | Head turn | The head twists all the way around to face behind it with a sickening crack, stares, then twists back. Everything within 10 blocks is struck with terror: slowed and weakened for 5 s, mobs drop their fight and run, villagers panic. |
| **B** | Green bile | A gush of green muck where you look (8 blocks). Anything hit gets nausea, a little poison and some damage. |
| **J** | Taunt | Laugh, hiss or growl at whoever you're looking at (see "The taunt key"). |

Passive, awakening with the stages:
- **Levitation** (II): every possessed mob can fly. Double-tap jump, like creative mode.
- **Spider-walk** (III): walk into a wall to crawl up it.
- **Unholy strength** (III): melee hits do +50% damage and knock targets back much harder.
- **Demon's voice** (II): every growl and cry the mob makes comes out deep and wrong.
- **Cold presence** (from the start): frost drifts off you. Creatures nearby shiver with frost on them and slow down, players see
  their breath and frost on their screen (it never freezes anyone to death), and still water around you ices over
  like Frost Walker ice, melting back later.
- **Possessed skin:** the body turns pale and sickly, crossed with dark veins and cracks (II), then corpse-grey with
  twice the veins, bruises and blood running from cuts (III). It's generated from each mob's own texture, so it
  fits every mob, modded ones included.
- **Floating flat:** hover in place while flying and the body tips over to float on its back, bobbing gently.
  Start moving and it rights itself.
- **Spider-walk:** crouch on the ground (humanoids) and the body flips face-up, arms and legs bent under it, and
  scuttles head-first. On walls, the head bends all the way back to stare up.
- **Room reacts:** every power shakes your screen, wobbles armor stands and spins item frames nearby. Poltergeist
  fury can knock paintings off the walls.

Cooldowns: telekinesis ½ s, taunt 2 s, poltergeist 3 s, head turn 3 s, bile 4 s, telepathy 5 s.

Powers are inspired by the film and the novel. No dialogue is copied (every whisper is original), and the
disturbing scenes that aren't game material are left out. Lifting blocks follows the `mobGriefing` gamerule, and never takes unbreakable
blocks or chests and other containers.

## The haunting
While you possess something, it follows you everywhere:
- **Knocking** from inside the walls every so often: soft raps in threes, or hard bangs.
- **Dying lights:** torches and lanterns near you keep flickering (they briefly burn blue and dim), and now and
  then one goes out. Torches drop as items; candles and campfires can be relit.

## Exorcism
- **Holy water:** throw a splash bottle of plain water at a possessed creature and it burns (3 damage, hissing
  smoke). Six burns before the count fades and the demon is cast out, which forces you out of the body.
  The burn count shows on screen, and one burn fades every 10 seconds.
- **Exorcists:** cleric villagers know what you are. If one can see you within 14 blocks, it **speaks the rite**
  at you, a line with every throw, commanding you to leave the body, and throws holy water every few seconds, even
  while the other villagers run. The rite builds: the further it gets, the faster they throw and the harder the
  water burns (up to 6 damage). It starts over if they stop for 30 seconds.
- **Breaking their faith:** taunt an exorcist (in chat, or with the taunt key while looking at it) and it falters:
  it stumbles over the rite, stops throwing for 10 seconds, and the rite loses ground. Talk to it any other way and it
  won't bargain. Every word of the rite is original.

## Ouija board
Craft it from 4 planks, 1 ink sac and 1 bone:

```
Plank   Ink sac  Plank
Plank   Bone     Plank
```

Right-click it to hold a one-minute séance:
- the haunting starts around you, whispers included, even if you aren't possessing anything;
- **any creature at all will accept you** while it lasts;
- your powers recharge **twice as fast**.

It can be used again when the séance ends.

## Possessing bosses
Bosses are possessed like any other mob: as a soul, walk up to one and use Requiem's possess action.
No soul vessel needed.

| Boss | Left click a mob | Left click air | Right click (mob or air) |
|---|---|---|---|
| **Warden** | Melee smash (its real 30 damage) | — | **Sonic boom**: charges ~1.7 s with the real animation, then fires where you look. 20 blocks, goes through walls, hits everything on the line. 4 s cooldown. |
| **Wither** | Black wither skull | Blue wither skull | — |
| **Ender Dragon** | Bite (10 damage) | — | **Dragon's breath** fireball where you look (lingering breath cloud, bottle it if you like). 2 s cooldown. |

- **Warden:** you're spared the Darkness it pulses out; everyone else around you still gets it.
- **Wither:** flies, and the skull attacks are Requiem's own, which this addon simply unlocks.
- **Ender Dragon:**
  - You fly it and it faces where you look, with its wings flapping.
  - Its head bites and its wings fling anything you fly into, but never you.
  - It smashes through blocks like the real dragon, if `mobGriefing` is on.
  - To possess it, get close to its **body** (not the tail or wing tips), ideally while it's perched on the exit portal.
  - It is enormous, so it needs open sky.

## The possessed face
- **Stage I** shows only flickers of the glowing eyes; **stage II** adds the grin and fangs; **stage III** adds
  tears of blood, a gash on the brow and blood at the corners of the mouth, and the eyes pulse.
- **Humanoids** have hand-made faces that sit exactly on their own eyes and mouths: all zombies and skeletons,
  villagers, wandering traders, witches, illagers, vexes, piglins, endermen, and Requiem's cured mobs.
- **Every other mob** gets a face found automatically on the front of its head. That includes cows, creepers, spiders,
  the Wither (all three heads), the Warden, the Ender Dragon, and modded mobs.
- The eyes glow in the dark.

## Passive mobs attacking
Any possessed mob without an attack of its own now punches for a tenth of its max health (min 1, max 10).
That's 2 for a villager, 1 for a chicken, and a bite of 10 for the Ender Dragon. Held weapons add on top, and
Requiem's attack cooldown still applies.

How it works: those mobs get an attack stat with a base of **0**, and the punch damage is added only for the moment
you swing. A base of 0 is exactly what Requiem's soul capture already assumes for them, so capturing their souls
is no harder than before, and their normal AI is unchanged.

## With Pandemonium
Pandemonium's config has a **"possess all mobs"** option. Turn it on and you can possess any mob without capturing
its soul first. This addon makes sure that includes the bosses, and gives all of them faces and attacks.
Nothing in the two mods overlaps.

## Building the jar

### Option A: no installs (GitHub)
1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab. The "Build mod jar" workflow runs on upload (or press **Run workflow**).
3. Download the **possessed-grin** artifact; inside is `possessed-grin-5.1.0.jar`.

### Option B: on your computer
Install a JDK 21 and [Gradle 8.14](https://gradle.org/install/), then run `gradle build` in this folder.
The jar is in `build/libs/`.

## Installing
Put the jar in your `mods` folder next to Requiem (and Pandemonium if you use it).
In singleplayer that's it. On a server, put it on the server **and** every player's game.

## Customizing (datapacks / resource packs, no code)
| What | Where |
|---|---|
| Which bosses are possessable | entity tag `possessedgrin:possessable_bosses` |
| Mobs that should *not* get the punch | entity tag `possessedgrin:never_punch` |
| A hand-made face for any mob | `assets/possessedgrin/textures/entity/possessed/<namespace>/<mob>_face.png`, `_eyes.png` and `_blood.png`, same layout as the mob's texture |
| The face designs themselves | edit the ASCII grids in `tools/gen_textures.py`, then `python3 tools/gen_textures.py src/main/resources/assets/possessedgrin` |

## How it works (for the curious)
- **Bosses:** Requiem blocks bosses with one possession rule called `requiem:blacklist`. Requiem stores its
  rules by name, so the addon swaps in a copy of that rule that lets the bosses through, then adds a rule allowing them.
- **Dragon targeting:** the dragon is made of separate hitbox parts. Aiming at a part redirects Requiem to the dragon itself.
- **Dragon flying:** the dragon flies on an autopilot ("phases") that Requiem doesn't switch off. While it's possessed,
  the autopilot is held in its hover phase with its steering cut, so you fly it.
- **Boss attacks:** Requiem abilities registered through the `requiem:plugin` entrypoint.
- **Demonic powers:** the keys send a tiny packet to the server, which checks you're possessing something and
  runs the power, so it all works in multiplayer. Levitation and the spider-walk hook Requiem's movement rules.
  Fabric API (keys, networking, ticking) is only compiled against; in game, Quilted Fabric API provides it.
- **Faces:**
  - An extra render layer on every mob renderer, plus a hook on the dragon renderer.
  - Hand-made faces re-draw the mob's model with an overlay texture.
  - Other mobs get a small decal on the front of their head part, which the addon finds by name in the model.
- **Stages:** the server times each possession and tells clients, which draw and animate it accordingly.
- **Speech:** chat messages from a possessing player are read on the server; replies come from what the game
  knows about that creature (its brain's memories, its goals, its trades, the world around it).
- **Animation:** after each mob's model has posed itself for the frame, the head, arms and legs are nudged
  (twitches, tilts, jerks) and the whole body is tilted and shaken; everything is put back after drawing.
- **`src/main/java/ladysnake`:** compile-time copies of the few Requiem API types used. They are stripped from the jar, so
  Requiem's real classes are used in game, and building doesn't need Requiem's Maven repository.

## Status
Every Minecraft and Requiem name used here was checked against the real 1.19.2 mappings and Requiem beta.16
source, every sound and particle against the real 1.19.2 game data, and the code compiles cleanly against them. It has **not been play-tested yet**.

The Ender Dragon is the most experimental part. Its vanilla code was never meant to be player-driven, so expect
some jank. If something misbehaves, the game log (`logs/latest.log`) will say which part.

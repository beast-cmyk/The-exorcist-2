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
- **Unique replies**: a built-in generator builds every reply from pieces so they rarely repeat, and with a free
  Groq key (or a paid Claude key) a real AI writes them, fully in character.
- **You feed on the body**: you never go hungry inside it, but after about 20 minutes it wastes away and dies,
  and you slip out unharmed (no Attrition).
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
  During a séance the villagers (children first) ask the board questions, and **you** answer them.
- **The body rots while you wear it**: grey and sunken, then skin peeling away to raw flesh, then mostly bone,
  ribs through the chest and a skull for a face, until it collapses into a heap of bones.
- **The full rite**: one night an old priest with a lantern comes out of the fog for you. He and a younger priest
  read the rite while a bar fills; break their faith before it fills, or be cast out.

## New in 7.2: plain talk, and Ollama
- **Characters talk plainly now**, like real people: simple everyday words, short sentences, the feeling right in
  them, no "literature teacher" speeches. (Set `voice_style=rich` in the config for the old, more descriptive way.)
- **Ollama support**: a free AI that runs on your own computer, with no key and no limits (see "Ollama" below).
  By default it's the **backup**: when Groq's free limit runs out, Ollama answers instead of the built-in lines.

## New in 7.1: voices that sound human
Everything from 7.0 is still here. New:
- **Every creature has an inner life.** Feelings that come and go (fear, dread, anger, defiance, grief, pity,
  hope, trust, curiosity, disgust, shame, confusion, relief, longing), stirred by everything it sees and hears,
  fading at their own pace, and mixed: terrified of the body and heartbroken for the person trapped in it, at once.
- **Every creature is someone.** Its own nature (brave or timid, warm or cold, devout or doubting, hot-headed or
  calm, wry or solemn), its own way of talking, and a small piece of a life: a worry, a loss, someone it loves.
- **The AI talks like a real person:** it shows feelings instead of naming them, speaks unevenly the way frightened
  people do, gets moved by what you say, and never repeats itself.
- **The AI now writes the new scenes too:** both priests (their own personalities, reacting to what you do and say,
  breaking down in their own words), the child at the spirit board (its questions follow from your answers), the
  people fooled by your innocent voice or a lost mother's, the nearest bystander when you speak, the cleric, and the
  trapped soul's own outbursts, pleas, and last words.
- Without an AI key, the built-in voices also follow the creature's strongest feeling (new grieving, hopeful,
  curious and ashamed moods), and the priests' stock lines change as their faith goes.

## New in 7.0
Everything from before is still here. New:
- **Visible decay** to the bone (see "Feeding on the body").
- **The idol**: an ancient winged statuette found in desert temple chests.
- **The spirit board, from the other side**: villagers ask, you answer, letter by letter; after three answers they
  invite you in.
- **Attic sounds**: scratching inside the walls and footsteps on the floor above.
- **The bed scene**: the body thrashes on a shaking bed, then rises off it rigid and hangs in the air.
- **The full exorcism**: the old priest's arrival, a two-priest rite with a progress bar, faith you can break, and
  the younger priest offering himself near the end ("take me instead").
- **Body jump (K)**: leap out of this body into another creature.
- **Cruel lines** from the taunt key, an **innocent voice** for lies (`~` in chat), and **mimicry** of someone
  they lost.
- **Freed bodies** are weak and remember nothing.
- **The true face**: for a few frames, the demon's own face shows on the body. Whoever sees it runs.
- **Animation**: the head snaps round at whoever stares at it, a faster and more frantic spider-walk, wilder
  flailing in fits, the head stretching wide as a new stage takes hold, and a body thinned down to the bone.

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

## Feeding on the body
From the moment you possess something, you feed on it. Its strength flows into you: your hunger refills every
10 seconds and you never starve inside it. But a body can only give so much: it lasts about **20 minutes** (one
Minecraft day).
- **Half-way:** the body is wasting: grey and sunken. The soul feels you eating it.
- **Three quarters:** it's rotting. The skin splits and peels back in ragged patches, showing wet raw flesh with
  blackened, mouldering skin around it, and bits of rot fall off it. It slows and drags its feet, and gets thinner.
- **Last two minutes:** it's more bone than body. The patches spread and the flesh in them is gone to ivory bone;
  on humanoids the **ribcage** shows through the chest; the face is a **skull** with two burning pinpoints of light
  deep in the sockets; flakes of bone dust drift off it; it's visibly gaunt, and it trembles.
- **The end:** "The body is spent". You slip out as it crumbles: it collapses into **a heap of bones** (and the
  odd scrap of rotten flesh). Because you left it first, its death isn't yours: **no Attrition**.

The rot and the bones are painted from each mob's own texture (modded mobs too) and show through clothing.

Telepathy turned inward (V with nobody in view) shows how much of its life you've drawn and how long it has left.
Leaving a body and possessing a new one starts the clock fresh.

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

Every reply is put together fresh by the built-in generator: an opener and an aside that fit the creature's
mood (calm, nervous, terrified, hostile, proud, or heartbroken kin), around its real answer. Ask about something it
has no answer for, and it answers about *that* in its own voice ("Diamonds? Is diamonds gold? No? Then no.").

Every line is original. Each kind of creature has its own voice: villagers, illagers, witches, zombies,
skeletons, piglins, creepers, spiders, endermen, golems, pets, animals, other monsters and bosses.

## Real AI replies (optional, free with Groq)
For truly unique replies, a real AI can write what creatures and the trapped soul say. It's told who the creature
is, how its kind talks, and what it really knows right now (its job, home, trades, what it can see, who it's
hunting, the time and weather, the stage of your possession, how much of the body's life is left), plus your last
few exchanges, so it stays in character and remembers what you said. What the creature *does* (attack, flee, obey)
is still decided by the game.

Replies are written to sound like a real person answering: the creature is told who it is (its nature, the way it
talks, a piece of its life), everything it's feeling right now and how that shows, what it's doing, and, for
questions with a true answer (where it lives, what it sells, what time it is), the real answer to put in its own
words. It remembers your last eight exchanges as a real back-and-forth. If you type again before it has answered,
your new message waits its turn.

**Free: Groq** (recommended)
1. Sign up at [console.groq.com](https://console.groq.com) (no card needed), open **API Keys**, and create a key.
2. Start the game once with the mod. It creates `config/possessedgrin-ai.properties` in your Minecraft folder
   (in a launcher like Prism, the instance's `config` folder).
3. Open it in Notepad, paste your key after `groq_api_key=`, save, and load your world.

It tries the models in `groq_models` in order: `openai/gpt-oss-120b` (the smartest, closest to chatting with a
modern assistant), then `llama-3.3-70b-versatile`, then `llama-3.1-8b-instant`. When one model's free daily
limit runs out, the next takes over for a while. Altogether that's very roughly 900+ AI replies a day; from 7.1
the scene lines use some of that too (a long rite might use 30 or 40), and when it runs out everything falls back to
the built-in voices until the limits reset.

**Creatures remember.** Each one keeps your last eight exchanges, and a short list of what it has seen you do:
you hit it, it watched you kill something, it saw the body convulse or its head twist round, the words rise on its
skin, its bed shake. It brings these up when they fit ("You're the one who killed my neighbour yesterday."). The
trapped soul of each body remembers you too. All of it is saved with the world, in `possessedgrin-memories.json`.

**Paid: Anthropic (Claude)**, the best character acting: put a key from
[console.anthropic.com](https://console.anthropic.com) after `anthropic_api_key=`. With `provider=auto` (the
default) Groq is used if it has a key, otherwise Claude; set `provider=groq` or `provider=anthropic` to choose.
`enabled=false` turns the real AI off without deleting your keys.

- **Without a key, the mod never connects to the internet.** With one, it only talks to that service
  (`api.groq.com` or `api.anthropic.com`), sending what you typed to a creature and that creature's situation.
- The game never waits for it. If the AI is slow (over 8 seconds), offline or out of free requests, the built-in
  reply is used. A rejected key turns it off until you fix the file and reload the world.
- **Scene lines** (the priests in the rite, the spirit board, the nearest bystander, the trapped soul crying out,
  reactions to your innocent voice) also use the AI, at most 14 a minute, always leaving room for your own
  conversations. They go to the fast Llama models first, so the big model is kept for talking with you. Whenever
  the AI is busy, slow or out of free requests, the built-in line is used instead, so nothing ever goes silent.
- Kin's cries and the village clerics' rite lines (one with every throw) stay built-in.
- Free limits change; your current ones are shown in the Groq console.

## Ollama (free, on your own computer, no limits)
[Ollama](https://ollama.com) runs an AI model on your own computer. No key, no internet needed, no daily limit.
It's slower than Groq (a few seconds to a few tens of seconds per reply on a laptop) and it uses your computer
while you play, so the mod gives it short prompts and only one request at a time.

1. Install Ollama from [ollama.com](https://ollama.com) and open it (it runs in the background).
2. In a command window, run `ollama pull gemma3:4b` (about 3 GB, once).
3. That's it. The config file already has `ollama=backup` and `ollama_model=gemma3:4b`.

In `config/possessedgrin-ai.properties`:
- `ollama=backup` (default): Groq (or Claude) first; Ollama takes over whenever they can't answer (free limit used
  up, offline, a bad key).
- `ollama=main`: Ollama for everything, even with a Groq key. The model is loaded as soon as you open a world, so
  the first reply isn't the slowest.
- `ollama=off`: never use it.
- `ollama_model`: any model you've pulled. `gemma3:4b` is the best fit for a 16 GB laptop; `llama3.2:3b` is
  faster but plainer.
- `ollama_url`: leave it as `http://localhost:11434` unless you run Ollama somewhere else.

If Ollama isn't running, or doesn't have the model, you get one message in chat saying what to do, and the mod
tries again five minutes later. Meanwhile Groq or the built-in lines carry on.

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
| **H** | Head turn | The head cranks all the way around in six jerky clicks, each with a crack of bone, stares backwards trembling, then slowly twists back. Everything within 10 blocks is struck with terror: slowed and weakened for 5 s, mobs drop their fight and run, villagers panic. |
| **B** | Green bile | A gush of green muck where you look (8 blocks). Anything hit gets nausea, a little poison and some damage. |
| **J** | Taunt | Laugh, hiss or growl at whoever you're looking at (see "The taunt key"). From stage II the body also says something cruel, aimed at them. |
| **K** | Body jump (III) | Look at a creature within 8 blocks and leap out of this body into it. The old body drops, weak and remembering nothing; the new one is taken at stage II (stage III if it's the priest who offered himself). If its soul won't let you in, you fall back into the old body. Cooldown 30 s. |

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
- **Floating flat:** hover in place while flying and the body tips over to float on its back, stiff as a board,
  arms straight at its sides, trembling faintly. Start moving and it rights itself.
- **Spider-walk:** crouch on the ground (humanoids) and the body flips face-up, arms and legs splayed and bent under
  it, and scuttles head-first, fast and frantic, at nearly walking speed (sneaking is normally slow). On walls, the
  head bends all the way back to stare up.
- **Watching back** (II): stare at a possessed body and its head snaps round to stare straight back at you, with a
  crack, then slowly turns away.
- **Room reacts:** every power shakes your screen, wobbles armor stands and spins item frames nearby. Poltergeist
  fury can knock paintings off the walls.

Cooldowns: telekinesis ½ s, taunt 2 s, poltergeist 3 s, head turn 3 s, bile 4 s, telepathy 5 s, body jump 30 s.

Powers are inspired by the film and the novel. No dialogue is copied (every whisper is original), and the
disturbing scenes that aren't game material are left out. Lifting blocks follows the `mobGriefing` gamerule, and never takes unbreakable
blocks or chests and other containers.

## The haunting
While you possess something, it follows you everywhere:
- **Knocking** from inside the walls every so often: soft raps in threes, or hard bangs.
- **Dying lights:** torches and lanterns near you keep flickering (they briefly burn blue and dim), and now and
  then one goes out. Torches drop as items; candles and campfires can be relit.

- **Attic sounds:** something scratches inside the walls in bursts, and slow, heavy footsteps cross the floor
  above you (only under a roof), sometimes ending with a door closing. Villagers who hear it remember.
- **The true face** (III): every minute or so, for a few frames, the body wears the demon's own face: ash-white,
  split with black cracks, black eyes with burning slits, a mouth torn too wide. Every creature looking at it in
  that instant remembers it, and anything that isn't a monster runs. Priests in a rite lose faith.

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

## The full rite of exorcism
- **The arrival:** stay in a body (stage II or later) for two minutes in a village (three or more villagers around)
  after dark. A dog barks somewhere, fog comes in, and a little later an old priest, **Father Ambrose Hale**,
  steps out of the fog at the edge of the village with a lantern in his hand, and walks toward you.
- **The rite:** when he reaches you, a younger priest, **Father Tomas Reyna**, joins him with a book, and they
  begin. A **"The Rite of Exorcism"** bar fills as they pray (about two and a half minutes with both at full faith;
  holy water burns fill it faster, and they throw it like any cleric). When it's full, you're **cast out**.
- **Faith:** each priest's faith shows on the bar. You break it with: taunts (J, or in chat), pulling a secret out
  of a priest's head (V while looking at him), the head turn, bile, telekinesis, poltergeist fury, your true face,
  the idol, and above all a dead mother's voice (mimicry, below). The younger one doubts more easily. Shaky faith
  makes a weak prayer; faith that breaks sends the priest running. Leave them alone a while and it comes back.
  Break both and **the rite is broken**: the body stays yours.
- **"Take me instead":** near the end, the younger priest steps forward and offers himself. You have 15 seconds:
  press **K** looking at him and you go into him, at stage III. The old priest's faith shatters; he walks away into
  the fog without another word, and the rite is over. Or let the moment pass, and the rite goes on.
- Nobody dies in any of it. Run far enough (64 blocks) and the rite falls apart; after a rite, the priests don't
  come back for a while.

## Lying with its voice
- **The innocent voice:** start a chat message with `~` (for example `~ please, it's me, I'm alright now`) and it
  comes out in the trapped soul's own voice, with none of the demon in it. Creatures that hear it believe it: they
  stop running and come closer, and answer kindly. Those that have already seen what it really is waver instead.
  Exorcists don't believe a word.
- **Mimicry:** speak with `~` as someone they lost (mentioning a mother, father, son, daughter, sister, brother,
  wife, husband, child...) and every creature that hears it is drawn toward the voice as if in a dream. A priest
  who hears it loses a great deal of faith.

## Freed bodies
Leave a body alive (cast out, a body jump, or just letting go) and it's limp and dazed: slowed, weakened and
dizzy for a while. It remembers nothing of what happened or of anything it said to anyone while you wore it.

## The idol
An **Ancient Winged Idol**: a small, very old statuette of something winged with red eyes. About one desert temple
chest in three has one (it's also in the creative inventory).
- Carry it and every possession takes hold **half again as fast**.
- **Clerics can feel it** and keep their distance from whoever carries it (unless they're mid-rite, where it eats
  at their faith instead).
- Right-click it as a soul: dogs howl far away, and a short séance starts. Inside a body: let your true face
  show for an instant. Cooldown 30 s.

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

**The spirit board, from the other side:** during a séance, a villager nearby (a child, if there is one) comes to
the board and asks it something: "Is someone there?", "What's your name?", "Do you want to be my friend?"...
**You are the spirit.** Type your answer in chat: it doesn't go to chat, it goes to the planchette, which spells it
out on your screen letter by letter, knocking with each one. After three answers the villager whispers that you can
come in, if you want: it's **invited**. An invited creature can be possessed whatever it is, and its possession
starts at stage II. Grown-ups may snatch their hand back from the board if your answers turn dark; children rarely
do. The séance lasts as long as someone is still talking to the board.

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

## The haunting, part two: the beds
From stage II, every so often every bed near the possessed body starts banging against the floor, hard and fast,
for a few seconds, with splinters flying, and anyone asleep in one is thrown awake. Levitate over a bed and it
happens far more often. If the body is on or beside the bed, it **thrashes** with it, arms flung about; when the
shaking stops, it rises off the bed, **rigid as a board**, and hangs flat in the air for a few seconds before
sinking back down.

## Help me
At stage II the trapped soul finds one last way to be heard: on humanoid bodies (villagers, illagers, witches,
zombies, skeletons, piglins) two words rise in raised welts across the belly: **HELP ME**. They show through
clothing and robes, and anyone who sees them remembers. (Once the body has rotted to the bone, the skin with
the words on it is gone.)

## The possessed face
- **Stage I** shows only flickers of the glowing eyes; **stage II** adds the grin and fangs; **stage III** adds
  tears of blood, a gash on the brow and blood at the corners of the mouth, and the eyes pulse.
- Faces and skins are painted at **four times** the mob's own texture resolution, so they have detail the mob
  itself doesn't: sunken, bruised eye sockets; bloodshot whites; separate teeth with gums; pointed fangs; slashes
  on the brow; tears of blood that run and drip; skin with branching veins, split cracks, soft bruises and cuts
  that bleed in thin runs ending in drops.
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
3. Download the **possessed-grin** artifact; inside is `possessed-grin-6.0.0.jar`.

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
- **Feeding:** the body's life is the possession's age against one Minecraft day; just before the end the mod
  ends the possession itself, so Requiem never sees a possessed body die.
- **Real AI:** Java's built-in HTTP client, used only when a key is set, off the game thread, with the built-in
  reply as the fallback. Groq is called through its OpenAI-style chat endpoint, Claude through the Messages API.
- **Animation:** after each mob's model has posed itself for the frame, the head, arms and legs are nudged
  (twitches, tilts, jerks) and the whole body is tilted and shaken; everything is put back after drawing.
- **`src/main/java/ladysnake`:** compile-time copies of the few Requiem API types used. They are stripped from the jar, so
  Requiem's real classes are used in game, and building doesn't need Requiem's Maven repository.

## Status
Every Minecraft and Requiem name used here was checked against the real 1.19.2 mappings and Requiem beta.16
source, every sound and particle against the real 1.19.2 game data, and the code compiles cleanly against them. It has **not been play-tested yet**.

The Ender Dragon is the most experimental part. Its vanilla code was never meant to be player-driven, so expect
some jank. If something misbehaves, the game log (`logs/latest.log`) will say which part.

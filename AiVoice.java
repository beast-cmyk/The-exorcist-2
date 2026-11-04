package com.possessedgrin.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Optional real AI for what creatures and the trapped soul say. Off unless you put an API key in
 * {@code config/possessedgrin-ai.properties}: a free Groq key, or a paid Anthropic (Claude) key. Without one, nothing
 * ever goes over the internet and the built-in {@link Phrasebook} does the talking.
 *
 * <p>With a key, each reply is written by Claude from the creature's real situation (what the game knows: its
 * job, home, trades, what it sees, who it's hunting, the time, the weather, the stage of your possession...) and
 * the last few things you said to each other, so it stays in character and remembers. The game never waits for
 * it: if the AI is slow or unreachable, the built-in reply is used instead.
 */
public final class AiVoice {
    private static final Logger LOGGER = LoggerFactory.getLogger("possessedgrin");
    private static final String FILE = "possessedgrin-ai.properties";
    private static final URI ANTHROPIC = URI.create("https://api.anthropic.com/v1/messages");
    private static final URI GROQ = URI.create("https://api.groq.com/openai/v1/chat/completions");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    /** After a model hits its free limit, skip it for this long. */
    private static final long REST_MS = 30L * 60L * 1000L;
    private static final String MEMORY_FILE = "possessedgrin-memories.json";
    /** The smartest free model first; the others take over as each one's daily limit runs out. */
    private static final String DEFAULT_GROQ_MODELS = "openai/gpt-oss-120b, llama-3.3-70b-versatile, llama-3.1-8b-instant";
    private static final int MAX_PER_MINUTE = 30;
    /** How many past exchanges (you said / it answered) a creature remembers. */
    private static final int MEMORY = 8;

    private enum Provider {GROQ, ANTHROPIC, OLLAMA}

    /** In place of a cloud model name: send this one to Ollama, on your own computer. */
    private static final String OLLAMA_ROUTE = "\u0000ollama";
    private static final Duration OLLAMA_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_OLLAMA_LINES_PER_MINUTE = 6;
    /** off, backup (when the cloud AI can't answer) or main. */
    private static String ollamaMode = "backup";
    private static String ollamaModel = "gemma3:4b";
    @Nullable
    private static URI ollamaChat;
    @Nullable
    private static URI ollamaLoad;
    private static long ollamaDownUntil;
    private static int ollamaInFlight;
    private static boolean ollamaWarned;
    private static final Deque<Long> OLLAMA_LINES = new ArrayDeque<>();

    @Nullable
    private static Provider provider;
    private static String apiKey = "";
    private static String model = "";
    private static List<String> models = new ArrayList<>();
    private static final Map<String, Long> RESTING = new HashMap<>();
    private static boolean thinkingOffRejected;
    /** voice_style=plain (default): simple everyday speech. rich: more descriptive. */
    private static boolean plain = true;
    private static long pausedUntil;

    /** Server thread only. */
    private static final Set<UUID> IN_FLIGHT = new HashSet<>();
    private static final Deque<Long> RECENT = new ArrayDeque<>();
    private static final Map<UUID, Deque<String[]>> MEMORIES = new HashMap<>();
    /** A message typed while the AI was still answering the last one waits its turn instead of getting a stock reply. */
    private static final Map<UUID, Runnable> PENDING = new HashMap<>();
    /** Scene lines (priests, the board, bystanders, the soul crying out) have their own, smaller budget. */
    private static final int MAX_LINES_PER_MINUTE = 14;
    private static final Set<UUID> LINE_IN_FLIGHT = new HashSet<>();
    private static final Deque<Long> LINE_RECENT = new ArrayDeque<>();

    /** One request to the AI: who to tell if something goes wrong, what to send, and what to do after. */
    private record Request(ServerPlayerEntity player, String system, Supplier<String> compactSystem, List<JsonObject> turns,
                           Consumer<String> done, Runnable after, boolean light) {
    }

    private AiVoice() {
    }

    public static void init() {
        load();     // creates the config file as soon as the game starts
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            loadMemories(server);
            warmUpOllama();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(AiVoice::saveMemories);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 6000 == 0) {
                saveMemories(server);       // every five minutes, in case the game crashes
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            IN_FLIGHT.clear();
            RECENT.clear();
            MEMORIES.clear();
            PENDING.clear();
            Witness.clear();
        });
    }

    private static final String TEMPLATE = """
            # Possessed Grin: real AI replies (optional)
            #
            # Paste an API key below to let a real AI write what creatures and the trapped soul say.
            # Leave both keys empty and the built-in reply generator is used: without a key this mod never
            # connects to the internet. Changes apply the next time you load a world.
            #
            # FREE: Groq. Sign up at https://console.groq.com, open "API Keys", create a key, paste it after groq_api_key=
            # groq_models is tried in order: when one model's free daily limit runs out, the next takes over.
            groq_api_key=%s
            groq_models=%s
            #
            # PAID: Anthropic (Claude), the best character acting. Get a key at https://console.anthropic.com
            anthropic_api_key=%s
            anthropic_model=%s
            #
            # Which to use: auto (Groq if it has a key, else Anthropic), groq, or anthropic
            provider=%s
            # false turns the real AI off without deleting your keys
            enabled=%s
            #
            # How characters talk: plain (simple everyday words, like real people; the default) or rich (more
            # descriptive and literary)
            voice_style=%s
            #
            # FREE, ON YOUR OWN COMPUTER: Ollama. No key and no limits, but slower, and it uses your computer while
            # you play. Install it from https://ollama.com, then in a command window run:  ollama pull gemma3:4b
            # ollama: off, backup (used whenever Groq/Claude can't answer, like when the free limit runs out),
            # or main (always used, even if you have a Groq key)
            ollama=%s
            ollama_model=%s
            ollama_url=%s
            config_version=8
            """;

    /** Reads (or creates, or upgrades) the config file. Called when the game starts and every time a world starts. */
    private static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
        Properties properties = new Properties();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            if (!properties.containsKey("groq_models") || !properties.containsKey("voice_style") || !properties.containsKey("ollama")) {
                // First run, or a file from an older version: write the new layout, keeping any keys
                String oldGroqModel = properties.getProperty("groq_model", "").trim();
                String oldBackup = properties.getProperty("groq_backup_model", "llama-3.1-8b-instant").trim();
                String chain;
                if (properties.containsKey("groq_models")) {
                    chain = properties.getProperty("groq_models").trim();       // a 7.0 file: keep its list as it is
                } else if (oldGroqModel.isEmpty() || oldGroqModel.equals("llama-3.3-70b-versatile")) {
                    chain = oldBackup.isEmpty() ? "openai/gpt-oss-120b, llama-3.3-70b-versatile" : DEFAULT_GROQ_MODELS;
                } else {
                    chain = oldBackup.isEmpty() ? oldGroqModel : oldGroqModel + ", " + oldBackup;
                }
                String anthropicKey = properties.getProperty("anthropic_api_key", properties.getProperty("api_key", "")).trim();
                String anthropicModel = properties.getProperty("anthropic_model", properties.getProperty("model", "claude-haiku-5-5")).trim();
                properties.setProperty("groq_api_key", properties.getProperty("groq_api_key", "").trim());
                properties.setProperty("groq_models", chain);
                properties.setProperty("anthropic_api_key", anthropicKey);
                properties.setProperty("anthropic_model", anthropicModel.isEmpty() ? "claude-haiku-5-5" : anthropicModel);
                properties.setProperty("provider", properties.getProperty("provider", "auto").trim());
                properties.setProperty("enabled", properties.getProperty("enabled", "true").trim());
                properties.setProperty("voice_style", properties.getProperty("voice_style", "plain").trim());
                properties.setProperty("ollama", properties.getProperty("ollama", "backup").trim());
                properties.setProperty("ollama_model", properties.getProperty("ollama_model", "gemma3:4b").trim());
                properties.setProperty("ollama_url", properties.getProperty("ollama_url", "http://localhost:11434").trim());
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    writer.write(String.format(TEMPLATE,
                            properties.getProperty("groq_api_key"), properties.getProperty("groq_models"),
                            properties.getProperty("anthropic_api_key"), properties.getProperty("anthropic_model"),
                            properties.getProperty("provider"), properties.getProperty("enabled"), properties.getProperty("voice_style"),
                            properties.getProperty("ollama"), properties.getProperty("ollama_model"), properties.getProperty("ollama_url")));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Possessed Grin: couldn't read {}: {}", path, e.toString());
        }

        String groqKey = properties.getProperty("groq_api_key", "").trim();
        String anthropicKey = properties.getProperty("anthropic_api_key", "").trim();
        String choice = properties.getProperty("provider", "auto").trim().toLowerCase();
        boolean on = !"false".equalsIgnoreCase(properties.getProperty("enabled", "true").trim());
        plain = !"rich".equalsIgnoreCase(properties.getProperty("voice_style", "plain").trim());
        provider = null;
        models = new ArrayList<>();
        ollamaMode = properties.getProperty("ollama", "backup").trim().toLowerCase();
        if (!ollamaMode.equals("off") && !ollamaMode.equals("main")) {
            ollamaMode = "backup";
        }
        if (choice.equals("ollama")) {
            ollamaMode = "main";
        }
        ollamaModel = properties.getProperty("ollama_model", "gemma3:4b").trim();
        String ollamaUrl = properties.getProperty("ollama_url", "http://localhost:11434").trim().replaceAll("/+$", "");
        try {
            ollamaChat = URI.create(ollamaUrl + "/api/chat");
            ollamaLoad = URI.create(ollamaUrl + "/api/generate");
        } catch (IllegalArgumentException e) {
            ollamaChat = null;
            ollamaLoad = null;
            LOGGER.warn("Possessed Grin: ollama_url isn't a web address: {}", ollamaUrl);
        }
        ollamaDownUntil = 0L;
        ollamaWarned = false;
        if (on && ollamaMode.equals("main") && ollamaChat != null) {
            provider = Provider.OLLAMA;
        } else if (on && (choice.equals("groq") || choice.equals("auto")) && !groqKey.isEmpty()) {
            provider = Provider.GROQ;
            apiKey = groqKey;
            for (String name : properties.getProperty("groq_models", DEFAULT_GROQ_MODELS).split(",")) {
                if (!name.isBlank()) {
                    models.add(name.trim());
                }
            }
        } else if (on && (choice.equals("anthropic") || choice.equals("auto")) && !anthropicKey.isEmpty()) {
            provider = Provider.ANTHROPIC;
            apiKey = anthropicKey;
            models.add(properties.getProperty("anthropic_model", "claude-haiku-5-5").trim());
        }
        if (models.isEmpty() && provider != Provider.OLLAMA) {
            provider = null;
        }
        if (on && provider == null && ollamaMode.equals("backup") && ollamaChat != null && !choice.equals("off")) {
            provider = Provider.OLLAMA;     // no cloud key: Ollama is the only AI
        }
        model = models.isEmpty() ? "" : models.get(0);
        thinkingOffRejected = false;
        RESTING.clear();
        pausedUntil = 0L;
        if (provider != null) {
            try {
                Net.ready();
            } catch (Throwable t) {
                provider = null;    // a Java without its HTTP client: stay with the built-in voices
                LOGGER.warn("Possessed Grin: can't use real AI on this Java ({}). Using the built-in generator.", t.toString());
            }
        }
        LOGGER.info("Possessed Grin: creature voices use {}", provider == null ? "the built-in generator"
                : provider == Provider.OLLAMA ? "Ollama on this computer (" + ollamaModel + ")"
                : "real AI (" + provider.name().toLowerCase() + ", " + String.join(" > ", models) + ")"
                + (ollamaMode.equals("backup") ? ", with Ollama (" + ollamaModel + ") as backup" : ""));
    }

    /**
     * The first model that isn't resting after hitting its limit, or null if they all are. {@code light}: a short
     * scene line, which wants a fast answer more than a deep one, so thinking models go last.
     */
    @Nullable
    private static String pickModel(boolean light) {
        long now = System.currentTimeMillis();
        for (int pass = 0; pass < (light ? 2 : 1); pass++) {
            for (String name : models) {
                boolean thinking = name.startsWith("openai/gpt-oss");
                if (light && (pass == 0) == thinking) {
                    continue;
                }
                if (now >= RESTING.getOrDefault(name, 0L)) {
                    return name;
                }
            }
        }
        return null;
    }

    // --- remembering, across sessions -------------------------------------------------------------------------

    private static Path memoryFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(MEMORY_FILE);
    }

    private static void loadMemories(MinecraftServer server) {
        Path path = memoryFile(server);
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("talk")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("talk").entrySet()) {
                    Deque<String[]> memory = new ArrayDeque<>();
                    for (JsonElement pair : entry.getValue().getAsJsonArray()) {
                        JsonArray array = pair.getAsJsonArray();
                        memory.addLast(new String[]{array.get(0).getAsString(), array.get(1).getAsString()});
                    }
                    MEMORIES.put(UUID.fromString(entry.getKey()), memory);
                }
            }
            if (root.has("seen")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("seen").entrySet()) {
                    Deque<Witness.Event> events = new ArrayDeque<>();
                    for (JsonElement pair : entry.getValue().getAsJsonArray()) {
                        JsonArray array = pair.getAsJsonArray();
                        events.addLast(new Witness.Event(array.get(0).getAsString(), array.get(1).getAsLong()));
                    }
                    Witness.all().put(UUID.fromString(entry.getKey()), events);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Possessed Grin: couldn't read creature memories: {}", e.toString());
        }
    }

    private static void saveMemories(MinecraftServer server) {
        if (MEMORIES.isEmpty() && Witness.all().isEmpty()) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonObject talk = new JsonObject();
        for (Map.Entry<UUID, Deque<String[]>> entry : MEMORIES.entrySet()) {
            JsonArray pairs = new JsonArray();
            for (String[] exchange : entry.getValue()) {
                JsonArray pair = new JsonArray();
                pair.add(exchange[0]);
                pair.add(exchange[1]);
                pairs.add(pair);
            }
            talk.add(entry.getKey().toString(), pairs);
        }
        JsonObject seen = new JsonObject();
        for (Map.Entry<UUID, Deque<Witness.Event>> entry : Witness.all().entrySet()) {
            JsonArray pairs = new JsonArray();
            for (Witness.Event event : entry.getValue()) {
                JsonArray pair = new JsonArray();
                pair.add(event.text());
                pair.add(event.time());
                pairs.add(pair);
            }
            seen.add(entry.getKey().toString(), pairs);
        }
        root.add("talk", talk);
        root.add("seen", seen);
        try (Writer writer = Files.newBufferedWriter(memoryFile(server), StandardCharsets.UTF_8)) {
            writer.write(root.toString());
        } catch (Exception e) {
            LOGGER.warn("Possessed Grin: couldn't save creature memories: {}", e.toString());
        }
    }

    // --- asking ----------------------------------------------------------------------------------------------

    /**
     * A creature answers you. Returns false if the AI isn't available (the caller speaks the built-in reply).
     * Returns true if the AI is on it: the reply is spoken when it arrives, or {@code fallback} if it fails.
     *
     * @param truth  for questions with a factual answer (where it lives, what it sells...), that answer, so the AI
     *               says the true thing in its own words; otherwise null
     * @param mood   how the creature feels about the demon right now
     */
    /** Forgets every conversation with this creature (and with the soul that was trapped in it). */
    public static void forget(UUID mob) {
        MEMORIES.remove(mob);
    }

    public static boolean ask(ServerPlayerEntity player, MobEntity host, MobEntity mob, Persona persona, int stage,
                              Intent intent, String message, String fallback, @Nullable String truth, String mood,
                              @Nullable String outcome) {
        if (!usable()) {
            return false;
        }
        if (IN_FLIGHT.contains(player.getUuid())) {
            PENDING.put(player.getUuid(), () -> {
                if (!ask(player, host, mob, persona, stage, intent, message, fallback, truth, mood, outcome)) {
                    Conversation.speak(player, host, mob, fallback, 5);
                }
            });
            return true;
        }
        if (MEMORIES.size() > 500) {
            MEMORIES.clear();       // forget old conversations rather than grow forever
        }
        String system = creaturePrompt(player, host, mob, persona, stage, truth, mood, outcome, false);
        Supplier<String> compact = () -> creaturePrompt(player, host, mob, persona, stage, truth, mood, outcome, true);
        Deque<String[]> memory = MEMORIES.computeIfAbsent(mob.getUuid(), id -> new ArrayDeque<>());
        send(player, system, compact, memory, message, reply -> {
            if (reply == null) {
                Conversation.speak(player, host, mob, fallback, 5);
            } else {
                remember(memory, message, reply);
                Conversation.speak(player, host, mob, reply, 0);
            }
        });
        return true;
    }

    /** The trapped soul answers you. Same contract as {@link #ask}. */
    public static boolean askSoul(ServerPlayerEntity player, MobEntity host, int stage, String message, String fallback,
                                  Consumer<String> speak) {
        if (!usable()) {
            return false;
        }
        if (IN_FLIGHT.contains(player.getUuid())) {
            PENDING.put(player.getUuid(), () -> {
                if (!askSoul(player, host, stage, message, fallback, speak)) {
                    speak.accept(fallback);
                }
            });
            return true;
        }
        String system = soulPrompt(player, host, stage, false);
        Supplier<String> compact = () -> soulPrompt(player, host, stage, true);
        Deque<String[]> memory = MEMORIES.computeIfAbsent(host.getUuid(), id -> new ArrayDeque<>());
        send(player, system, compact, memory, message, reply -> {
            if (reply == null) {
                speak.accept(fallback);
            } else {
                remember(memory, message, reply);
                speak.accept(reply);
            }
        });
        return true;
    }

    private static boolean usable() {
        return provider != null && route(false) != null;
    }

    /** Can the cloud AI (Groq or Claude) take a request right now? */
    private static boolean cloudReady() {
        if (provider != Provider.GROQ && provider != Provider.ANTHROPIC) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < pausedUntil) {
            return false;
        }
        while (!RECENT.isEmpty() && now - RECENT.peekFirst() > 60_000L) {
            RECENT.pollFirst();
        }
        return RECENT.size() < MAX_PER_MINUTE;
    }

    private static boolean ollamaReady() {
        return ollamaChat != null && !ollamaMode.equals("off") && System.currentTimeMillis() >= ollamaDownUntil;
    }

    /** Where this request goes: a cloud model, Ollama ({@link #OLLAMA_ROUTE}), or nowhere (null: use the built-in line). */
    @Nullable
    private static String route(boolean light) {
        if (provider == null) {
            return null;
        }
        if (provider == Provider.OLLAMA) {
            return ollamaReady() ? OLLAMA_ROUTE : null;
        }
        String cloud = cloudReady() ? pickModel(light) : null;
        if (cloud != null) {
            return cloud;
        }
        return ollamaMode.equals("backup") && ollamaReady() ? OLLAMA_ROUTE : null;
    }

    private static void remember(Deque<String[]> memory, String said, String answered) {
        memory.addLast(new String[]{said, answered});
        while (memory.size() > MEMORY) {
            memory.pollFirst();
        }
    }

    /** The conversation so far as real back-and-forth turns, ending with what you just said. */
    private static List<JsonObject> turns(Deque<String[]> memory, String message) {
        List<JsonObject> turns = new ArrayList<>();
        for (String[] exchange : memory) {
            turns.add(message("user", exchange[0]));
            turns.add(message("assistant", exchange[1]));
        }
        turns.add(message("user", clip(message, 240)));
        return turns;
    }

    /** Sends the request off the game thread; {@code done} runs back on the server thread with the reply or null. */
    private static void send(ServerPlayerEntity player, String system, Supplier<String> compact, Deque<String[]> memory, String message,
                             Consumer<String> done) {
        IN_FLIGHT.add(player.getUuid());
        String first = route(false);
        if (first == null) {
            done.accept(null);      // every model is resting: the built-in reply, this time
            finished(player);
            return;
        }
        if (!first.equals(OLLAMA_ROUTE)) {
            RECENT.addLast(System.currentTimeMillis());
        }
        attempt(new Request(player, system, compact, turns(memory, message), done, () -> finished(player), false), first);
    }

    /** Finished with one message: if another was typed meanwhile, answer it now. */
    private static void finished(ServerPlayerEntity player) {
        IN_FLIGHT.remove(player.getUuid());
        Runnable next = PENDING.remove(player.getUuid());
        if (next != null) {
            next.run();
        }
    }

    private static void attempt(Request request, String withModel) {
        if (withModel.equals(OLLAMA_ROUTE)) {
            attemptOllama(request);
            return;
        }
        MinecraftServer server = request.player().server;
        Provider using = provider;
        if (using == null || using == Provider.OLLAMA) {
            request.done().accept(null);
            request.after().run();
            return;
        }
        boolean thinkingOff = !thinkingOffRejected;
        String json = using == Provider.GROQ ? groqBody(request.system(), request.turns(), withModel)
                : anthropicBody(request.system(), request.turns(), withModel, thinkingOff);
        String[] headers = using == Provider.GROQ
                ? new String[]{"authorization", "Bearer " + apiKey}
                : new String[]{"x-api-key", apiKey, "anthropic-version", "2023-06-01"};
        Net.post(using == Provider.GROQ ? GROQ : ANTHROPIC, headers, json, TIMEOUT, (status, body, error) -> {
            String reply = error == null && status == 200 && body != null ? clean(using == Provider.GROQ ? groqText(body) : anthropicText(body)) : null;
            server.execute(() -> {
                if (reply != null && !reply.isBlank()) {
                    request.done().accept(reply);
                    request.after().run();
                    return;
                }
                // This model's free limit ran out (or it won't take our request): rest it, and try the next one straight away
                if (status == 429 || (status == 400 && using == Provider.GROQ) || (status == 404 && models.size() > 1)) {
                    RESTING.put(withModel, System.currentTimeMillis() + (status == 429 ? REST_MS : 24L * 60L * 60L * 1000L));
                    String next = pickModel(request.light());
                    if (next != null) {
                        LOGGER.info("Possessed Grin: free limit reached on {}, switching to {}", withModel, next);
                        attempt(request, next);
                        return;
                    }
                }
                handleProblem(request.player(), status, error == null ? "HTTP " + status : "no answer (" + error.getClass().getSimpleName() + ")", thinkingOff);
                if (ollamaMode.equals("backup") && ollamaReady() && (!request.light() || ollamaInFlight == 0)) {
                    attemptOllama(request);     // the cloud couldn't answer: your own computer can
                    return;
                }
                request.done().accept(null);
                request.after().run();
            });
        });
    }

    // --- scene lines -----------------------------------------------------------------------------------------

    /**
     * Someone says their next line in a scene (a priest in the rite, a child at the spirit board, someone who just
     * overheard the demon...). Spoken in chat by {@code speak} when the AI answers, or {@code fallback} if it can't.
     * Returns false straight away if the AI isn't available right now (then nothing is spoken: speak the fallback).
     *
     * @param system who they are and what's happening (see {@link #character})
     * @param cue    what just happened, or what they're about to do, in a sentence or two
     */
    private static boolean line(ServerPlayerEntity player, LivingEntity speaker, Function<Boolean, String> system, String cue, String fallback,
                                Consumer<String> speak) {
        if (provider == null || LINE_IN_FLIGHT.contains(speaker.getUuid())) {
            return false;
        }
        String first = route(true);
        long now = System.currentTimeMillis();
        if (first == null) {
            return false;
        }
        if (first.equals(OLLAMA_ROUTE)) {
            // One at a time on your own computer, and not too many: it shares the computer with the game
            while (!OLLAMA_LINES.isEmpty() && now - OLLAMA_LINES.peekFirst() > 60_000L) {
                OLLAMA_LINES.pollFirst();
            }
            if (ollamaInFlight > 0 || OLLAMA_LINES.size() >= MAX_OLLAMA_LINES_PER_MINUTE) {
                return false;
            }
            OLLAMA_LINES.addLast(now);
        } else {
            while (!LINE_RECENT.isEmpty() && now - LINE_RECENT.peekFirst() > 60_000L) {
                LINE_RECENT.pollFirst();
            }
            // Always leave room for your own conversations
            if (LINE_RECENT.size() >= MAX_LINES_PER_MINUTE || RECENT.size() >= MAX_PER_MINUTE - 6) {
                return false;
            }
            RECENT.addLast(now);
            LINE_RECENT.addLast(now);
        }
        UUID id = speaker.getUuid();
        Deque<String[]> memory = MEMORIES.computeIfAbsent(id, key -> new ArrayDeque<>());
        LINE_IN_FLIGHT.add(id);
        attempt(new Request(player, system.apply(false), () -> system.apply(true), turns(memory, cue), reply -> {
            if (reply == null) {
                speak.accept(fallback);
            } else {
                remember(memory, cue, reply);
                speak.accept(reply);
            }
        }, () -> LINE_IN_FLIGHT.remove(id), true), first);
        return true;
    }

    /** {@link #line} for a creature, spoken in chat as itself; speaks {@code fallback} after {@code delay} if there's no AI. */
    public static void say(ServerPlayerEntity player, @Nullable MobEntity host, MobEntity speaker, @Nullable String role,
                           String scene, String cue, String fallback, int delay) {
        Function<Boolean, String> system = compact -> character(player, host, speaker, role, scene, compact);
        if (!line(player, speaker, system, cue, fallback, text -> Conversation.speak(player, host, speaker, text, 0))) {
            Conversation.speak(player, host, speaker, fallback, delay);
        }
    }

    /** The trapped soul speaks up on its own (crying out, pleading, its last words). */
    public static boolean soulLine(ServerPlayerEntity player, MobEntity host, int stage, String cue, String fallback, Consumer<String> speak) {
        return line(player, host, compact -> soulPrompt(player, host, stage, compact), cue, fallback, speak);
    }

    /** Is a real AI set up (it may still be resting or out of requests right now)? */
    public static boolean enabled() {
        return provider != null;
    }

    private static void handleProblem(ServerPlayerEntity player, int status, String problem, boolean thinkingOff) {
        if (status == 401 || status == 403) {
            provider = ollamaMode.equals("backup") ? Provider.OLLAMA : null;
            player.sendMessage(Text.literal("[Possessed Grin] Your AI key was rejected (" + problem + "). Using the built-in voices until you fix "
                    + FILE + " and reload the world.").formatted(Formatting.GRAY), false);
        } else if (status == 404) {
            provider = ollamaMode.equals("backup") ? Provider.OLLAMA : null;
            player.sendMessage(Text.literal("[Possessed Grin] An AI model in your list wasn't found. Check the models in "
                    + FILE + ". Using the built-in voices.").formatted(Formatting.GRAY), false);
        } else if (status == 400 && thinkingOff && provider == Provider.ANTHROPIC) {
            thinkingOffRejected = true;     // a Claude model that won't run with thinking switched off: ask it normally next time
        } else if (status == 429) {
            pausedUntil = System.currentTimeMillis() + 60_000L;     // out of free requests for now: built-in voices for a minute
        }
        LOGGER.warn("Possessed Grin: AI reply failed: {}", problem);
    }

    // --- Ollama (on your own computer) ------------------------------------------------------------------------

    private static void attemptOllama(Request request) {
        MinecraftServer server = request.player().server;
        URI uri = ollamaChat;
        if (uri == null) {
            request.done().accept(null);
            request.after().run();
            return;
        }
        ollamaInFlight++;
        // A small model on a laptop: the short version of who it is, and only the last few exchanges
        List<JsonObject> turns = request.turns();
        List<JsonObject> recent = turns.subList(Math.max(0, turns.size() - 9), turns.size());
        String json = ollamaBody(request.compactSystem().get(), recent);
        Net.post(uri, new String[0], json, OLLAMA_TIMEOUT, (status, body, error) -> {
            String reply = error == null && status == 200 && body != null ? clean(ollamaText(body)) : null;
            server.execute(() -> {
                ollamaInFlight = Math.max(0, ollamaInFlight - 1);
                if (reply != null && !reply.isBlank()) {
                    request.done().accept(reply);
                    request.after().run();
                    return;
                }
                ollamaProblem(request.player(), status, error);
                request.done().accept(null);
                request.after().run();
            });
        });
    }

    private static String ollamaBody(String system, List<JsonObject> turns) {
        JsonObject body = new JsonObject();
        body.addProperty("model", ollamaModel);
        body.addProperty("stream", false);
        body.addProperty("keep_alive", ollamaMode.equals("main") ? "30m" : "10m");
        String name = ollamaModel.toLowerCase();
        if (name.startsWith("qwen3") || name.contains("deepseek-r1") || name.startsWith("gpt-oss")) {
            body.addProperty("think", false);       // thinking models: answer straight away
        }
        JsonObject options = new JsonObject();
        options.addProperty("temperature", plain ? 0.75 : 0.85);
        options.addProperty("top_p", 0.9);
        options.addProperty("repeat_penalty", 1.15);
        options.addProperty("num_predict", 120);
        options.addProperty("num_ctx", 4096);
        body.add("options", options);
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        for (JsonObject turn : turns) {
            messages.add(turn);
        }
        body.add("messages", messages);
        return body.toString();
    }

    private static String ollamaText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement content = root.getAsJsonObject("message").get("content");
            String text = content == null || content.isJsonNull() ? "" : content.getAsString();
            return text.replaceAll("(?s)<think>.*?</think>", "").trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void ollamaProblem(ServerPlayerEntity player, int status, @Nullable Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null && !(cause instanceof java.net.ConnectException)) {
            cause = cause.getCause();
        }
        boolean timedOut = cause instanceof java.net.http.HttpTimeoutException;
        String tell = null;
        if (status == 404) {
            ollamaDownUntil = System.currentTimeMillis() + 5L * 60L * 1000L;
            tell = "Ollama doesn't have the model " + ollamaModel + " yet. In a command window, run:  ollama pull " + ollamaModel;
        } else if (timedOut) {
            LOGGER.warn("Possessed Grin: Ollama took over {}s to answer; using the built-in line", OLLAMA_TIMEOUT.toSeconds());
        } else if (error != null || status < 0) {
            ollamaDownUntil = System.currentTimeMillis() + 5L * 60L * 1000L;     // not running: don't keep knocking
            tell = "Can't reach Ollama at " + ollamaChat + ". Is it running? (Open the Ollama app.) Trying again in 5 minutes.";
        } else {
            LOGGER.warn("Possessed Grin: Ollama answered HTTP {}", status);
        }
        if (tell != null) {
            LOGGER.warn("Possessed Grin: {}", tell);
            if (!ollamaWarned) {
                ollamaWarned = true;
                player.sendMessage(Text.literal("[Possessed Grin] " + tell).formatted(Formatting.GRAY), false);
            }
        }
    }

    /** With Ollama as the main AI, load the model as soon as a world starts, so the first reply isn't the slowest. */
    private static void warmUpOllama() {
        URI uri = ollamaLoad;
        if (provider != Provider.OLLAMA || uri == null || !ollamaReady()) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", ollamaModel);
        body.addProperty("keep_alive", "30m");
        try {
            Net.post(uri, new String[0], body.toString(), OLLAMA_TIMEOUT, (status, response, error) -> {
                if (status == 200) {
                    LOGGER.info("Possessed Grin: Ollama has {} loaded and ready", ollamaModel);
                } else {
                    LOGGER.warn("Possessed Grin: couldn't load {} in Ollama (HTTP {}, {})", ollamaModel, status, error == null ? "" : error.toString());
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("Possessed Grin: couldn't reach Ollama: {}", t.toString());
        }
    }

    // --- Groq (OpenAI-style chat completions) ----------------------------------------------------------------

    private static String groqBody(String system, List<JsonObject> turns, String withModel) {
        JsonObject body = new JsonObject();
        body.addProperty("model", withModel);
        if (withModel.startsWith("openai/gpt-oss")) {
            // A thinking model: keep its thinking brief and hidden, and leave room for it in the length limit
            body.addProperty("reasoning_effort", "low");
            body.addProperty("include_reasoning", false);
            body.addProperty("max_completion_tokens", 700);
        } else {
            body.addProperty("max_completion_tokens", 180);
            body.addProperty("temperature", plain ? 0.75 : 0.85);
        }
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        for (JsonObject turn : turns) {
            messages.add(turn);
        }
        body.add("messages", messages);
        return body.toString();
    }

    private static String groqText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject first = root.getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonElement content = first.getAsJsonObject("message").get("content");
            return content == null || content.isJsonNull() ? "" : content.getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    // --- Anthropic (Claude Messages API) ---------------------------------------------------------------------

    private static String anthropicBody(String system, List<JsonObject> turns, String withModel, boolean thinkingOff) {
        JsonObject body = new JsonObject();
        body.addProperty("model", withModel);
        body.addProperty("max_tokens", thinkingOff ? 200 : 1200);
        if (thinkingOff) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "disabled");
            body.add("thinking", thinking);
        }
        body.addProperty("system", system);
        JsonArray messages = new JsonArray();
        for (JsonObject turn : turns) {
            messages.add(turn);
        }
        body.add("messages", messages);
        return body.toString();
    }

    /** The text blocks of the response (skipping any thinking). */
    private static String anthropicText(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("content")) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement block : root.getAsJsonObject().getAsJsonArray("content")) {
                JsonObject object = block.getAsJsonObject();
                if (object.has("type") && "text".equals(object.get("type").getAsString()) && object.has("text")) {
                    text.append(object.get("text").getAsString());
                }
            }
            return text.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    /** Everything that touches Java's HTTP client lives here, so it's only loaded when a key is set. */
    private static final class Net {
        private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

        interface Done {
            void done(int status, @Nullable String body, @Nullable Throwable error);
        }

        static void ready() {
            CLIENT.version();
        }

        /** {@code headers}: name, value, name, value... */
        static void post(URI uri, String[] headers, String json, Duration timeout, Done done) {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            for (int i = 0; i + 1 < headers.length; i += 2) {
                request.header(headers[i], headers[i + 1]);
            }
            CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, error) -> done.done(response == null ? -1 : response.statusCode(),
                            response == null ? null : response.body(), error));
        }
    }

    /** One line, no wrapping quotes or a speaker name, not too long. */
    private static String clean(String reply) {
        String line = reply.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        int colon = line.indexOf(':');
        if (colon > 0 && colon < 24 && !line.substring(0, colon).contains(" ") && !line.startsWith("*")) {
            line = line.substring(colon + 1).trim();    // "Villager: ..." -> "..."
        } else if (colon > 0 && colon < 32 && line.substring(0, colon).matches("(Father|Soul of|The soul|Child|The child|Villager|Cleric)[A-Za-z' ]*")) {
            line = line.substring(colon + 1).trim();    // "Father Ambrose Hale: ..." -> "..."
        }
        if (line.length() > 1 && line.startsWith("\"") && line.endsWith("\"")) {
            line = line.substring(1, line.length() - 1).trim();
        }
        line = line.replace("\"", "");
        return clip(line, 320);
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3).trim() + "...";
    }

    // --- what the AI is told ---------------------------------------------------------------------------------

    private static String howToReply() {
        return plain ? PLAIN : RICH;
    }

    private static String howToReply(boolean compact) {
        return compact ? SHORT : howToReply();
    }

    /** For a small model on your own computer: the same rules, short, so it reads them fast. */
    private static final String SHORT = """
            How to talk: like an ordinary person, in simple everyday words. One or two short sentences, under 25 words.
            Answer exactly what was said to you. Let your feelings show plainly in what you say. No fancy words, no
            metaphors, no speeches. At most one tiny action in asterisks, like *backs away*, and only sometimes. Never
            repeat yourself. No quotation marks, no name label. Never say you're an AI or in a game. Scary is fine;
            never gory, sexual or hateful.""";

    /** The default: how real people talk. Simple and direct, with the feeling in it. */
    private static final String PLAIN = """
            How to talk:
            - Talk the way an ordinary person really talks, not like a writer, a poet or an actor. Simple, everyday
              words. Short sentences.
            - Most replies are one or two short sentences, under 25 words. Never more than three sentences.
            - Answer exactly what was said to you. If it asked you something, answer it, or refuse, or lie, if
              that's what you'd really do.
            - Your feelings must come through clearly, but in plain words, the way people blurt things out when
              they're scared or angry or heartbroken. Plain doesn't mean flat: mean every word.
            - No fancy or old-fashioned words, no metaphors, no poetic descriptions, no speeches. Never words like
              "foul", "wretched", "thee", "abomination" or "the darkness".
            - Don't describe your surroundings or your body. At most one tiny action in asterisks, like *backs
              away*, and only sometimes.
            - Use "..." or a dash at most once, and only where you'd really stop or stammer.
            - Your feelings change with what it says: a cruel word makes you angrier or more scared, a kind one
              makes you waver.
            - Mention your own life only when it fits, briefly and simply. Never make up names, places or numbers.
            - Never repeat something you've already said.
            - Plain text only: no quotation marks, no name label, no emoji, no lists.
            - Never say you're an AI, a character, or in a game.
            - It's a horror story: scary is fine, but never gory, sexual or hateful.""";

    private static final String RICH = """
            How to talk:
            - You are a real person (or creature) living through this moment, not a narrator and not an actor. Talk
              exactly the way someone would if this were really happening to them.
            - Answer what was actually said to you. A real question gets a real answer (or a refusal, or a lie, if
              that's what you'd do). A cruel word stings. A kind word makes you waver.
            - Show your feelings in HOW you speak, never by naming them. Not "I am filled with terror," but
              "Stay back. Please... just stay back."
            - Real speech is uneven: short sentences, a word cut off with a dash, starting over, trailing off,
              answering a question with a question, a nervous laugh, swallowing hard. Use these sometimes, not in
              every line.
            - Your feelings are mixed and they move. Fear can flare into anger; pity can break through; hope can
              be crushed. Let what the demon says change you as the conversation goes on.
            - Be specific and personal: your own life, the people you care about, the little things of your day.
              It makes you real. Never make up names, places or numbers.
            - Ordinary words. No speeches, no poetry, no old-fashioned "thee" or "foul creature" (unless you're a
              priest in the rite). Contractions are normal.
            - Usually one to three sentences. Sometimes just a few words. Never more than four sentences.
            - Never repeat something you've already said, and don't start the way you started last time.
            - Now and then, one short action in asterisks, like *takes a step back* or *swallows*. Not every time.
            - Plain text only: no quotation marks, no name label, no emoji, no lists.
            - Never say you're an AI, a character, or in a game.
            - It's a horror story: be unsettling when it fits, never gory, sexual or hateful.""";


    private static String creaturePrompt(ServerPlayerEntity player, MobEntity host, MobEntity mob, Persona persona, int stage,
                                         @Nullable String truth, String mood, @Nullable String outcome, boolean compact) {
        String kind = PossessedGrin.kind(mob);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are ").append(mob.hasCustomName() ? mob.getName().getString() + ", a " + kind : "a " + kind)
                .append(" in a world of villages, forests, caves and monsters. ").append(describe(persona)).append('\n');
        prompt.append(Feelings.natureText(mob)).append("\n\n");
        prompt.append("What's happening: a demon has taken over a ").append(PossessedGrin.kind(host))
                .append(" near you and is talking to you through it. ").append(stageText(stage));
        if (Kinship.isKin(mob, host)) {
            prompt.append(" That body is one of your own kind, someone you might know.");
        }
        prompt.append("\nIn short, toward it you're: ").append(mood).append('\n');
        String feelings = Feelings.describe(mob);
        if (!feelings.isEmpty()) {
            prompt.append(feelings).append('\n');
        }
        if (outcome != null) {
            prompt.append("What you're doing right now (already decided; your words should match it): ").append(outcome).append("\n");
        }
        if (truth != null) {
            prompt.append("The true answer to what it asked (say this in your own words, it's correct): ")
                    .append(Conversation.fill(truth, mob, host, player)).append("\n");
        }

        prompt.append("\nThings you know (use only if relevant):\n");
        prompt.append("- ").append(Facts.timeOfDay(mob.world)).append(' ').append(Facts.weather(mob.world)).append('\n');
        prompt.append("- Around you: ").append(Facts.sees(mob, host, player)).append('\n');
        if (mob.getHealth() < mob.getMaxHealth() * 0.5F) {
            prompt.append("- You're badly hurt (").append(Facts.hearts(mob)).append(" hearts).\n");
        }
        if (mob instanceof VillagerEntity villager) {
            String job = villager.getVillagerData().getProfession().id().replace('_', ' ');
            prompt.append("- Your job: ").append(job.equals("none") ? "none yet" : job).append(". What the village thinks of ")
                    .append(player.getName().getString()).append(": ").append(reputationWord(villager.getReputation(player))).append(".\n");
        }
        if (mob instanceof TameableEntity pet && pet.isTamed() && pet.getOwner() != null) {
            prompt.append("- You belong to ").append(pet.getOwner().getName().getString()).append(" and love them.\n");
        }
        String target = Facts.nameOf(mob.getTarget(), host, player);
        if (target != null) {
            prompt.append("- You're hunting ").append(target).append(".\n");
        }
        List<String> memories = recent(Witness.memories(mob), compact ? 4 : 99);
        if (!memories.isEmpty()) {
            prompt.append("\nWhat has happened between you and the demon:\n");
            for (String memory : memories) {
                prompt.append("- ").append(memory).append('\n');
            }
        }
        String attacker = Facts.nameOf(mob.getAttacker(), host, player);
        if (attacker != null) {
            prompt.append("- ").append(attacker).append(" hurt you recently.\n");
        }
        return prompt.append('\n').append(howToReply(compact)).toString();
    }

    private static List<String> recent(List<String> all, int keep) {
        return all.size() <= keep ? all : all.subList(all.size() - keep, all.size());
    }

    private static String soulPrompt(ServerPlayerEntity player, MobEntity host, int stage, boolean compact) {
        String kind = PossessedGrin.kind(host);
        String state = switch (stage) {
            case Stages.INFESTATION -> "It has only just got in. You're terrified and furious, fighting it and demanding it leave.";
            case Stages.OPPRESSION -> "You're losing. You bargain, plead, and beg it to forgive you and let you go.";
            default -> "You've almost lost. You're weak and frightened and speak quietly, but you still make sense.";
        };
        return "You are the soul of a " + kind + ". A demon has taken over your body, and you're trapped inside with it; "
                + "it is talking to you from inside your own head. " + state + "\n"
                + Feelings.soul(host, stage) + "\n"
                + "It has drained " + Math.round(Stages.drain(host) * 100.0F) + "% of your body's life; when it reaches 100% your body dies.\n"
                + "You are a " + kind + ": " + describe(Persona.of(host)) + "\n"
                + Feelings.natureText(host) + " You cling to that life: it's what you're fighting for.\n\n"
                + howToReply(compact);
    }

    /**
     * Who a creature is and what's happening, for a scene line. {@code role} replaces its ordinary identity (the old
     * priest, the child at the board); null for an ordinary creature of its kind.
     */
    public static String character(ServerPlayerEntity player, @Nullable MobEntity host, LivingEntity speaker, @Nullable String role, String scene,
                                   boolean compact) {
        Persona persona = Persona.of(speaker);
        StringBuilder prompt = new StringBuilder();
        if (role != null) {
            prompt.append("You are ").append(role).append('\n');
        } else {
            prompt.append("You are ").append(speaker.hasCustomName() ? speaker.getName().getString() + ", a " + PossessedGrin.kind(speaker)
                    : "a " + PossessedGrin.kind(speaker)).append(" in a world of villages, forests, caves and monsters. ")
                    .append(describe(persona)).append('\n');
        }
        prompt.append(Feelings.natureText(speaker)).append("\n\n");
        prompt.append("What's happening: ").append(scene).append('\n');
        if (host != null && host != speaker) {
            prompt.append(stageText(Stages.of(host))).append('\n');
        }
        String feelings = Feelings.describe(speaker);
        if (!feelings.isEmpty()) {
            prompt.append(feelings).append('\n');
        }
        prompt.append("- ").append(Facts.timeOfDay(speaker.world)).append(' ').append(Facts.weather(speaker.world)).append('\n');
        List<String> memories = recent(Witness.memories(speaker), compact ? 3 : 99);
        if (!memories.isEmpty()) {
            prompt.append("\nWhat you remember of all this:\n");
            for (String memory : memories) {
                prompt.append("- ").append(memory).append('\n');
            }
        }
        return prompt.append('\n').append(howToReply(compact))
                .append("\n\nYou'll be told in brackets what just happened. Say only your next line, as yourself.").toString();
    }

    private static String stageText(int stage) {
        return switch (stage) {
            case Stages.INFESTATION -> "It has only just taken the body: its eyes flicker white now and then, and its voice is almost normal.";
            case Stages.OPPRESSION -> "The body looks sick and twitches, its eyes glow white, and its voice is deep and wrong.";
            default -> "The body is fully taken: bloody, grinning, eyes blazing white, head hanging at a wrong angle.";
        };
    }

    private static String reputationWord(int reputation) {
        if (reputation >= 20) {
            return "a hero who has been kind to the village";
        }
        if (reputation <= -20) {
            return "someone who has hurt the village before";
        }
        return "a stranger";
    }

    private static String describe(Persona persona) {
        return switch (persona) {
            case VILLAGER -> "Villagers are ordinary, wary country folk: farmers and craftspeople who care about their families and their village.";
            case ILLAGER -> "Illagers are cold, proud raiders who serve their captain and look down on villagers.";
            case WITCH -> "Witches are sly, cackling old brewers of potions who enjoy other people's fear.";
            case UNDEAD -> "Zombies are dead people who still walk: hungry, slow and bitter, but they remember a little of who they were. They talk slowly, in short sentences.";
            case SKELETON -> "Skeletons are dry, sarcastic archers with a dark sense of humour.";
            case PIGLIN -> "Piglins are gold-obsessed brutes from the Nether: blunt, greedy and quick to anger. They talk in short, blunt sentences.";
            case CREEPER -> "Creepers are lonely, nervous creatures who hiss when upset and long to get close to someone.";
            case SPIDER -> "Spiders are patient, hungry hunters of the dark who speak softly.";
            case ENDERMAN -> "Endermen are tall, quiet beings from the End who hate being stared at. They speak calmly and briefly.";
            case GOLEM -> "Golems are stern guardians built to protect their village. They speak plainly and briefly.";
            case PET -> "Pets are loving, simple and loyal to their owner, easily frightened and easily excited.";
            case ANIMAL -> "Animals are simple, timid creatures who think about food, their family and danger. They speak simply.";
            case MONSTER -> "Monsters of the dark are cruel and mocking, but even they are uneasy around a demon.";
            case BOSS -> "Bosses are ancient, proud, terrifying powers who speak with calm contempt.";
        };
    }
}

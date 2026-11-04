package com.possessedgrin.power;

import com.possessedgrin.stage.Stages;

/** The key-activated powers. Default keys can be changed in Options > Controls > Possessed Powers. */
public enum Power {
    TELEKINESIS("telekinesis", "Telekinesis", 10, 82 /* R */, 0.4F, 10, Stages.POSSESSION),
    POLTERGEIST("poltergeist", "Poltergeist", 60, 71 /* G */, 1.6F, 30, Stages.OPPRESSION),
    TELEPATHY("telepathy", "Telepathy", 100, 86 /* V */, 0.3F, 12, Stages.INFESTATION),
    HEAD_TURN("head_turn", "The head turn", 60, 72 /* H */, 0.9F, 25, Stages.OPPRESSION),
    BILE("bile", "Bile", 80, 66 /* B */, 0.6F, 12, Stages.POSSESSION),
    TAUNT("taunt", "Taunting", 40, 74 /* J */, 0.25F, 8, Stages.INFESTATION),
    BODY_JUMP("body_jump", "The body jump", 600, 75 /* K */, 1.2F, 20, Stages.POSSESSION);

    public final String id;
    public final String title;
    /** Ticks before the power can be used again (20 ticks = 1 second). */
    public final int cooldown;
    /** GLFW key code (kept as a plain number so this class is safe to load on servers). */
    public final int defaultKey;
    /** How hard your screen shakes when you use it, and for how many ticks. */
    public final float shake;
    public final int shakeTicks;
    /** The possession stage at which this power awakens. */
    public final int stage;

    Power(String id, String title, int cooldown, int defaultKey, float shake, int shakeTicks, int stage) {
        this.id = id;
        this.title = title;
        this.cooldown = cooldown;
        this.defaultKey = defaultKey;
        this.shake = shake;
        this.shakeTicks = shakeTicks;
        this.stage = stage;
    }
}

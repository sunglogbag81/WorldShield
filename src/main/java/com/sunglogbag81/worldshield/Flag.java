package com.sunglogbag81.worldshield;

import java.util.Arrays;
import java.util.Optional;

public enum Flag {
    PVP("pvp"),
    EXPLOSION_BLOCK_DAMAGE("explosion-block-damage"),
    BLOCK_PLACE("block-place"),
    BLOCK_BREAK("block-break"),
    KEEP_INVENTORY("keep-inventory"),
    MOB_SPAWNING("mob-spawning"),
    ITEM_DROP("item-drop"),
    ITEM_PICKUP("item-pickup"),
    ENDERPEARL("enderpearl"),
    MOB_TARGET("mob-target"),
    MOB_DAMAGE("mob-damage"),
    MOB_ENTRY("mob-entry");

    private final String key;

    Flag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Flag> fromKey(String key) {
        return Arrays.stream(values()).filter(flag -> flag.key.equalsIgnoreCase(key)).findFirst();
    }
}

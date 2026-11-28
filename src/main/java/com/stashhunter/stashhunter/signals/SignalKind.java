package com.stashhunter.stashhunter.signals;

public enum SignalKind {
    SHULKER_BOX(SignalCategory.STRONG, 10, TraceGroup.NONE, "shulker box"),
    ENDER_CHEST(SignalCategory.STRONG, 10, TraceGroup.NONE, "ender chest"),

    CHEST(SignalCategory.CONTAINER, 2, TraceGroup.NONE, "chest"),
    TRAPPED_CHEST(SignalCategory.CONTAINER, 2, TraceGroup.NONE, "trapped chest"),
    BARREL(SignalCategory.CONTAINER, 2, TraceGroup.NONE, "barrel"),
    HOPPER(SignalCategory.CONTAINER, 1, TraceGroup.NONE, "hopper"),
    DISPENSER(SignalCategory.CONTAINER, 1, TraceGroup.NONE, "dispenser"),
    DROPPER(SignalCategory.CONTAINER, 1, TraceGroup.NONE, "dropper"),
    FURNACE(SignalCategory.CONTAINER, 1, TraceGroup.NONE, "furnace"),

    WRITTEN_SIGN(SignalCategory.TRACE, 3, TraceGroup.SIGNS, "written sign"),
    UNNATURAL_BLOCK(SignalCategory.TRACE, 3, TraceGroup.UNNATURAL_BLOCKS, "unnatural block"),
    OPEN_PORTAL(SignalCategory.TRACE, 3, TraceGroup.PORTALS, "open portal"),
    OBSIDIAN_FRAME(SignalCategory.TRACE, 3, TraceGroup.PORTALS, "obsidian frame"),
    SKY_BUILD(SignalCategory.TRACE, 3, TraceGroup.HIGH_BUILDS, "sky build"),
    NETHER_ROOF_BUILD(SignalCategory.TRACE, 3, TraceGroup.HIGH_BUILDS, "nether roof build"),
    UNNATURAL_POT(SignalCategory.TRACE, 3, TraceGroup.UNNATURAL_BLOCKS, "unnatural flower pot"),
    ITEM_FRAME(SignalCategory.TRACE, 3, TraceGroup.ENTITIES, "item frame"),
    NAMED_ENTITY(SignalCategory.TRACE, 3, TraceGroup.ENTITIES, "named entity"),
    TRADED_VILLAGER(SignalCategory.TRACE, 3, TraceGroup.ENTITIES, "traded villager"),
    CHESTED_ANIMAL(SignalCategory.TRACE, 3, TraceGroup.ENTITIES, "chested animal"),
    ACTIVATED_SPAWNER(SignalCategory.TRACE, 3, TraceGroup.OTHER_MODULES, "activated spawner"),
    CAVE_DISTURBANCE(SignalCategory.TRACE, 3, TraceGroup.OTHER_MODULES, "cave disturbance"),

    PLACED_BEDROCK(SignalCategory.TRACE, 4, TraceGroup.BEDROCK, "placed bedrock"),
    MINECART_STACK(SignalCategory.TRACE, 4, TraceGroup.OTHER_MODULES, "minecart stack"),
    BEACON(SignalCategory.TRACE, 4, TraceGroup.UNNATURAL_BLOCKS, "beacon"),
    CONDUIT(SignalCategory.TRACE, 4, TraceGroup.UNNATURAL_BLOCKS, "conduit"),

    BED(SignalCategory.WEAK_TRACE, 1, TraceGroup.BASE_BLOCKS, "bed"),
    CRAFTING_TABLE(SignalCategory.WEAK_TRACE, 1, TraceGroup.BASE_BLOCKS, "crafting table"),
    ANVIL(SignalCategory.WEAK_TRACE, 1, TraceGroup.BASE_BLOCKS, "anvil"),
    ENCHANTING_TABLE(SignalCategory.WEAK_TRACE, 1, TraceGroup.BASE_BLOCKS, "enchanting table"),
    BREWING_STAND(SignalCategory.WEAK_TRACE, 1, TraceGroup.BASE_BLOCKS, "brewing stand"),
    SPAWNER(SignalCategory.WEAK_TRACE, 1, TraceGroup.BASE_BLOCKS, "spawner"),
    BOAT(SignalCategory.WEAK_TRACE, 1, TraceGroup.ENTITIES, "boat"),
    CHEST_MINECART(SignalCategory.WEAK_TRACE, 1, TraceGroup.ENTITIES, "chest minecart"),
    MOB_GEAR(SignalCategory.WEAK_TRACE, 1, TraceGroup.ENTITIES, "mob gear");

    private final SignalCategory category;
    private final int weight;
    private final TraceGroup group;
    private final String label;

    SignalKind(SignalCategory category, int weight, TraceGroup group, String label) {
        this.category = category;
        this.weight = weight;
        this.group = group;
        this.label = label;
    }

    public SignalCategory category() {
        return category;
    }

    public int weight() {
        return weight;
    }

    public TraceGroup group() {
        return group;
    }

    public String label() {
        return label;
    }
}

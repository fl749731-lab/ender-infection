package dev.hui.enderinfection.entity;

public enum InfectedKind {
    COW("cow", 60, 0.25, 10, 14, 30, 3.0, 1.5F, 1.9F),
    PIG("pig", 40, 0.29, 8, 12, 28, 2.4, 1.1F, 1.2F),
    SHEEP("sheep", 45, 0.24, 4, 20, 40, 5.0, 1.3F, 2.0F),
    SKELETON("skeleton", 50, 0.23, 4, 16, 32, 20.0, 0.8F, 2.3F),
    SPIDER("spider", 50, 0.30, 7, 14, 30, 2.8, 2.0F, 1.6F),
    ENDERMAN("enderman", 90, 0.28, 14, 18, 36, 3.4, 1.0F, 3.6F),
    CORE("core", 240, 0.0, 8, 24, 56, 8.0, 2.6F, 2.4F),
    SIEGE_BEAST("siege_beast",400,.22,16,20,64,5,2.8F,3.2F),
    EARLY_COW("cow_early",30,.23,4,14,30,2.2,.9F,1.6F),
    EARLY_PIG("pig_early",20,.25,3,12,28,1.8,.9F,.95F),
    EARLY_SHEEP("sheep_early",24,.22,3,20,40,1.8,.9F,1.5F),
    EARLY_SKELETON("skeleton_early",24,.23,3,16,32,12,.65F,2F),
    EARLY_SPIDER("spider_early",24,.27,4,12,26,2.2,1.4F,.9F),
    EARLY_ENDERMAN("enderman_early",40,.25,6,12,26,2.6,.65F,3.2F),
    EARLY_ZOMBIE("zombie_early",24,.23,4,12,26,2,.65F,1.95F);
    public final String key;
    public final double health, speed, radius;
    public final float damage, width, height;
    public final int hitTick, duration;
    public boolean early(){return key.endsWith("_early");}
    public InfectedKind evolved(){
        return switch(this){case EARLY_COW->COW;case EARLY_PIG->PIG;case EARLY_SHEEP->SHEEP;case EARLY_SKELETON->SKELETON;case EARLY_SPIDER->SPIDER;case EARLY_ENDERMAN->ENDERMAN;default->null;};
    }
    InfectedKind(String key, double health, double speed, float damage, int hitTick, int duration, double radius, float width, float height) {
        this.key=key; this.health=health; this.speed=speed; this.damage=damage;
        this.hitTick=hitTick; this.duration=duration; this.radius=radius; this.width=width; this.height=height;
    }
}

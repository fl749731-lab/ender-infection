package dev.hui.enderinfection.entity;

public enum InfectedKind {
    COW("cow", 95, 0.25, 16, 14, 30, 3.0, 1.5F, 1.9F),
    PIG("pig", 65, 0.29, 13, 12, 28, 2.4, 1.1F, 1.2F),
    SHEEP("sheep", 70, 0.24, 7, 20, 40, 5.0, 1.3F, 2.0F),
    SKELETON("skeleton", 80, 0.23, 7, 16, 32, 20.0, 0.8F, 2.3F),
    SPIDER("spider", 80, 0.30, 11, 14, 30, 2.8, 2.0F, 1.6F),
    ENDERMAN("enderman", 140, 0.28, 20, 18, 36, 3.4, 1.0F, 3.6F),
    CORE("core", 240, 0.0, 10, 24, 56, 8.0, 2.6F, 2.4F),
    SIEGE_BEAST("siege_beast",600,.22,24,20,64,5,2.8F,3.2F),
    FORAGER("forager",44,0.32,5,16,40,2.2,1.15F,0.8F),
    SPOREWING_SCOUT("sporewing_scout",48,0.26,5,16,40,14,1.0F,1.4F),
    BURROWER("burrower",120,0.25,12,16,40,3,1.3F,2.5F),
    CARRIER("carrier",180,0.22,10,16,40,2.8,1.5F,1.8F),
    SPIDER_MOTHER("spider_mother",180,0.25,11,16,40,3,2.5F,1.8F),
    HATCH_SAC("hatch_sac",160,0,0,16,40,8,1.7F,2F),
    SHIELD_SENTINEL("shield_sentinel",160,0.22,13,16,40,3,1.1F,2.2F),
    THROAT_SPITTER("throat_spitter",110,0.23,9,16,40,18,1.1F,2.3F),
    LEAP_AMBUSHER("leap_ambusher",110,0.34,16,16,40,7,1.1F,2.4F),
    ROOT_TURRET("root_turret",160,0,10,16,40,20,1.7F,1.8F),
    RESONANCE_PRIEST("resonance_priest",130,0.22,7,16,40,14,1.0F,2.8F),
    SIEGEBREAKER("siegebreaker",420,0.2,24,16,40,4.2,2.6F,3.4F),
    WALKING_NEST("walking_nest",750,0.14,20,16,40,6,5.5F,6.2F),
    BURROW_KING("burrow_king",850,0.18,26,16,40,18,4.8F,7.0F),
    EARLY_COW("cow_early",45,.23,6,14,30,2.2,.9F,1.6F),
    EARLY_PIG("pig_early",32,.25,5,12,28,1.8,.9F,.95F),
    EARLY_SHEEP("sheep_early",36,.22,5,20,40,1.8,.9F,1.5F),
    EARLY_SKELETON("skeleton_early",36,.23,5,16,32,12,.65F,2F),
    EARLY_SPIDER("spider_early",36,.27,6,12,26,2.2,1.4F,.9F),
    EARLY_ENDERMAN("enderman_early",60,.25,9,12,26,2.6,.65F,3.2F),
    EARLY_ZOMBIE("zombie_early",36,.23,6,12,26,2,.65F,1.95F),
    ANCHOR_GUARD("anchor_guard",180,.19,12,16,40,3.3,2.5F,2.75F);
    public boolean designed(){return ordinal()>=FORAGER.ordinal()&&ordinal()<=BURROW_KING.ordinal();}
    public boolean stationary(){return this==CORE||this==HATCH_SAC||this==ROOT_TURRET;}
    public boolean ranged(){return this==SKELETON||this==EARLY_SKELETON||this==SPOREWING_SCOUT||this==THROAT_SPITTER||this==ROOT_TURRET||this==RESONANCE_PRIEST||this==BURROW_KING;}
    public final String key;
    public final double health, speed, radius;
    public final float damage, width, height;
    public final int hitTick, duration;
    public boolean early(){return key.endsWith("_early");}
    public InfectedKind evolved(){
        return switch(this){case EARLY_COW->COW;case EARLY_PIG->PIG;case EARLY_SHEEP->SHEEP;case EARLY_SKELETON->SKELETON;case EARLY_SPIDER->SPIDER;case EARLY_ENDERMAN->ENDERMAN;default->null;};
    }
    /** Armour points so a brood is not melted by a single iron sword sweep. */
    public double armor(){return this==SIEGE_BEAST?10:this==CORE?6:early()?2:4;}
    InfectedKind(String key, double health, double speed, float damage, int hitTick, int duration, double radius, float width, float height) {
        this.key=key; this.health=health; this.speed=speed; this.damage=damage;
        this.hitTick=hitTick; this.duration=duration; this.radius=radius; this.width=width; this.height=height;
    }
}

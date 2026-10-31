package dev.hui.enderinfection.biomass;

/** One speed curve and explicit prices, paid from each mother's own reserve. */
public final class ColonyEconomy {
    private ColonyEconomy(){}
    public static final int STARTER=36,EARLY_UNIT=8,ADVANCED_UNIT=24,NODE=8,EXPANSION=1,RADIUS=4;
    /** Terrain rounds now come every 24 ticks instead of 40. The per-round budget stays 3 so a
     *  double-height plant is still bought atomically and never half-applied. */
    public static final int SPREAD_BASE_TICKS=24,SPREAD_PER_ROUND=3,RADIUS_BASE_TICKS=200,OUTPOST_BASE_TICKS=300,DEEP_BASE_TICKS=200;
    /** Mature land grows flora quickly and births a unit slowly; the mother still pays for the unit. */
    public static final int FLORA_BASE_TICKS=100,SPAWN_BASE_TICKS=600;
    public static final int BOSS_UNIT=160;
    public static int upgradePrice(int stage){return switch(stage){case 1->40;case 2->160;case 3->500;case 4->1400;default->Integer.MAX_VALUE;};}
    public static double speed(int reserve){return 1+Math.min(3,Math.max(0,reserve)/80.0);}
    public static int interval(int baseTicks,int reserve){return Math.max(5,(int)Math.ceil(baseTicks/speed(reserve)));}
}

package dev.hui.enderinfection.biomass;

/** One speed curve and explicit prices, paid from each mother's own reserve. */
public final class ColonyEconomy {
    private ColonyEconomy(){}
    public static final int STARTER=36,EARLY_UNIT=8,ADVANCED_UNIT=24,NODE=8,EXPANSION=1,RADIUS=4;
    public static final int BOSS_UNIT=160;
    public static int upgradePrice(int stage){return switch(stage){case 1->40;case 2->160;case 3->500;case 4->1400;default->Integer.MAX_VALUE;};}
    public static double speed(int reserve){return 1+Math.min(3,Math.max(0,reserve)/80.0);}
    public static int interval(int baseTicks,int reserve){return Math.max(5,(int)Math.ceil(baseTicks/speed(reserve)));}
}

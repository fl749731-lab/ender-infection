package dev.hui.enderinfection.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Third learning layer: a four armed bandit over what a colony does with its biomass.
 * HATCH spends on brood now, EXPAND leaves the wallet to the node network, EVOLVE saves for
 * the next stage and RECOVER keeps reserve for healing. Every 30 second window is scored with
 * one fitness number and the arm that was active takes the credit. The EXPAND arm is scored
 * with what its nodes actually produced, so a node that burns biomass without converting
 * territory cannot keep winning. Exploration is epsilon greedy with an optimism bonus.
 */
public final class ColonyPolicy {
    public enum Action { HATCH, EXPAND, EVOLVE, RECOVER }
    public static final int WINDOW=600,WARMUP=6;
    private static final double EXPLORE=1.1,EPSILON=.2,DECAY=.65,EXPANSION_BONUS=.35;
    private static final double LAND_WEIGHT=.06,KILL_WEIGHT=4,DEATH_WEIGHT=6,INCOME_WEIGHT=.5;
    private final double[] values=new double[Action.values().length];
    private final int[] counts=new int[Action.values().length];
    private int windows;
    private boolean acting;
    private Action arm=Action.HATCH;
    private long windowStart;
    private double lastFitness;
    private int lastChanges,lastSpend;
    private int hatchSpend,terrainSpend;
    public Action arm(){return arm;}
    public boolean acting(){return acting;}
    public int windows(){return windows;}
    public double value(Action action){return values[action.ordinal()];}
    public int samples(Action action){return counts[action.ordinal()];}
    public double hatchValue(){return value(Action.HATCH);}
    public double expandValue(){return value(Action.EXPAND);}
    /** Biomass actually spent by hatching and by the node network, reported by InfectedMob. */
    public void noteHatchSpend(int amount){if(amount>0)hatchSpend+=amount;}
    public void noteTerrainSpend(int amount){if(amount>0)terrainSpend+=amount;}
    public int lastHatchSpend(){return hatchSpend;}
    public int lastTerrainSpend(){return terrainSpend;}
    /** One scalar a mother can actually measure: land, kills, losses and biomass earned. */
    private static double fitness(InfectedMob mother,BroodLearning learning){
        return mother.territoryWeight()*LAND_WEIGHT+learning.kills()*KILL_WEIGHT-learning.deaths()*DEATH_WEIGHT+mother.colonyIncomeTotal()*INCOME_WEIGHT;
    }
    private void credit(Action action,double delta){
        int index=action.ordinal();
        values[index]=values[index]*DECAY+delta*(1-DECAY);
        counts[index]++;
    }
    private static double explore(int seen){return seen==0?EXPLORE:EXPLORE/Math.sqrt(seen);}
    /** Epsilon greedy, with unvisited arms treated as optimistic so every arm gets tried. */
    private Action chooseNext(RandomSource random){
        if(random.nextDouble()<EPSILON)return Action.values()[random.nextInt(Action.values().length)];
        Action best=Action.HATCH;double bestScore=Double.NEGATIVE_INFINITY;
        for(Action candidate:Action.values()){
            double score=values[candidate.ordinal()]+explore(counts[candidate.ordinal()]);
            if(score>bestScore){bestScore=score;best=candidate;}
        }
        return best;
    }
    /** Records one finished window. Public so tests replay the same update path as live play. */
    public void observeWindow(double delta,RandomSource random){
        credit(arm,delta);windows++;
        if(windows>=WARMUP)acting=true;
        arm=chooseNext(random);
    }
    public void tick(ServerLevel level,InfectedMob mother,BroodLearning learning){
        long now=level.getGameTime();
        if(windowStart==0){windowStart=now;lastFitness=fitness(mother,learning);lastChanges=mother.nodePurchasedChanges();lastSpend=mother.nodeSpentBiomass();return;}
        // Node ledgers are only read once per window; reading them every tick would walk every node.
        if(now-windowStart<WINDOW)return;
        int changes=mother.nodePurchasedChanges(),spend=mother.nodeSpentBiomass();
        double delta=fitness(mother,learning)-lastFitness;
        int newChanges=Math.max(0,changes-lastChanges),newSpend=Math.max(0,spend-lastSpend);
        // A node that burns biomass without converting territory must not keep the EXPAND arm fed.
        if(arm==Action.EXPAND&&newSpend>0)delta+=EXPANSION_BONUS*newChanges/(double)newSpend;
        observeWindow(delta,level.random);
        lastFitness=fitness(mother,learning);lastChanges=changes;lastSpend=spend;
        windowStart=now;hatchSpend=0;terrainSpend=0;
    }
    /** True when this decision tick should leave the biomass alone instead of hatching. */
    public boolean skipHatching(InfectedMob mother){
        if(!acting)return false;
        var target=mother.getTarget();
        if(target!=null&&FactionAI.enemy(mother,target))return false;
        return switch(arm){
            case EXPAND->mother.colonyBiomass()>24&&!mother.activeColonyNodes().isEmpty();
            case EVOLVE->mother.motherStage()<5&&mother.colonyBiomass()>16;
            case RECOVER->mother.getHealth()<mother.getMaxHealth()*.6F&&mother.colonyBiomass()>5;
            default->false;
        };
    }
    public void save(CompoundTag tag){
        CompoundTag data=new CompoundTag();
        for(Action action:Action.values()){data.putDouble("V_"+action.name(),values[action.ordinal()]);data.putInt("N_"+action.name(),counts[action.ordinal()]);}
        data.putInt("Windows",windows);data.putBoolean("Acting",acting);data.putString("Arm",arm.name());
        tag.put("ColonyPolicy",data);
    }
    public void load(CompoundTag tag){
        CompoundTag data=tag.getCompound("ColonyPolicy");
        for(Action action:Action.values()){values[action.ordinal()]=data.getDouble("V_"+action.name());counts[action.ordinal()]=Math.max(0,data.getInt("N_"+action.name()));}
        windows=Math.max(0,data.getInt("Windows"));acting=data.getBoolean("Acting");
        try{arm=Action.valueOf(data.getString("Arm"));}catch(IllegalArgumentException ignored){arm=Action.HATCH;}
        windowStart=0;lastFitness=0;lastChanges=0;lastSpend=0;
    }
}

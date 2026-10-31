package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.EnderInfection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded outcome preferences, owned and saved by one mother. No global colony learning. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BroodLearning {
    private final int[][] scores=new int[PlayerTactics.Tactic.values().length][7];
    private long lastDecay;

    private static int family(InfectedKind kind){
        return switch(kind){
            case COW,EARLY_COW->0;case PIG,EARLY_PIG->1;case SHEEP,EARLY_SHEEP->2;
            case SKELETON,EARLY_SKELETON->3;case SPIDER,EARLY_SPIDER->4;
            case ENDERMAN,EARLY_ENDERMAN->5;case EARLY_ZOMBIE->6;default->-1;
        };
    }
    private void decay(long now){
        if(lastDecay==0){lastDecay=now;return;}
        long steps=Math.min(20,Math.max(0,(now-lastDecay)/1200));
        if(steps==0)return;
        for(int[] row:scores)for(int i=0;i<row.length;i++)row[i]-=(int)(Math.signum(row[i])*Math.min(Math.abs(row[i]),steps));
        lastDecay=now;
    }
    public int score(PlayerTactics.Tactic tactic,InfectedKind host,long now){
        decay(now);int index=family(host);return index<0?0:scores[tactic.ordinal()][index];
    }
    public void record(PlayerTactics.Tactic tactic,InfectedKind host,int reward,long now){
        decay(now);int index=family(host);if(index>=0)scores[tactic.ordinal()][index]=Math.max(-18,Math.min(18,scores[tactic.ordinal()][index]+reward));
    }
    public void save(CompoundTag tag){
        CompoundTag learned=new CompoundTag();learned.putLong("LastDecay",lastDecay);
        for(PlayerTactics.Tactic tactic:PlayerTactics.Tactic.values())learned.putIntArray(tactic.name(),scores[tactic.ordinal()]);
        tag.put("BroodLearning",learned);
    }
    public void load(CompoundTag tag,long now){
        CompoundTag learned=tag.getCompound("BroodLearning");lastDecay=Math.min(now,Math.max(0,learned.getLong("LastDecay")));
        for(PlayerTactics.Tactic tactic:PlayerTactics.Tactic.values()){
            int[] values=learned.getIntArray(tactic.name());
            for(int i=0;i<7;i++)scores[tactic.ordinal()][i]=i<values.length?Math.max(-18,Math.min(18,values[i])):0;
        }
    }
    public static void stamp(Mob unit,InfectedKind host,PlayerTactics.Tactic tactic){
        unit.getPersistentData().putString("BroodHost",host.name());
        unit.getPersistentData().putString("BroodTactic",tactic.name());
    }
    private static void report(Mob unit,int reward){
        if(!(unit.level() instanceof ServerLevel level)||!FactionAI.infected(unit))return;
        var data=unit.getPersistentData();
        if(!data.contains("BroodHost")||!data.contains("BroodTactic"))return;
        InfectedMob mother=FactionAI.home(unit);
        if(mother==null||mother.motherBrain()==null)return;
        try{
            mother.motherBrain().learning().record(PlayerTactics.Tactic.valueOf(data.getString("BroodTactic")),InfectedKind.valueOf(data.getString("BroodHost")),reward,level.getGameTime());
        }catch(IllegalArgumentException ignored){/* Old or invalid metadata carries no preference. */}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event){
        if(event.getAmount()<1||!(event.getSource().getEntity() instanceof Mob unit)||!FactionAI.infected(unit)||!FactionAI.enemy(unit,event.getEntity()))return;
        long now=unit.level().getGameTime();var data=unit.getPersistentData();
        if(now<data.getLong("NextBroodReward"))return;
        data.putLong("NextBroodReward",now+100);report(unit,1);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel))return;
        if(event.getSource().getEntity() instanceof Mob killer&&FactionAI.infected(killer)&&killer!=event.getEntity()&&!FactionAI.infected(event.getEntity())&&!killer.isAlliedTo(event.getEntity()))report(killer,3);
        if(event.getEntity() instanceof Mob unit&&FactionAI.infected(unit)&&! (unit instanceof InfectedMob core&&core.kind==InfectedKind.CORE))report(unit,-4);
    }
}

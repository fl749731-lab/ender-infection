package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.biomass.BiomassData;
import dev.hui.enderinfection.biomass.ColonyEconomy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Priority selector for one persistent mother, evaluated at most once per second outside combat. */
public final class MotherBrain {
    public enum State {
        RISING("扎根登场"),EVOLVING("进阶蜕变"),DEFENDING("指挥防御"),INCUBATING("孵化单位"),RECOVERING("消耗生物质恢复"),WAITING("积累生物质"),BLOCKED("等待进阶空间"),BURSTING("濒死爆种防御"),EXPANDING("优先扩张领地");
        public final String label;State(String label){this.label=label;}
    }
    private final InfectedMob mother;
    private long lastBrood=-1000000,nextRetry,lastRecovery,lastSiege=-1000000;
    private int decisionClock;
    private boolean burstArmed=true;
    private long burstReadyAt,nextBurstRetry,burstDisplayUntil;
    private PlayerTactics.Tactic tactic=PlayerTactics.Tactic.BALANCED;
    private final BroodLearning learning=new BroodLearning();
    private java.util.UUID observedPlayer;
    private long observedUntil;
    public void rememberPlayer(java.util.UUID player,long now){observedPlayer=player;observedUntil=now+600;}
    public MotherBrain(InfectedMob mother){this.mother=mother;}
    public static int threshold(int stage){return ColonyEconomy.upgradePrice(stage);}
    public static double health(int stage){return switch(stage){case 1->100;case 2->240;case 3->480;case 4->900;default->1000;};}
    public static float width(int stage){return switch(stage){case 1->1.45F;case 2->2.6F;case 3->3.3F;case 4->4.2F;default->5.2F;};}
    public static float height(int stage){return switch(stage){case 1->1.4F;case 2->2.4F;case 3->3.1F;case 4->4F;default->4.8F;};}
    public static int interval(int stage){return switch(stage){case 1->300;case 2->200;case 3->160;case 4->120;default->100;};}
    public static int cap(int stage){return switch(stage){case 1->4;case 2->8;case 3->14;case 4->20;default->28;};}
    public static int brood(int stage){return Math.min(4,stage);}
    public PlayerTactics.Tactic tactic(){return tactic;}
    public BroodLearning learning(){return learning;}
    public void save(CompoundTag tag){tag.putLong("LastBrood",lastBrood);tag.putLong("NextBroodRetry",nextRetry);tag.putLong("LastRecovery",lastRecovery);tag.putBoolean("BurstArmed",burstArmed);tag.putLong("BurstReadyAt",burstReadyAt);tag.putLong("NextBurstRetry",nextBurstRetry);tag.putLong("BurstDisplayUntil",burstDisplayUntil);tag.putLong("LastSiege",lastSiege);if(observedPlayer!=null)tag.putUUID("ObservedPlayer",observedPlayer);tag.putLong("ObservedPlayerUntil",observedUntil);learning.save(tag);}
    public void load(CompoundTag tag){long now=mother.level().getGameTime();lastBrood=tag.contains("LastBrood")?Math.min(now,tag.getLong("LastBrood")):-1000000;nextRetry=Math.min(now+20,tag.getLong("NextBroodRetry"));lastRecovery=Math.min(now,tag.getLong("LastRecovery"));burstArmed=!tag.contains("BurstArmed")||tag.getBoolean("BurstArmed");burstReadyAt=Math.min(now+1200,tag.getLong("BurstReadyAt"));nextBurstRetry=Math.min(now+20,tag.getLong("NextBurstRetry"));burstDisplayUntil=Math.min(now+16,tag.getLong("BurstDisplayUntil"));lastSiege=tag.contains("LastSiege")?Math.min(now,tag.getLong("LastSiege")):-1000000;observedPlayer=tag.hasUUID("ObservedPlayer")?tag.getUUID("ObservedPlayer"):null;observedUntil=Math.min(now+600,Math.max(0,tag.getLong("ObservedPlayerUntil")));learning.load(tag,now);}
    public boolean tryIncubate(ServerLevel level){
        long now=level.getGameTime();if(mother.isNoAi()||now<nextRetry||now-lastBrood<ColonyEconomy.interval(interval(mother.motherStage()),mother.colonyBiomass()))return false;
        nextRetry=now+20;
        if(mother.motherStage()>=4&&now-lastSiege>=ColonyEconomy.interval(1200,mother.colonyBiomass())&&mother.summonSiege(level)){lastSiege=now;lastBrood=now;return true;}
        if(!mother.summonBrood(level))return false;lastBrood=now;return true;
    }
    public void tick(ServerLevel level){
        if(mother.isNoAi())return;
        long now=level.getGameTime();
        if(!burstArmed&&mother.getHealth()>mother.getMaxHealth()*.5F&&now>=burstReadyAt)burstArmed=true;
        if(mother.emerging()){mother.setMotherState(now<burstDisplayUntil?State.BURSTING:mother.motherStage()==1?State.RISING:State.EVOLVING);return;}
        // A crisis can interrupt an attack and bypass ordinary incubation, never the resource or space checks.
        if(burstArmed&&mother.getHealth()<=mother.getMaxHealth()*.25F&&now>=burstReadyAt&&now>=nextBurstRetry){
            nextBurstRetry=now+20;
            if(mother.emergencyDefense(level)){
                burstArmed=false;burstReadyAt=now+1200;burstDisplayUntil=now+16;lastBrood=now;
                mother.setMotherState(State.BURSTING);return;
            }
        }
        if(++decisionClock<ColonyEconomy.interval(20,mother.colonyBiomass()))return;decisionClock=0;
        var target=mother.getTarget();
        if(target instanceof Player player&&mother.hasLineOfSight(player)){
            PlayerTactics.Data memory=PlayerTactics.get(level);memory.observeRetreat(player,mother,level.getGameTime());rememberPlayer(player.getUUID(),now);
        }
        tactic=observedPlayer!=null&&now<observedUntil?PlayerTactics.get(level).tactic(observedPlayer,now):PlayerTactics.Tactic.BALANCED;
        if(mother.attacking()){mother.setMotherState(State.DEFENDING);return;}
        if(target!=null&&FactionAI.enemy(mother,target)&&mother.hasLineOfSight(target)&&((mother.getLastHurtByMob()==target&&mother.tickCount-mother.getLastHurtByMobTimestamp()<100)||target instanceof net.minecraft.world.entity.monster.Enemy&&mother.distanceToSqr(target)<144)){
            FactionAI.defendColony(mother,target,mother.blockPosition());tryIncubate(level);mother.setMotherState(State.DEFENDING);return;
        }
        boolean wantsUpgrade=mother.motherStage()<5&&mother.colonyBiomass()>=threshold(mother.motherStage());
        if(wantsUpgrade&&mother.tryAdvanceMother(level)){mother.setMotherState(State.EVOLVING);return;}
        if(target!=null&&FactionAI.enemy(mother,target)&&mother.distanceToSqr(target)<1024&&mother.hasLineOfSight(target)){
            tryIncubate(level);
            mother.setMotherState(State.DEFENDING);FactionAI.support(mother);return;
        }
        if(mother.getHealth()<mother.getMaxHealth()*.6&&level.getGameTime()-lastRecovery>=ColonyEconomy.interval(100,mother.colonyBiomass())&&mother.spendForRecovery(5)){
            lastRecovery=level.getGameTime();mother.heal(Math.max(2,mother.getMaxHealth()*.03F));mother.setMotherState(State.RECOVERING);return;
        }
        if(tryIncubate(level)){mother.setMotherState(State.INCUBATING);return;}
        mother.setMotherState(!mother.expansionReady()&&mother.motherStage()<5?State.EXPANDING:wantsUpgrade&&mother.colonyBiomass()>=threshold(mother.motherStage())+16?State.BLOCKED:State.WAITING);
    }
    public InfectedKind pickHost(){
        InfectedKind[] pool=switch(tactic){
            case ANTI_RANGED,PURSUIT->new InfectedKind[]{InfectedKind.EARLY_SPIDER,InfectedKind.EARLY_PIG,InfectedKind.EARLY_ENDERMAN};
            case ANTI_MELEE->new InfectedKind[]{InfectedKind.EARLY_COW,InfectedKind.EARLY_SKELETON,InfectedKind.EARLY_SHEEP};
            case ANTI_TOWER->new InfectedKind[]{InfectedKind.EARLY_SKELETON,InfectedKind.EARLY_SPIDER,InfectedKind.EARLY_ENDERMAN};
            case DEFEND_CORE->new InfectedKind[]{InfectedKind.EARLY_COW,InfectedKind.EARLY_SHEEP,InfectedKind.EARLY_SPIDER};
            default->new InfectedKind[]{InfectedKind.EARLY_COW,InfectedKind.EARLY_PIG,InfectedKind.EARLY_SHEEP,InfectedKind.EARLY_SKELETON,InfectedKind.EARLY_SPIDER,InfectedKind.EARLY_ZOMBIE};
        };
        var units=mother.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,mother.getBoundingBox().inflate(40),e->e!=mother&&FactionAI.infected(e)&&FactionAI.home(e)==mother);
        InfectedKind chosen=pool[0];int best=Integer.MAX_VALUE;
        for(InfectedKind host:pool){
            int count=(int)units.stream().filter(e->e instanceof InfectedMob unit&&(unit.kind==host||unit.kind==host.evolved())||e instanceof MutantZombie&&host==InfectedKind.EARLY_ZOMBIE).count();
            int score=count*8-learning.score(tactic,host,mother.level().getGameTime())/3+mother.getRandom().nextInt(3);if(score<best){best=score;chosen=host;}
        }
        return chosen;
    }
}

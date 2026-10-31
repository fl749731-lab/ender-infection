package dev.hui.enderinfection.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared faction targeting, local assistance and throttled navigation. */
public final class FactionAI {
    private FactionAI() {}
    public static boolean infected(Entity e) { return e instanceof InfectedMob || e instanceof MutantZombie; }
    public static boolean enemy(Mob self, LivingEntity e) {
        return e!=self && e.isAlive() && !infected(e) && !self.isAlliedTo(e)
            && (e instanceof Mob || e instanceof Player p && !p.isCreative() && !p.isSpectator());
    }
    public static InfectedMob home(Mob mob){
        if(!(mob.level() instanceof ServerLevel level))return null;
        if(mob instanceof InfectedMob core&&core.kind==InfectedKind.CORE)return core;
        var data=mob.getPersistentData();
        if(data.hasUUID("ColonyMother")){
            var entity=level.getEntity(data.getUUID("ColonyMother"));
            // An assigned unit never silently changes colony when its mother is unavailable.
            return entity instanceof InfectedMob core&&core.kind==InfectedKind.CORE&&core.isAlive()?core:null;
        }
        var core=level.getEntitiesOfClass(InfectedMob.class,mob.getBoundingBox().inflate(40,12,40),e->e.kind==InfectedKind.CORE&&e.isAlive()).stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        if(core!=null)data.putUUID("ColonyMother",core.getUUID());return core;
    }
    public static String duty(Mob mob){String duty=mob.getPersistentData().getString("ColonyDuty");return duty.isEmpty()?"hunter":duty;}
    public static String assignDuty(InfectedMob core){
        var units=core.level().getEntitiesOfClass(Mob.class,core.getBoundingBox().inflate(48),e->e!=core&&infected(e)&&e.getPersistentData().hasUUID("ColonyMother")&&e.getPersistentData().getUUID("ColonyMother").equals(core.getUUID()));
        if(units.size()>=2&&units.stream().noneMatch(e->duty(e).equals("guard")))return "guard";
        return units.size()>=5&&units.size()%4==1?"patrol":"hunter";
    }
    public static void inheritOrders(Mob from,Mob to){
        for(String key:new String[]{"ColonyMother","ColonyDuty","DefendingMother","DefendUntil","DefendPoint","CommandTarget","CommandUntil","SearchPos","SearchUntil","BroodHost","BroodTactic","PendingColonyBiomass"})if(from.getPersistentData().contains(key))to.getPersistentData().put(key,from.getPersistentData().get(key).copy());
    }
    private static boolean emerging(Mob mob){return mob instanceof InfectedMob unit&&unit.emerging()||mob instanceof MutantZombie mutant&&mutant.emerging();}
    private static Vec3 defenseCenter(Mob mob){
        var core=defendedMother(mob);
        if(core!=null)return mob.getPersistentData().contains("DefendPoint")?Vec3.atBottomCenterOf(BlockPos.of(mob.getPersistentData().getLong("DefendPoint"))):core.position();
        core=home(mob);return core!=null&&duty(mob).equals("guard")?core.position():null;
    }
    private static int defenseRadius(Mob mob){return defendedMother(mob)!=null?32:16;}
    public static void task(Mob mob,String label,String reason){
        mob.getPersistentData().putString("ColonyTask",label);
        mob.getPersistentData().putString("TaskReason",reason);
    }
    private static boolean sameColony(Mob first,Mob second){
        var a=first.getPersistentData();var b=second.getPersistentData();
        return a.hasUUID("ColonyMother")?b.hasUUID("ColonyMother")&&a.getUUID("ColonyMother").equals(b.getUUID("ColonyMother")):!b.hasUUID("ColonyMother");
    }
    private static boolean urgent(Mob mob,LivingEntity target){
        return defendedMother(mob)!=null||mob.getLastHurtByMob()==target&&mob.tickCount-mob.getLastHurtByMobTimestamp()<200
            ||target instanceof Mob other&&infected(other.getTarget());
    }
    private static void reserve(Mob mob,LivingEntity target){
        var data=mob.getPersistentData();data.putUUID("HuntTarget",target.getUUID());data.putLong("HuntUntil",mob.level().getGameTime()+40);
    }
    private static void release(Mob mob){mob.getPersistentData().remove("HuntTarget");mob.getPersistentData().remove("HuntUntil");}
    private static int huntLoad(Mob mob,LivingEntity target){
        long now=mob.level().getGameTime();
        return mob.level().getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(48,12,48),e->e!=mob&&e.isAlive()&&infected(e)&&!(e instanceof InfectedMob core&&core.kind==InfectedKind.CORE)&&sameColony(mob,e)
            &&(e.getTarget()==target||e.getPersistentData().hasUUID("HuntTarget")&&e.getPersistentData().getLong("HuntUntil")>now&&e.getPersistentData().getUUID("HuntTarget").equals(target.getUUID()))).size();
    }
    private static boolean canJoin(Mob mob,LivingEntity target){
        if(urgent(mob,target)||mob instanceof InfectedMob core&&core.kind==InfectedKind.CORE)return true;
        if(duty(mob).equals("guard")&&!(target instanceof Enemy)&&!(target instanceof Player))return false;
        int limit=target instanceof Enemy||target instanceof Player?3:target.getMaxHealth()>=30?2:1;
        return huntLoad(mob,target)<limit;
    }
    private static boolean inDutyRange(Mob mob,LivingEntity target){
        if(defendedMother(mob)!=null){
            var data=mob.getPersistentData();boolean ordered=data.hasUUID("CommandTarget")&&data.getUUID("CommandTarget").equals(target.getUUID())&&data.getLong("CommandUntil")>mob.level().getGameTime();
            if(!ordered&&!(target instanceof Player)&&!(target instanceof Enemy)&&!(target instanceof Mob other&&infected(other.getTarget()))&&mob.getLastHurtByMob()!=target)return false;
        }
        Vec3 defense=defenseCenter(mob);if(defense!=null)return target.position().distanceToSqr(defense)<=defenseRadius(mob)*defenseRadius(mob);var core=home(mob);return core==null||core.distanceToSqr(target)<=Math.pow(core.expansionRange()+16,2);
    }
    private static void search(Mob mob,Vec3 pos,long until){mob.getPersistentData().putLong("SearchPos",BlockPos.containing(pos).asLong());mob.getPersistentData().putLong("SearchUntil",until);}
    private static void issue(Mob ally,LivingEntity target,Vec3 seen){
        if(ally.getTarget()!=target)interrupt(ally);
        long now=ally.level().getGameTime();ally.getPersistentData().putUUID("CommandTarget",target.getUUID());ally.getPersistentData().putLong("CommandUntil",now+160);
        if(ally.hasLineOfSight(target)&&inDutyRange(ally,target)){ally.setTarget(target);reserve(ally,target);}else{ally.setTarget(null);release(ally);search(ally,seen,now+160);}
    }
    private static void interrupt(Mob mob){
        if(mob instanceof InfectedMob unit)unit.interruptForOrders();
        else if(mob instanceof MutantZombie unit)unit.interruptForOrders();
    }
    public static void alert(Mob victim,LivingEntity attacker){
        if(!(victim.level() instanceof ServerLevel)||!enemy(victim,attacker))return;
        if(victim instanceof InfectedMob core&&core.kind==InfectedKind.CORE){defendColony(core,attacker,core.blockPosition());return;}
        var core=home(victim);long now=victim.level().getGameTime();
        if(now<victim.getPersistentData().getLong("NextAlarm"))return;victim.getPersistentData().putLong("NextAlarm",now+20);
        int sent=0;
        for(Mob ally:victim.level().getEntitiesOfClass(Mob.class,victim.getBoundingBox().inflate(20,8,20),e->infected(e)&&e!=victim&&!e.isNoAi())){
            if(sent>=3)break;
            var owner=home(ally);if(core!=null&&owner!=core||!inDutyRange(ally,attacker))continue;
            if(ally.getTarget()!=null&&enemy(ally,ally.getTarget()))continue;
            if(victim.hasLineOfSight(attacker))issue(ally,attacker,attacker.position());else search(ally,victim.position(),now+160);sent++;
        }
    }
    public static void defendColony(InfectedMob core,LivingEntity attacker,BlockPos objective){
        if(!(core.level() instanceof ServerLevel)||!enemy(core,attacker))return;
        long now=core.level().getGameTime();if(now<core.getPersistentData().getLong("NextRecall"))return;core.getPersistentData().putLong("NextRecall",now+40);
        var units=new ArrayList<>(core.level().getEntitiesOfClass(Mob.class,core.getBoundingBox().inflate(56,16,56),e->e!=core&&infected(e)&&!e.isNoAi()&&home(e)==core));
        Vec3 point=Vec3.atBottomCenterOf(objective);units.sort(Comparator.comparingDouble(e->e.position().distanceToSqr(point)+(duty(e).equals("guard")?-400:e.getTarget()==null?-100:100)));
        int limit=core.motherStage()==1?3:core.motherStage()==2?4:core.motherStage()==3?6:core.motherStage()==4?8:10;
        dev.hui.enderinfection.particle.FactionEffects.rune((ServerLevel)core.level(),point,1.8);
        for(int i=0;i<Math.min(limit,units.size());i++){
            Mob ally=units.get(i);ally.getPersistentData().putUUID("DefendingMother",core.getUUID());ally.getPersistentData().putLong("DefendUntil",Math.max(now+240,ally.getPersistentData().getLong("DefendUntil")));ally.getPersistentData().putLong("DefendPoint",objective.asLong());
            release(ally);interrupt(ally);task(ally,"回防", "母体或节点警报");
            if(core.hasLineOfSight(attacker))issue(ally,attacker,point);else{ally.setTarget(null);ally.getPersistentData().remove("CommandTarget");search(ally,point,now+160);}
        }
    }
    public static boolean clearShot(Mob shooter,LivingEntity target){
        if(!shooter.hasLineOfSight(target))return false;Vec3 start=shooter.getEyePosition(),end=target.getBoundingBox().getCenter();
        for(Mob ally:shooter.level().getEntitiesOfClass(Mob.class,shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),e->e!=shooter&&infected(e)))if(ally.getBoundingBox().inflate(.15).clip(start,end).isPresent())return false;
        return true;
    }
    public static final class HuntGoal extends Goal {
        private final Mob mob;
        private final Map<UUID,Integer> blocked=new HashMap<>();
        private int scanAt;
        private int lastSeen;
        private UUID tracked;
        private Vec3 lastKnown;
        public HuntGoal(Mob mob) { this.mob=mob;scanAt=mob.getRandom().nextInt(10);setFlags(EnumSet.of(Flag.TARGET)); }
        public void reject(LivingEntity e) { blocked.put(e.getUUID(),mob.tickCount+200);if(lastKnown!=null&&e.getUUID().equals(tracked)&&!mob.hasLineOfSight(e))search(mob,lastKnown,mob.level().getGameTime()+80);mob.setTarget(null);release(mob);task(mob,"重新搜寻","追击失败或目标丢失"); }
        private boolean available(LivingEntity e) {
            if(!inDutyRange(mob,e))return false;
            if(mob instanceof InfectedMob core&&core.kind==InfectedKind.CORE&&!(e instanceof Player)&&!(e instanceof Enemy)&&!(e instanceof Mob other&&infected(other.getTarget()))&&mob.distanceToSqr(e)>144)return false;
            return enemy(mob,e) && blocked.getOrDefault(e.getUUID(),0)<=mob.tickCount && canJoin(mob,e)
                && mob.distanceToSqr(e)<=Math.pow(mob.getAttributeValue(Attributes.FOLLOW_RANGE),2);
        }
        @Override public boolean canUse() { return mob.tickCount>=scanAt; }
        @Override public boolean canContinueToUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(mob.tickCount<scanAt)return;scanAt=mob.tickCount+10;
            blocked.values().removeIf(t->t<=mob.tickCount);
            LivingEntity current=mob.getTarget();
            if(current!=null&&!current.getUUID().equals(tracked)){tracked=current.getUUID();lastKnown=current.position();lastSeen=mob.tickCount;}
            if(current!=null&&mob.hasLineOfSight(current)){lastKnown=current.position();lastSeen=mob.tickCount;}
            if(current!=null&&!mob.hasLineOfSight(current)&&mob.tickCount-lastSeen>120){if(lastKnown!=null)search(mob,lastKnown,mob.level().getGameTime()+100);reject(current);current=null;}
            var data=mob.getPersistentData();
            if(mob.level() instanceof ServerLevel level&&data.hasUUID("CommandTarget")&&data.getLong("CommandUntil")>level.getGameTime()){
                var ordered=level.getEntity(data.getUUID("CommandTarget"));if(ordered instanceof LivingEntity target&&available(target)&&mob.hasLineOfSight(target)){mob.setTarget(target);reserve(mob,target);lastKnown=target.position();lastSeen=mob.tickCount;return;}
            }
            LivingEntity revenge=mob.getLastHurtByMob();
            if(revenge!=null && mob.tickCount-mob.getLastHurtByMobTimestamp()<200 && available(revenge) && mob.hasLineOfSight(revenge)){mob.setTarget(revenge);return;}
            double range=mob.getAttributeValue(Attributes.FOLLOW_RANGE);
            LivingEntity best=current!=null&&available(current)?current:null;double score=best==null?Double.NEGATIVE_INFINITY:score(best)+5;
            var candidates=new ArrayList<>(mob.level().getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(range,8,range),e->available(e)&&mob.hasLineOfSight(e)));
            candidates.sort(Comparator.comparingDouble((LivingEntity e)->score(e)).reversed());
            int paths=0;
            for(LivingEntity e:candidates) {
                double candidate=score(e);if(candidate<=score)continue;
                boolean ranged=mob instanceof InfectedMob unit&&(unit.kind==InfectedKind.SKELETON||unit.kind==InfectedKind.EARLY_SKELETON);
                if(e!=current&&!ranged&&mob.distanceToSqr(e)>64){
                    if(paths++>=3)break;
                    boolean climber=mob instanceof InfectedMob unit&&(unit.kind==InfectedKind.SPIDER||unit.kind==InfectedKind.EARLY_SPIDER);
                    // A freshly spawned ground unit cannot build a path until its first landing.
                    // Retry after landing instead of blacklisting a perfectly reachable prey.
                    if(!climber&&!mob.onGround()&&!mob.isInWater())continue;
                    var path=mob.getNavigation().createPath(e,0);
                    if((path==null||path.getNodeCount()<2)&&!climber) {blocked.put(e.getUUID(),mob.tickCount+80);task(mob,"重新搜寻","候选猎物暂不可达");continue;}
                }
                score=candidate;best=e;
            }
            mob.setTarget(best);
            if(best!=null){reserve(mob,best);task(mob,defendedMother(mob)!=null?"回防":urgent(mob,best)?"应战":"狩猎","已分配目标");}
            else release(mob);
        }
        private double score(LivingEntity target){return (urgent(mob,target)?120:target instanceof Enemy?38:target instanceof Player?30:24)-mob.distanceTo(target)*.9-huntLoad(mob,target)*6+(target.getHealth()<target.getMaxHealth()*.3?4:0);}
        public Vec3 pursuitPoint(LivingEntity target){return mob.hasLineOfSight(target)||lastKnown==null?target.position():lastKnown;}
    }
    public static final class Motion {
        private final Mob mob;
        private final HuntGoal hunt;
        private int nextPath,stalled,noProgress,verticalStall;
        private double bestDistance=Double.MAX_VALUE;
        private Vec3 checkpoint,targetCheckpoint;
        private UUID target;
        private int nextRangedMove;
        public Motion(Mob mob,HuntGoal hunt){this.mob=mob;this.hunt=hunt;}
        public void chase(LivingEntity enemy,boolean ranged) {
            if(!enemy.getUUID().equals(target)){target=enemy.getUUID();checkpoint=mob.position();targetCheckpoint=enemy.position();stalled=0;noProgress=0;verticalStall=0;bestDistance=Double.MAX_VALUE;nextPath=0;}
            if(mob.tickCount<nextPath)return;nextPath=mob.tickCount+10;
            if(checkpoint!=null && mob.position().distanceToSqr(checkpoint)<.04)stalled+=10;else stalled=0;
            checkpoint=mob.position();
            Vec3 pursuit=hunt.pursuitPoint(enemy);double distance=mob.position().distanceToSqr(pursuit);
            if(distance+.25<bestDistance||targetCheckpoint==null||enemy.position().distanceToSqr(targetCheckpoint)>1){bestDistance=distance;noProgress=0;}else noProgress+=10;
            targetCheckpoint=enemy.position();
            double horizontal=mob.position().subtract(enemy.position()).multiply(1,0,1).lengthSqr();
            if(!ranged&&horizontal<9&&enemy.getY()-mob.getY()>1.6)verticalStall+=10;else verticalStall=0;
            if(!ranged && distance<9 && mob.hasLineOfSight(enemy)){stalled=0;noProgress=0;}
            if(stalled>=80||noProgress>=100||verticalStall>=100){hunt.reject(enemy);mob.getNavigation().stop();stalled=0;noProgress=0;verticalStall=0;return;}
            if(ranged&&mob.distanceToSqr(enemy)<16&&mob.hasLineOfSight(enemy)){rangedPosition(enemy);return;}
            if(!mob.hasLineOfSight(enemy)){mob.getNavigation().setSpeedModifier(1.05);mob.getNavigation().moveTo(pursuit.x,pursuit.y,pursuit.z,1.05);return;}
            if(!ranged&&distance>9&&distance<256&&Math.abs(enemy.getY()-mob.getY())<2&&mob.level().getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(8,3,8),e->e!=mob&&infected(e)&&e.getTarget()==enemy).size()>0){
                double angle=Math.floorMod(mob.getUUID().hashCode(),6)*Math.PI/3;Vec3 flank=enemy.position().add(Math.cos(angle)*1.3,0,Math.sin(angle)*1.3);
                if(walkTo(mob,flank,1.1))return;
            }
            mob.getNavigation().moveTo(enemy,1.05);
        }
        public boolean rangedPosition(LivingEntity enemy){
            if(mob.tickCount<nextRangedMove)return !mob.getNavigation().isDone();nextRangedMove=mob.tickCount+10;
            Vec3 away=mob.position().subtract(enemy.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
            double backward=mob.distanceToSqr(enemy)<16?4:0;
            for(double side:new double[]{3,-3,0})if(walkTo(mob,mob.position().add(away.x*backward-away.z*side,0,away.z*backward+away.x*side),1.15))return true;
            return false;
        }
        public void reset(){stalled=0;noProgress=0;verticalStall=0;bestDistance=Double.MAX_VALUE;checkpoint=null;targetCheckpoint=null;target=null;}
    }
    public static void support(Mob mob) {
        if(mob.tickCount%20!=0)return;
        int cargo=mob.getPersistentData().getInt("PendingColonyBiomass");
        if(cargo>0){var mother=home(mob);if(mother!=null){int accepted=Math.min(cargo,10000-mother.colonyBiomass());mother.receiveColonyBiomass(accepted);mob.getPersistentData().putInt("PendingColonyBiomass",cargo-accepted);}}
        LivingEntity enemy=mob.getTarget();
        if(enemy==null || !enemy(mob,enemy) || !mob.hasLineOfSight(enemy))return;
        var owner=home(mob);
        int sent=0;
        for(Mob ally:mob.level().getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(20,8,20),e->infected(e)&&e!=mob&&!e.isNoAi())) {
            if(sent>=2)break;
            if(owner!=null&&home(ally)!=owner||!inDutyRange(ally,enemy))continue;
            if((ally.getTarget()==null||!enemy(ally,ally.getTarget()))&&canJoin(ally,enemy)){issue(ally,enemy,enemy.position());sent++;}
        }
    }
    private static boolean walkTo(Mob mob,Vec3 desired,double speed){
        BlockPos column=BlockPos.containing(desired);if(!mob.level().hasChunkAt(column))return false;
        for(int dy=3;dy>=-4;dy--){
            BlockPos pos=column.offset(0,dy,0);
            if(!mob.level().getBlockState(pos.below()).isFaceSturdy(mob.level(),pos.below(),Direction.UP)||!mob.level().getFluidState(pos).isEmpty()||!mob.level().getFluidState(pos.below()).isEmpty())continue;
            var path=mob.getNavigation().createPath(pos,0);if(path!=null&&path.canReach()){mob.getNavigation().moveTo(path,speed);return true;}
        }
        return false;
    }
    public static final class InvestigateGoal extends Goal {
        private final Mob mob;private Vec3 destination;private int retry;
        public InvestigateGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            var data=mob.getPersistentData();if(emerging(mob)||mob.getTarget()!=null||!data.contains("SearchPos")||data.getLong("SearchUntil")<=mob.level().getGameTime())return false;
            destination=Vec3.atBottomCenterOf(BlockPos.of(data.getLong("SearchPos")));Vec3 defense=defenseCenter(mob);if(defense!=null&&destination.distanceToSqr(defense)>defenseRadius(mob)*defenseRadius(mob))destination=defense;return mob.position().distanceToSqr(destination)>4;
        }
        @Override public boolean canContinueToUse(){return mob.getTarget()==null&&mob.getPersistentData().getLong("SearchUntil")>mob.level().getGameTime()&&mob.position().distanceToSqr(destination)>4;}
        @Override public void tick(){if(mob.tickCount>=retry){retry=mob.tickCount+20;task(mob,"搜索","前往最后目击点或警报地点");if(!walkTo(mob,destination,1.15)){boolean detour=false;for(int i=0;i<4;i++){double angle=i*Math.PI/2; if(walkTo(mob,destination.add(Math.cos(angle)*3,0,Math.sin(angle)*3),1.1)){detour=true;break;}}if(!detour){mob.getPersistentData().putLong("SearchUntil",mob.level().getGameTime());task(mob,"待命","搜索地点不可达，任务结束");}}}}
        @Override public void stop(){mob.getNavigation().stop();}
    }
    private static InfectedMob defendedMother(Mob mob){
        var data=mob.getPersistentData();
        if(!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)||!data.hasUUID("DefendingMother")||level.getGameTime()>=data.getLong("DefendUntil"))return null;
        var entity=level.getEntity(data.getUUID("DefendingMother"));
        return entity instanceof InfectedMob core&&core.kind==InfectedKind.CORE&&core.isAlive()?core:null;
    }
    public static final class GuardMotherGoal extends Goal {
        private final Mob mob;private Vec3 point;
        public GuardMotherGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){point=defenseCenter(mob);return !emerging(mob)&&point!=null&&mob.getTarget()==null&&mob.position().distanceToSqr(point)>36;}
        @Override public boolean canContinueToUse(){return defenseCenter(mob)!=null&&mob.getTarget()==null&&mob.position().distanceToSqr(point)>25;}
        @Override public void tick(){if(mob.tickCount%10==0){task(mob,"驻守","守卫母体或警报地点");double angle=Math.floorMod(mob.getUUID().hashCode(),6)*Math.PI/3;walkTo(mob,point.add(Math.cos(angle)*3,0,Math.sin(angle)*3),1.15);}}
        @Override public void stop(){mob.getNavigation().stop();}
    }
    public static final class ReturnToCoreGoal extends Goal {
        private final Mob mob;private InfectedMob core;private int scanAt,deadline;
        public ReturnToCoreGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            if(mob instanceof InfectedMob infected && infected.kind==InfectedKind.CORE)return false;
            if(emerging(mob)||mob.getTarget()!=null||mob.tickCount<scanAt||defenseCenter(mob)!=null)return false;scanAt=mob.tickCount+60;
            core=home(mob);if(core==null)return false;
            Vec3 anchor=core.position();var nodes=core.activeColonyNodes();
            if(!nodes.isEmpty())anchor=Vec3.atBottomCenterOf(nodes.stream().max(Comparator.comparingDouble(n->core.distanceToSqr(Vec3.atCenterOf(n.getBlockPos())))).get().getBlockPos());
            if(mob.distanceToSqr(core)>Math.pow(core.expansionRange()+16,2)){if(!walkTo(mob,core.position().add(4,0,0),1.1))return false;}
            else{
                double radius=duty(mob).equals("patrol")?8:20;
                for(int i=0;i<6;i++){
                    double angle=Math.floorMod(mob.getUUID().hashCode(),16)*Math.PI/8+(mob.tickCount/120.0)+i*Math.PI/3;
                    Vec3 desired=anchor.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
                    if(desired.distanceToSqr(core.position())>core.expansionRange()*core.expansionRange())continue;
                    if(walkTo(mob,desired,1.0)){deadline=mob.tickCount+160;return true;}
                }
                return false;
            }
            task(mob,"巡猎","沿母体或前线节点搜索新目标");deadline=mob.tickCount+200;return true;
        }
        @Override public boolean canContinueToUse(){return core!=null&&core.isAlive()&&mob.getTarget()==null&&mob.tickCount<deadline&&!mob.getNavigation().isDone()&&defenseCenter(mob)==null;}
        @Override public void stop(){mob.getNavigation().stop();}
    }
}

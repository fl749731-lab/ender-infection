package dev.hui.enderinfection.design;

import dev.hui.enderinfection.*;
import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.particle.FactionEffects;
import dev.hui.enderinfection.combat.CrystalAttacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;

/** Specialisations share the existing colony's orders, cap and transactional biomass reserve. */
public final class DesignBehavior {
    private DesignBehavior(){}
    public static int stage(InfectedKind k){return switch(k){case FORAGER->1;case SPOREWING_SCOUT,CARRIER,HATCH_SAC->2;case BURROWER,SPIDER_MOTHER,SHIELD_SENTINEL,THROAT_SPITTER,LEAP_AMBUSHER,ROOT_TURRET->3;case RESONANCE_PRIEST,SIEGEBREAKER->4;default->5;};}
    public static int price(InfectedKind k){return switch(k){case FORAGER->18;case SPOREWING_SCOUT->24;case BURROWER->38;case CARRIER->34;case SPIDER_MOTHER->52;case HATCH_SAC->42;case SHIELD_SENTINEL,LEAP_AMBUSHER->42;case THROAT_SPITTER->36;case ROOT_TURRET->44;case RESONANCE_PRIEST->60;case SIEGEBREAKER->120;case WALKING_NEST->240;case BURROW_KING->260;default->18;};}
    public static float filterDamage(InfectedMob mob,DamageSource source,float damage){
        if(mob.kind!=InfectedKind.SHIELD_SENTINEL||source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)||source.getSourcePosition()==null)return damage;
        Vec3 from=source.getSourcePosition().subtract(mob.position()).multiply(1,0,1).normalize();
        return mob.getLookAngle().multiply(1,0,1).normalize().dot(from)>.25?damage*.35F:damage;
    }
    public static boolean recruit(InfectedMob core,ServerLevel level){
        if(core.colonyBiomass()<64||core.isNoAi()||!core.isAlive()||core.colonyPopulation(level)>=MotherBrain.cap(core.motherStage()))return false;
        if(core.getRandom().nextInt(3)==0)return false;
        var candidates=new java.util.ArrayList<InfectedKind>();
        for(InfectedKind k:InfectedKind.values())if(k.designed()&&stage(k)<=core.motherStage()&&core.colonyBiomass()>=price(k)+16){
            long count=level.getEntitiesOfClass(InfectedMob.class,core.getBoundingBox().inflate(core.expansionRange()+16),e->e.isAlive()&&e.kind==k&&FactionAI.home(e)==core).size();
            if(count<(k==InfectedKind.WALKING_NEST||k==InfectedKind.BURROW_KING||k==InfectedKind.HATCH_SAC?1:3))candidates.add(k);
        }
        if(candidates.isEmpty())return false;
        return spawn(core,core,candidates.get(core.getRandom().nextInt(candidates.size())),level);
    }
    public static boolean spawn(InfectedMob source,InfectedMob core,InfectedKind kind,ServerLevel level){
        if(core==null||!core.isAlive()||core.isNoAi()||core.colonyPopulation(level)>=MotherBrain.cap(core.motherStage()))return false;
        for(int i=0;i<24;i++){
            double angle=i*Math.PI/12,radius=(source.getBbWidth()+kind.width)*.5+1.5;
            BlockPos column=BlockPos.containing(source.getX()+Math.cos(angle)*radius,source.getY(),source.getZ()+Math.sin(angle)*radius);
            if(!level.hasChunkAt(column))continue;
            for(int y=2;y>=-3;y--){BlockPos pos=column.offset(0,y,0);
                if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))continue;
                InfectedMob child=InfectedContent.TYPES.get(kind).get().create(level);if(child==null)return false;
                child.moveTo(pos.getX()+.5,pos.getY()+(kind==InfectedKind.SPOREWING_SCOUT?1:0),pos.getZ()+.5,source.getRandom().nextFloat()*360,0);
                if(!level.noCollision(child,child.getBoundingBox())||level.containsAnyLiquid(child.getBoundingBox()))continue;
                if(!core.spendForHatching(price(kind)))return false;
                child.getPersistentData().putUUID("ColonyMother",core.getUUID());child.getPersistentData().putString("ColonyDuty",kind==InfectedKind.SHIELD_SENTINEL?"guard":FactionAI.assignDuty(core));
                if(source.getTarget()!=null&&FactionAI.enemy(child,source.getTarget()))child.setTarget(source.getTarget());
                if(level.addFreshEntity(child)){FactionEffects.rune(level,child.position(),Math.min(3,kind.width));FactionEffects.shatter(level,child.position(),16);return true;}
                core.refundBiomass(price(kind));return false;
            }
        }
        return false;
    }
    public static void tick(InfectedMob mob,ServerLevel level){
        if(!mob.kind.designed())return;
        var target=mob.getTarget();var core=FactionAI.home(mob);
        if(mob.kind==InfectedKind.SPOREWING_SCOUT){
            if(mob.tickCount%60==0&&target!=null&&mob.hasLineOfSight(target)){target.addEffect(new MobEffectInstance(MobEffects.GLOWING,100));FactionAI.alert(mob,target);FactionEffects.rune(level,target.position(),1);}
        }
        if(mob.kind==InfectedKind.RESONANCE_PRIEST&&mob.tickCount%80==0){
            for(InfectedMob ally:level.getEntitiesOfClass(InfectedMob.class,mob.getBoundingBox().inflate(10),e->e.isAlive()&&e.kind!=InfectedKind.CORE)){ally.heal(3);ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));}
            FactionEffects.rune(level,mob.position(),3);
        }
        if((mob.kind==InfectedKind.HATCH_SAC||mob.kind==InfectedKind.SPIDER_MOTHER||mob.kind==InfectedKind.WALKING_NEST)&&core!=null&&mob.tickCount%dev.hui.enderinfection.biomass.ColonyEconomy.interval(400,core.colonyBiomass())==0)
            spawn(mob,core,mob.kind==InfectedKind.SPIDER_MOTHER?InfectedKind.EARLY_SPIDER:InfectedKind.FORAGER,level);
        if(mob.kind==InfectedKind.FORAGER&&target!=null&&mob.tickCount%100==0&&mob.hasLineOfSight(target))FactionAI.alert(mob,target);
        if(mob.kind.stationary())mob.getNavigation().stop();
    }
    public static void impact(InfectedMob mob,ServerLevel level){
        var target=mob.getTarget();if(target==null||!target.isAlive()||!FactionAI.enemy(mob,target)||!mob.hasLineOfSight(target))return;
        FactionEffects.rune(level,mob.position(),Math.min(3,mob.kind.width));
        switch(mob.kind){
            case THROAT_SPITTER,SPOREWING_SCOUT,RESONANCE_PRIEST -> {
                for(int i=-1;i<=1;i++)DesignProjectile.fire(level,mob,target.getEyePosition().add(i*.3,0,0),mob.kind.damage,mob.kind==InfectedKind.THROAT_SPITTER);
            }
            case ROOT_TURRET,BURROW_KING -> CrystalAttacks.cast(mob,InfectedKind.SKELETON,target,16);
            case BURROWER -> {CrystalAttacks.cast(mob,InfectedKind.SPIDER,target,12);melee(mob,target);}
            case LEAP_AMBUSHER -> melee(mob,target,2.3);
            case SIEGEBREAKER,WALKING_NEST -> {
                CrystalAttacks.cast(mob,InfectedKind.COW,target,12);FactionEffects.shatter(level,mob.position(),32);
                for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(3,1,3),e->FactionAI.enemy(mob,e)))melee(mob,e);
            }
            case HATCH_SAC -> {var core=FactionAI.home(mob);if(core!=null)spawn(mob,core,InfectedKind.FORAGER,level);}
            default -> melee(mob,target);
        }
    }
    private static void melee(InfectedMob mob,LivingEntity target){melee(mob,target,mob.kind.radius);}
    private static void melee(InfectedMob mob,LivingEntity target,double reach){
        if(mob.distanceTo(target)>reach+target.getBbWidth()*.5||!mob.hasLineOfSight(target))return;
        if(target.hurt(mob.damageSources().mobAttack(mob),mob.kind.damage)){Vec3 d=target.position().subtract(mob.position());target.knockback(.5,-d.x,-d.z);if(mob.level() instanceof ServerLevel s)FactionEffects.shatter(s,target.position().add(0,.8,0),8);}
    }
}

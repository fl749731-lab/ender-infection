package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.biomass.BiomassData;
import dev.hui.enderinfection.biomass.ColonyEconomy;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import dev.hui.enderinfection.particle.FactionEffects;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;

/** Infected animals are hostile monsters, not replacements for vanilla farm animals. */
public final class InfectedMob extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(InfectedMob.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EMERGING = SynchedEntityData.defineId(InfectedMob.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MOTHER_STAGE=SynchedEntityData.defineId(InfectedMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOTHER_STATE=SynchedEntityData.defineId(InfectedMob.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CLIMBING=SynchedEntityData.defineId(InfectedMob.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> MORPH_SPEED=SynchedEntityData.defineId(InfectedMob.class,EntityDataSerializers.FLOAT);
    private int expansionAllowance=4,expansionRemainder;
    private MotherBrain motherBrain;
    public MotherBrain motherBrain(){return motherBrain;}
    private int colonyBiomass;
    private BlockPos expansionAnchor;
    public static final int MAX_AUTO_NODES=16;
    public int maxAutoNodes(){return motherStage()<4?6:motherStage()==4?10:MAX_AUTO_NODES;}
    public int expansionRange(){return motherStage()<4?48:motherStage()==4?64:80;}
    private final java.util.Set<BlockPos> colonyNodes=new java.util.HashSet<>();
    private long nextTerritoryHarvest;
    private int territoryRemainder,territoryColumns,territoryWeight,lastTerritoryIncome,territoryFertility=100;
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(.68F,.16F,.91F),1.1F);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public final InfectedKind kind;
    private int attackTick, cooldown, teleportCooldown=60;
    private int emergenceTicks=32;
    private boolean emergenceStarted;
    private int personalBiomass;
    private final java.util.Set<java.util.UUID> waveVictims=new java.util.HashSet<>();
    private boolean expansionNodeCreated;
    public void feedBiomass(int amount){if(kind.early())personalBiomass=Math.min(1000,personalBiomass+Math.max(0,amount));}
    private Vec3 forward=new Vec3(0,0,1);
    public InfectedMob(EntityType<? extends InfectedMob> type, Level level, InfectedKind kind) {
        super(type,level); this.kind=kind; this.xpReward=kind==InfectedKind.CORE?50:12;
        if(kind==InfectedKind.CORE){colonyBiomass=ColonyEconomy.STARTER;motherBrain=new MotherBrain(this);applyMotherStage(1);nextTerritoryHarvest=level.getGameTime()+200;}
        if(kind==InfectedKind.SPIDER||kind==InfectedKind.EARLY_SPIDER)this.navigation=new WallClimberNavigation(this,level);
    }
    public static AttributeSupplier.Builder attributes(InfectedKind kind) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,kind.health)
            .add(Attributes.MOVEMENT_SPEED,kind.speed).add(Attributes.ATTACK_DAMAGE,kind.damage)
            .add(Attributes.FOLLOW_RANGE,40).add(Attributes.KNOCKBACK_RESISTANCE,kind==InfectedKind.CORE?1:kind==InfectedKind.COW?.6:.2);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ATTACKING,false);entityData.define(EMERGING,true);entityData.define(MOTHER_STAGE,1);entityData.define(MOTHER_STATE,0);entityData.define(CLIMBING,false);entityData.define(MORPH_SPEED,1F); }
    public boolean attacking() { return entityData.get(ATTACKING); }
    public void interruptForOrders(){getPersistentData().putInt("CrystalCastSequence",getPersistentData().getInt("CrystalCastSequence")+1);entityData.set(ATTACKING,false);attackTick=0;cooldown=Math.max(cooldown,10);getNavigation().stop();}
    public boolean emerging(){return entityData.get(EMERGING);}
    public int motherStage(){return entityData.get(MOTHER_STAGE);}
    @Override public net.minecraft.network.chat.Component getName(){return kind==InfectedKind.CORE&&!hasCustomName()?net.minecraft.network.chat.Component.translatable("entity.ender_infection.mother_stage"+motherStage()):super.getName();}
    public int colonyBiomass(){return colonyBiomass;}
    public PlayerTactics.Tactic motherTactic(){return motherBrain==null?PlayerTactics.Tactic.BALANCED:motherBrain.tactic();}
    public void receiveColonyBiomass(int value){
        if(kind!=InfectedKind.CORE||value<=0)return;int added=Math.min(10000-colonyBiomass,value);colonyBiomass+=added;
        expansionAllowance=Math.min(10000,expansionAllowance+added);expansionRemainder=0;
    }
    public float morphSpeed(){return entityData.get(MORPH_SPEED);}
    public int expansionAllowance(){return expansionAllowance;}
    private int upgradeReserve(){return motherStage()<5&&expansionReady()&&colonyBiomass>=ColonyEconomy.upgradePrice(motherStage())+16?ColonyEconomy.upgradePrice(motherStage())+16:0;}
    public void observeExpansion(ExpansionNodeEntity node){
        if(!getUUID().equals(node.ownerId()))return;
        colonyNodes.add(node.getBlockPos().immutable());
        var current=expansionAnchor==null||!level().hasChunkAt(expansionAnchor)?null:level().getBlockEntity(expansionAnchor);
        if(current==null||!(current instanceof ExpansionNodeEntity previous)||!getUUID().equals(previous.ownerId())||node.radius()>=previous.radius())expansionAnchor=node.getBlockPos().immutable();
    }
    public int colonyNodeCount(){
        colonyNodes.removeIf(pos->level().hasChunkAt(pos)&&(!(level().getBlockEntity(pos) instanceof ExpansionNodeEntity node)||!getUUID().equals(node.ownerId())));
        return colonyNodes.size();
    }
    public boolean nodeSiteAvailable(BlockPos pos){colonyNodeCount();return colonyNodes.stream().noneMatch(existing->existing.distSqr(pos)<49);}
    public java.util.List<ExpansionNodeEntity> activeColonyNodes(){
        colonyNodeCount();var nodes=new java.util.ArrayList<ExpansionNodeEntity>();
        for(BlockPos pos:colonyNodes)if(level().hasChunkAt(pos)&&position().distanceToSqr(Vec3.atCenterOf(pos))<=expansionRange()*expansionRange()&&level().getBlockEntity(pos) instanceof ExpansionNodeEntity node)nodes.add(node);
        return nodes;
    }
    public int territoryColumns(){return territoryColumns;}
    public int territoryWeight(){return territoryWeight;}
    public int lastTerritoryIncome(){return lastTerritoryIncome;}
    public int territoryFertility(){return territoryFertility;}
    public int collectTerritoryBiomass(ServerLevel level){
        if(kind!=InfectedKind.CORE||isNoAi()||!isAlive()||level.getGameTime()<nextTerritoryHarvest)return 0;
        nextTerritoryHarvest=level.getGameTime()+200;
        var territory=dev.hui.enderinfection.biomass.TerritoryYield.harvest(level,this);territoryColumns=territory.columns();territoryWeight=territory.weight();
        territoryFertility=territoryWeight==0?0:territory.nutrientCredits()*100/(territoryWeight*dev.hui.enderinfection.biomass.LandNutrients.CYCLES);
        int denominator=dev.hui.enderinfection.biomass.LandNutrients.DENOMINATOR;
        int credits=territoryRemainder+territory.earnedCredits(),amount=credits/denominator;territoryRemainder=credits%denominator;
        int before=colonyBiomass;receiveColonyBiomass(amount);lastTerritoryIncome=colonyBiomass-before;return lastTerritoryIncome;
    }
    public boolean expansionReady(){
        if(expansionAnchor==null||!level().hasChunkAt(expansionAnchor))return false;
        var block=level().getBlockEntity(expansionAnchor);
        return block instanceof ExpansionNodeEntity node&&getUUID().equals(node.ownerId())&&node.radius()>=(motherStage()==1?6:motherStage()==2?10:motherStage()==3?12:14)
            &&(motherStage()<3||colonyNodeCount()>=(motherStage()==3?3:6)&&territoryColumns>=(motherStage()==3?192:480));
    }
    public boolean spendBiomass(int amount){if(amount<=0||colonyBiomass<amount)return false;colonyBiomass-=amount;return true;}
    public void refundBiomass(int amount){if(amount>0)colonyBiomass=Math.min(10000,colonyBiomass+amount);}
    public boolean spendForHatching(int price){return colonyBiomass-price>=upgradeReserve()&&spendBiomass(price);}
    public boolean spendForRecovery(int price){return colonyBiomass-price>=Math.max(8,upgradeReserve())&&spendBiomass(price);}
    public boolean canExpand(){return canExpand(ColonyEconomy.EXPANSION);}
    public boolean canExpand(int amount){return amount>0&&colonyBiomass-amount>=8;}
    public boolean spendForExpansion(int amount){if(!canExpand(amount))return false;colonyBiomass-=amount;expansionAllowance=Math.max(0,expansionAllowance-amount);return true;}
    public void refundExpansion(int amount,int usedAllowance){refundBiomass(amount);expansionAllowance=Math.min(10000,expansionAllowance+usedAllowance);}

    public MotherBrain.State motherState(){return MotherBrain.State.values()[Math.max(0,Math.min(MotherBrain.State.values().length-1,entityData.get(MOTHER_STATE)))];}
    void setMotherState(MotherBrain.State state){entityData.set(MOTHER_STATE,state.ordinal());}
    @Override public EntityDimensions getDimensions(Pose pose){return kind==InfectedKind.CORE?EntityDimensions.scalable(MotherBrain.width(motherStage()),MotherBrain.height(motherStage())):super.getDimensions(pose);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(MOTHER_STAGE.equals(key)&&kind==InfectedKind.CORE)refreshDimensions();}
    @Override public boolean onClimbable(){return (kind==InfectedKind.SPIDER||kind==InfectedKind.EARLY_SPIDER)&&entityData.get(CLIMBING)||super.onClimbable();}
    private void applyMotherStage(int stage){
        stage=Math.max(1,Math.min(5,stage));double old=getMaxHealth();float fraction=(float)Math.max(.01,Math.min(1,getHealth()/Math.max(1,old)));
        entityData.set(MOTHER_STAGE,stage);
        if(getAttribute(Attributes.MAX_HEALTH)!=null)getAttribute(Attributes.MAX_HEALTH).setBaseValue(MotherBrain.health(stage));
        if(getAttribute(Attributes.ATTACK_DAMAGE)!=null)getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stage==1?4:stage==2?8:stage==3?14:stage==4?18:24);
        setHealth(getMaxHealth()*fraction);refreshDimensions();
    }
    boolean tryAdvanceMother(ServerLevel level){
        int next=motherStage()+1;if(next>5||!expansionReady()||colonyBiomass<ColonyEconomy.upgradePrice(motherStage())+16)return false;
        double half=MotherBrain.width(next)*.5;
        var box=new net.minecraft.world.phys.AABB(getX()-half,getY(),getZ()-half,getX()+half,getY()+MotherBrain.height(next),getZ()+half);
        if(!level.noCollision(this,box)||level.containsAnyLiquid(box))return false;
        int duration=ColonyEconomy.interval(40,colonyBiomass);
        if(!spendBiomass(ColonyEconomy.upgradePrice(motherStage())))return false;
        applyMotherStage(next);entityData.set(MORPH_SPEED,32F/duration);emergenceTicks=duration;emergenceStarted=false;entityData.set(EMERGING,true);getNavigation().stop();
        FactionEffects.rune(level,position(),MotherBrain.width(next));FactionEffects.shatter(level,position(),24);
        for(var player:level.players())if(distanceToSqr(player)<96*96)player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.ender_infection.mother_evolved",getName()));return true;
    }
    private double attackRadius(){return kind==InfectedKind.CORE?(motherStage()==1?4:motherStage()==2?8:10):kind.radius;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",emergenceTicks);tag.putInt("PersonalBiomass",personalBiomass);tag.putBoolean("ExpansionNodeCreated",expansionNodeCreated);if(expansionAnchor!=null)tag.putLong("ExpansionAnchor",expansionAnchor.asLong());if(kind==InfectedKind.CORE){tag.putInt("MotherStage",motherStage());tag.putInt("ColonyBiomass",colonyBiomass);tag.putInt("ExpansionAllowance",expansionAllowance);tag.putInt("ExpansionRemainder",expansionRemainder);tag.putFloat("MorphSpeed",morphSpeed());var nodes=new net.minecraft.nbt.ListTag();for(BlockPos pos:colonyNodes)nodes.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));tag.put("ColonyNodes",nodes);tag.putLong("NextTerritoryHarvest",nextTerritoryHarvest);tag.putInt("TerritoryRemainder",territoryRemainder);tag.putInt("TerritoryIncomeVersion",2);tag.putInt("TerritoryFertility",territoryFertility);tag.putInt("TerritoryColumns",territoryColumns);tag.putInt("TerritoryWeight",territoryWeight);tag.putInt("LastTerritoryIncome",lastTerritoryIncome);motherBrain.save(tag);}}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);if(getAttribute(Attributes.FOLLOW_RANGE)!=null&&getAttribute(Attributes.FOLLOW_RANGE).getBaseValue()<40)getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40);emergenceTicks=Math.max(0,Math.min(40,tag.getInt("EmergenceTicks")));entityData.set(EMERGING,emergenceTicks>0);emergenceStarted=false;personalBiomass=Math.max(0,tag.getInt("PersonalBiomass"));expansionNodeCreated=tag.getBoolean("ExpansionNodeCreated");expansionAnchor=tag.contains("ExpansionAnchor")?BlockPos.of(tag.getLong("ExpansionAnchor")):null;if(kind==InfectedKind.CORE){colonyBiomass=Math.max(0,Math.min(10000,tag.getInt("ColonyBiomass")));expansionAllowance=tag.contains("ExpansionAllowance")?Math.max(0,Math.min(10000,tag.getInt("ExpansionAllowance"))):4;expansionRemainder=Math.max(0,Math.min(3,tag.getInt("ExpansionRemainder")));entityData.set(MORPH_SPEED,tag.contains("MorphSpeed")?Math.max(.5F,Math.min(4F,tag.getFloat("MorphSpeed"))):1F);colonyNodes.clear();for(var entry:tag.getList("ColonyNodes",4))colonyNodes.add(BlockPos.of(((net.minecraft.nbt.LongTag)entry).getAsLong()));nextTerritoryHarvest=tag.contains("NextTerritoryHarvest")?Math.max(level().getGameTime(),Math.min(level().getGameTime()+200,tag.getLong("NextTerritoryHarvest"))):level().getGameTime()+200;territoryRemainder=tag.getInt("TerritoryIncomeVersion")>=2?Math.max(0,Math.min(dev.hui.enderinfection.biomass.LandNutrients.DENOMINATOR-1,tag.getInt("TerritoryRemainder"))):Math.max(0,Math.min(31,tag.getInt("TerritoryRemainder")))*dev.hui.enderinfection.biomass.LandNutrients.CYCLES;territoryFertility=tag.contains("TerritoryFertility")?Math.max(0,Math.min(100,tag.getInt("TerritoryFertility"))):100;territoryColumns=Math.max(0,tag.getInt("TerritoryColumns"));territoryWeight=Math.max(0,tag.getInt("TerritoryWeight"));lastTerritoryIncome=Math.max(0,tag.getInt("LastTerritoryIncome"));motherBrain.load(tag);applyMotherStage(tag.contains("MotherStage")?tag.getInt("MotherStage"):2);}}
    @Override public boolean isPushable(){return kind!=InfectedKind.CORE && super.isPushable();}
    private FactionAI.HuntGoal factionHunt;
    private FactionAI.Motion factionMotion;
    @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity other){return FactionAI.infected(other)||super.isAlliedTo(other);}
    @Override public boolean hurt(DamageSource source,float amount){if(FactionAI.infected(source.getEntity()))return false;if((kind==InfectedKind.CORE&&motherStage()>=4||kind==InfectedKind.SIEGE_BEAST)&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))amount=Math.min(amount,getMaxHealth()*.12F);boolean hit=super.hurt(source,amount);if(hit&&isAlive()&&source.getEntity() instanceof LivingEntity attacker)FactionAI.alert(this,attacker);return hit;}
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this)); goalSelector.addGoal(1,new CombatGoal());
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.7){@Override public boolean canUse(){return kind!=InfectedKind.CORE&&super.canUse();}});
        goalSelector.addGoal(3,new FactionAI.GuardMotherGoal(this));
        goalSelector.addGoal(2,new FactionAI.InvestigateGoal(this));
        factionHunt=new FactionAI.HuntGoal(this);
        factionMotion=new FactionAI.Motion(this,factionHunt);
        targetSelector.addGoal(1,factionHunt);
        goalSelector.addGoal(4,new FactionAI.ReturnToCoreGoal(this));
    }
    private void startAttack(LivingEntity target) {
        forward=target.position().subtract(position()).multiply(1,0,1).normalize();
        if (forward.lengthSqr()<.001) forward=new Vec3(0,0,1);
        attackTick=0;waveVictims.clear();entityData.set(ATTACKING,true);getNavigation().stop();
        setDeltaMovement(0,getDeltaMovement().y,0);
        triggerAnim("action","special");
        if(level() instanceof ServerLevel)dev.hui.enderinfection.combat.CrystalAttacks.cast(this,kind,target,kind.hitTick);
        level().playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,1,.65F);
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if(kind==InfectedKind.CORE)collectTerritoryBiomass(server);
        if(kind==InfectedKind.SPIDER||kind==InfectedKind.EARLY_SPIDER)entityData.set(CLIMBING,horizontalCollision);
        if(kind==InfectedKind.CORE&&!expansionNodeCreated&&tickCount%40==0)establishExpansionNode(server);
        if(motherBrain!=null)motherBrain.tick(server);
        if(emergenceTicks>0){
            FactionEffects.emerge(server,position(),emergenceTicks);
            if(!emergenceStarted){triggerAnim("action","infection");emergenceStarted=true;}
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            if(--emergenceTicks==0)entityData.set(EMERGING,false);return;
        }
        if(kind.early()&&personalBiomass>=40&&!attacking()&&evolve(server))return;
        FactionAI.support(this);
        if (cooldown>0)cooldown--;if(teleportCooldown>0)teleportCooldown--;
        if (!attacking())return;
        attackTick++;getNavigation().stop();
        float yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
        if (attackTick<kind.hitTick && attackTick%3==0)
            FactionEffects.gather(server,position().add(0,getBbHeight()*.6,0),kind==InfectedKind.CORE?5:2);
        if (kind==InfectedKind.COW && attackTick>=8 && attackTick<kind.hitTick) {
            double speed=getTarget()!=null && distanceTo(getTarget())>1.7?.38:0;
            setDeltaMovement(forward.x*speed,getDeltaMovement().y,forward.z*speed);
        }
        if ((kind==InfectedKind.PIG || kind==InfectedKind.SPIDER) && attackTick==6 && onGround())
            setDeltaMovement(forward.x*.6,.34,forward.z*.6);
        if (attackTick==kind.hitTick){
            impact(server);
            setDeltaMovement(0,getDeltaMovement().y,0);
        }
        if((kind==InfectedKind.SIEGE_BEAST||kind==InfectedKind.CORE&&motherStage()>=4)&&attackTick>=kind.hitTick&&(attackTick-kind.hitTick)%6==0)crystalWave(server);
        if(attackTick>=kind.duration){entityData.set(ATTACKING,false);cooldown=kind==InfectedKind.CORE?ColonyEconomy.interval(motherStage()==1?200:motherStage()==2?160:100,colonyBiomass):kind==InfectedKind.SIEGE_BEAST?90:kind==InfectedKind.SHEEP?100:35;}
    }
    private void establishExpansionNode(ServerLevel level){
        BlockPos center=blockPosition();
        for(BlockPos p:BlockPos.betweenClosed(center.offset(-4,-2,-4),center.offset(4,2,4))){
            if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof ExpansionNodeEntity node&&(node.ownerId()==null||getUUID().equals(node.ownerId()))){node.setOwner(getUUID());expansionNodeCreated=true;return;}
        }
        for(int[] offset:new int[][]{{3,0},{-3,0},{0,3},{0,-3}}){
            for(int dy=1;dy>=-2;dy--){
                BlockPos p=center.offset(offset[0],dy,offset[1]);
                if(dev.hui.enderinfection.block.ExpansionNodeEntity.replaceSurface(level,p,this,true)){expansionNodeCreated=true;return;}
            }
        }
    }
    private boolean evolve(ServerLevel server){
        Monster next=kind==InfectedKind.EARLY_ZOMBIE?EnderInfection.MUTANT_ZOMBIE.get().create(server):InfectedContent.TYPES.get(kind.evolved()).get().create(server);
        if(next==null)return false;
        next.moveTo(getX(),getY(),getZ(),getYRot(),getXRot());
        if(server.getBlockCollisions(next,next.getBoundingBox()).iterator().hasNext()||server.containsAnyLiquid(next.getBoundingBox()))return false;
        next.setHealth(Math.max(1,getHealth()/getMaxHealth()*next.getMaxHealth()));
        FactionAI.inheritOrders(this,next);
        next.setTarget(getTarget());if(hasCustomName())next.setCustomName(getCustomName());
        if(isPersistenceRequired())next.setPersistenceRequired();
        if(server.addFreshEntity(next)){FactionEffects.shatter(server,position(),20);discard();return true;}return false;
    }
    private boolean canHit(LivingEntity target) {
        return FactionAI.enemy(this,target);
    }
    private void impact(ServerLevel server) {
        if(kind==InfectedKind.SKELETON)return;
        if(kind==InfectedKind.EARLY_SKELETON){fireArrows(server);return;}
        if(kind==InfectedKind.CORE)motherBrain.tryIncubate(server);
        for(LivingEntity target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(attackRadius(),1.5,attackRadius()),this::canHit)){
            Vec3 delta=target.position().subtract(position());double dist=delta.horizontalDistance();
            if(dist>attackRadius() || Math.abs(delta.y)>2 || !hasLineOfSight(target))continue;
            if(kind!=InfectedKind.SHEEP && kind!=InfectedKind.CORE && dist>.001 && (delta.x*forward.x+delta.z*forward.z)/dist<.15)continue;
            if(target.hurt(damageSources().mobAttack(this),kind==InfectedKind.CORE?(float)getAttributeValue(Attributes.ATTACK_DAMAGE):kind.damage)){
                waveVictims.add(target.getUUID());
                target.knockback(kind==InfectedKind.COW?1.3:.65,-delta.x,-delta.z);
                if(kind==InfectedKind.SPIDER)target.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));
                if(kind==InfectedKind.SHEEP || kind==InfectedKind.CORE){
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,80,0));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
                }
            }
        }
        ring(server,kind==InfectedKind.CORE?attackRadius():kind==InfectedKind.SHEEP?5:2.2);
        server.playSound(null,blockPosition(),kind==InfectedKind.SHEEP?SoundEvents.ENDERMAN_SCREAM:SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.HOSTILE,1.3F,.7F);
    }
    private void crystalWave(ServerLevel server){
        double reach=kind==InfectedKind.SIEGE_BEAST?18:motherStage()==4?16:22;
        double radius=Math.min(reach,3+(attackTick-kind.hitTick)*.65);
        FactionEffects.rune(server,position(),radius);
        for(int i=0;i<12;i++){double angle=i*Math.PI/6;FactionEffects.shatter(server,position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius),2);}
        for(LivingEntity target:server.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius,3,radius),this::canHit)){
            Vec3 delta=target.position().subtract(position());
            if(delta.horizontalDistance()>radius||delta.horizontalDistance()<radius-3.5||Math.abs(delta.y)>3||waveVictims.contains(target.getUUID())||!hasLineOfSight(target))continue;
            float damage=kind==InfectedKind.SIEGE_BEAST?12:motherStage()==4?10:14;
            if(target.hurt(damageSources().mobAttack(this),damage)){waveVictims.add(target.getUUID());target.knockback(1.2,-delta.x,-delta.z);target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,1));}
        }
    }
    public boolean summonSiege(ServerLevel server){
        if(kind!=InfectedKind.CORE||motherStage()<4||colonyBiomass-ColonyEconomy.BOSS_UNIT<Math.max(24,upgradeReserve()))return false;
        int count=server.getEntitiesOfClass(InfectedMob.class,getBoundingBox().inflate(expansionRange()+16),e->e.kind==InfectedKind.SIEGE_BEAST&&e.isAlive()&&FactionAI.home(e)==this).size();
        if(count>=(motherStage()==4?1:2))return false;
        for(int i=0;i<24;i++){
            double angle=i*Math.PI/12;double radius=getBbWidth()*.5+3.2;
            BlockPos column=BlockPos.containing(getX()+Math.cos(angle)*radius,getY(),getZ()+Math.sin(angle)*radius);if(!server.hasChunkAt(column))continue;
            for(int y=2;y>=-3;y--){BlockPos pos=column.offset(0,y,0);if(!server.getBlockState(pos.below()).isFaceSturdy(server,pos.below(),Direction.UP))continue;
                InfectedMob beast=InfectedContent.TYPES.get(InfectedKind.SIEGE_BEAST).get().create(server);if(beast==null)return false;
                beast.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,random.nextFloat()*360,0);
                if(!server.noCollision(beast,beast.getBoundingBox())||server.containsAnyLiquid(beast.getBoundingBox()))continue;
                beast.getPersistentData().putUUID("ColonyMother",getUUID());beast.getPersistentData().putString("ColonyDuty","hunter");
                if(getTarget()!=null&&hasLineOfSight(getTarget()))beast.setTarget(getTarget());
                if(!spendForHatching(ColonyEconomy.BOSS_UNIT))return false;
                if(server.addFreshEntity(beast)){FactionEffects.rune(server,beast.position(),3);FactionEffects.shatter(server,beast.position(),28);return true;}
                refundBiomass(ColonyEconomy.BOSS_UNIT);
            }
        }
        return false;
    }
    boolean summonBrood(ServerLevel server){
        int nearby=server.getEntitiesOfClass(Mob.class,getBoundingBox().inflate(expansionRange()+16),e->e!=this&&FactionAI.infected(e)&&FactionAI.home(e)==this).size();
        int base=MotherBrain.brood(motherStage());
        int budget=Math.min(base,MotherBrain.cap(motherStage())-nearby);if(budget<=0)return false;
        int spawned=0;
        for(int attempt=0;attempt<20&&budget>0;attempt++){
            double angle=random.nextDouble()*Math.PI*2;double spawnRadius=Math.max(3.8,getBbWidth()*.5+2);
            Vec3 dest=position().add(Math.cos(angle)*spawnRadius,0,Math.sin(angle)*spawnRadius);
            BlockPos ground=BlockPos.containing(dest).below();
            if(!server.getBlockState(ground).isFaceSturdy(server,ground,Direction.UP))continue;
            InfectedKind host=motherBrain.pickHost();boolean advanced=motherStage()>=3&&random.nextInt(motherStage()>=4?2:3)==0&&colonyBiomass-upgradeReserve()>=ColonyEconomy.ADVANCED_UNIT;
            int price=advanced?ColonyEconomy.ADVANCED_UNIT:ColonyEconomy.EARLY_UNIT;if(colonyBiomass-price<upgradeReserve())break;
            Monster child=advanced&&host==InfectedKind.EARLY_ZOMBIE?EnderInfection.MUTANT_ZOMBIE.get().create(server):InfectedContent.TYPES.get(advanced?host.evolved():host).get().create(server);
            if(child==null)continue;child.moveTo(dest.x,dest.y,dest.z,random.nextFloat()*360,0);
            if(!server.noCollision(child,child.getBoundingBox())||server.containsAnyLiquid(child.getBoundingBox()))continue;
            if(getTarget()!=null&&hasLineOfSight(getTarget()))child.setTarget(getTarget());
            child.getPersistentData().putUUID("ColonyMother",getUUID());
            child.getPersistentData().putString("ColonyDuty",FactionAI.assignDuty(this));
            BroodLearning.stamp(child,host,motherBrain.tactic());
            if(!spendForHatching(price))break;
            if(server.addFreshEntity(child)){spawned++;budget--;FactionEffects.shatter(server,dest,10);}else refundBiomass(price);
        }
        return spawned>0;
    }
    boolean emergencyDefense(ServerLevel server){
        if(kind!=InfectedKind.CORE||colonyBiomass<ColonyEconomy.EARLY_UNIT)return false;
        int wave=motherStage()==1?3:motherStage()==2?4:motherStage()==3?6:motherStage()==4?8:10;
        int nearby=server.getEntitiesOfClass(Mob.class,getBoundingBox().inflate(24),FactionAI::infected).size();
        int fee=Math.max(ColonyEconomy.EARLY_UNIT,(colonyBiomass*7+9)/10);
        int planned=Math.min(wave,Math.min(fee/ColonyEconomy.EARLY_UNIT,MotherBrain.cap(motherStage())+wave-nearby));
        if(planned<=0||!spendBiomass(fee))return false;
        LivingEntity threat=getLastHurtByMob();
        if(threat==null||!FactionAI.enemy(this,threat)||!hasLineOfSight(threat))threat=getTarget();
        if(threat!=null&&(!FactionAI.enemy(this,threat)||!hasLineOfSight(threat)||distanceToSqr(threat)>256))threat=null;
        InfectedKind[] defenders={InfectedKind.EARLY_COW,InfectedKind.EARLY_SHEEP,InfectedKind.EARLY_SKELETON,InfectedKind.EARLY_SPIDER};
        int spawned=0;
        for(int attempt=0;attempt<64&&spawned<planned;attempt++){
            double angle=(attempt%16)*Math.PI/8;double radius=Math.max(3.2,getBbWidth()*.5+1.2)+(attempt/16)*.65;
            BlockPos column=BlockPos.containing(getX()+Math.cos(angle)*radius,getY(),getZ()+Math.sin(angle)*radius);
            if(!server.hasChunkAt(column))continue;
            for(int dy=1;dy>=-2;dy--){
                BlockPos pos=column.offset(0,dy,0);
                if(!server.getBlockState(pos.below()).isFaceSturdy(server,pos.below(),Direction.UP))continue;
                InfectedMob guard=InfectedContent.TYPES.get(defenders[spawned%defenders.length]).get().create(server);
                if(guard==null)break;
                guard.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,random.nextFloat()*360,0);
                if(!server.noCollision(guard,guard.getBoundingBox())||server.containsAnyLiquid(guard.getBoundingBox()))continue;
                guard.getPersistentData().putUUID("ColonyMother",getUUID());
                guard.getPersistentData().putUUID("DefendingMother",getUUID());
                guard.getPersistentData().putString("ColonyDuty","guard");
                BroodLearning.stamp(guard,defenders[spawned%defenders.length],PlayerTactics.Tactic.DEFEND_CORE);
                guard.getPersistentData().putLong("DefendUntil",server.getGameTime()+1200);
                guard.setTarget(threat);guard.emergenceTicks=10;guard.entityData.set(MORPH_SPEED,3.2F);
                if(server.addFreshEntity(guard)){spawned++;FactionEffects.rune(server,guard.position(),.8);FactionEffects.shatter(server,guard.position(),8);}
                break;
            }
        }
        // The ability spends 70% for a full wave; unsuccessful slots refund their proportional share.
        int charged=(fee*spawned+planned-1)/planned;refundBiomass(fee-charged);
        if(spawned==0)return false;
        entityData.set(ATTACKING,false);attackTick=0;cooldown=Math.max(cooldown,40);
        emergenceTicks=16;emergenceStarted=false;entityData.set(EMERGING,true);entityData.set(MORPH_SPEED,2F);
        getNavigation().stop();FactionEffects.rune(server,position(),motherStage()+1.5);FactionEffects.shatter(server,position(),28);
        server.playSound(null,blockPosition(),SoundEvents.ENDERMAN_SCREAM,SoundSource.HOSTILE,1.5F,.55F);
        return true;
    }
    private void fireArrows(ServerLevel server){
        LivingEntity target=getTarget();if(target==null||!target.isAlive()||!FactionAI.clearShot(this,target))return;
        Vec3 aim=target.getEyePosition().subtract(getEyePosition());
        for(int i=-1;i<=1;i++){
            Arrow arrow=new Arrow(level(),this);arrow.setPos(getX(),getEyeY()-.1,getZ());
            double angle=Math.toRadians(i*9);double dx=aim.x*Math.cos(angle)-aim.z*Math.sin(angle),dz=aim.x*Math.sin(angle)+aim.z*Math.cos(angle);
            arrow.setBaseDamage(kind.early()?2:4);if(!kind.early())arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
            arrow.shoot(dx,aim.y+aim.horizontalDistance()*.13,dz,1.5F,2F);server.addFreshEntity(arrow);
        }
        server.playSound(null,blockPosition(),SoundEvents.SKELETON_SHOOT,SoundSource.HOSTILE,1,.7F);ring(server,1.2);
    }
    private void ring(ServerLevel server,double radius){
        FactionEffects.rune(server,position(),radius);
        FactionEffects.shatter(server,position(),kind==InfectedKind.CORE?24:12);
        for(int i=0;i<40;i++){
            double angle=i*Math.PI*2/40;
            server.sendParticles(FactionParticles.SPORE.get(),getX()+Math.cos(angle)*radius,getY()+.25,getZ()+Math.sin(angle)*radius,1,0,.06,0,0);
        }
    }
    private void blinkBehind(LivingEntity target){
        Vec3 direction=target.getLookAngle().multiply(1,0,1).normalize();
        Vec3 dest=target.position().subtract(direction.scale(2.4));
        BlockPos ground=BlockPos.containing(dest).below();
        var box=getBoundingBox().move(dest.subtract(position()));
        if(level().getBlockState(ground).isFaceSturdy(level(),ground,Direction.UP) && level().noCollision(this,box) && !level().containsAnyLiquid(box)){
            if(level() instanceof ServerLevel server){FactionEffects.rune(server,position(),1.3);FactionEffects.shatter(server,position(),12);}
            teleportTo(dest.x,dest.y,dest.z);
            level().playSound(null,blockPosition(),SoundEvents.ENDERMAN_TELEPORT,SoundSource.HOSTILE,1,1);
            if(level() instanceof ServerLevel server){FactionEffects.rune(server,position(),1.3);FactionEffects.shatter(server,position(),12);}
        }
        teleportCooldown=140;
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        String prefix="animation.infected_"+kind.key+".";
        controllers.add(new AnimationController<>(this,"locomotion",4,state->{
            if(attacking()||emerging())return PlayState.STOP;
            return state.setAndContinue(RawAnimation.begin().thenLoop(prefix+(state.isMoving()?"walk":"idle")));
        }));
        controllers.add(new AnimationController<>(this,"action",0,state->PlayState.STOP).setAnimationSpeedHandler(entity->entity.emerging()?(double)entity.morphSpeed():1.0).triggerableAnim("special",RawAnimation.begin().thenPlay(prefix+"special")).triggerableAnim("infection",RawAnimation.begin().thenPlay(prefix+"infection")));
    }
    private final class CombatGoal extends Goal{
        CombatGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !emerging()&&(attacking()||(getTarget()!=null&&getTarget().isAlive()));}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void stop(){getNavigation().stop();factionMotion.reset();}
        @Override public void tick(){
            if(attacking())return;LivingEntity target=getTarget();if(target==null)return;
            getLookControl().setLookAt(target,25,25);double distance=distanceTo(target);
            boolean ranged=kind==InfectedKind.SKELETON||kind==InfectedKind.EARLY_SKELETON;
            if(ranged){
                if(!hasLineOfSight(target)){factionMotion.chase(target,true);return;}
                boolean clear=kind==InfectedKind.SKELETON?hasLineOfSight(target):FactionAI.clearShot(InfectedMob.this,target);
                if((distance<4||!clear)&&factionMotion.rangedPosition(target))return;
                if(cooldown==0&&distance<=kind.radius&&clear)startAttack(target);
                else if(distance>=4&&distance<=kind.radius&&clear)getNavigation().stop();
                else if(clear)factionMotion.chase(target,true);
                return;
            }
            if(kind==InfectedKind.ENDERMAN&&teleportCooldown==0&&distance>3.5&&distance<12){blinkBehind(target);return;}
            double specialReach=switch(kind){case COW->10;case PIG->7;case SHEEP->5;case SPIDER,ENDERMAN->8;default->kind.radius;};
            if(cooldown==0&&Math.abs(target.getY()-getY())<3&&distance<=(kind==InfectedKind.CORE?(motherStage()==1?8:motherStage()==2?16:motherStage()==5?22:motherStage()==4?16:20):kind==InfectedKind.SIEGE_BEAST?18:specialReach)&&hasLineOfSight(target))startAttack(target);
            else if(kind==InfectedKind.CORE)getNavigation().stop();
            else factionMotion.chase(target,false);
        }
    }
}

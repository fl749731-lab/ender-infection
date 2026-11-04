package dev.hui.enderinfection.particle;

import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Bounded cosmetic emitters for the ravager-derived siege beast. Damage stays in InfectedMob. */
public final class SiegeEffects {
    private SiegeEffects(){}
    private static Vec3 facing(InfectedMob beast){double yaw=Math.toRadians(beast.getYRot());return new Vec3(-Math.sin(yaw),0,Math.cos(yaw));}
    private static Vec3 point(InfectedMob beast,Vec3 direction,double ahead,double side,double height){return beast.position().add(direction.scale(ahead)).add(-direction.z*side,height,direction.x*side);}
    private static void spark(ServerLevel level,Vec3 point,Vec3 speed){level.sendParticles(FactionParticles.SIEGE_SPARK.get(),point.x,point.y,point.z,0,speed.x,speed.y,speed.z,1);}
    private static void shock(ServerLevel level,Vec3 center,double radius){level.sendParticles(FactionParticles.SIEGE_SHOCK.get(),center.x,center.y+.065,center.z,0,radius,0,0,1);}
    private static void dust(ServerLevel level,Vec3 foot,int amount){
        BlockPos below=BlockPos.containing(foot.add(0,-.15,0));var ground=level.getBlockState(below);
        if(ground.isAir()||!ground.getFluidState().isEmpty())return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,ground),foot.x,foot.y+.1,foot.z,Math.min(12,amount),.35,.12,.35,.04);
    }
    public static void idle(ServerLevel level,InfectedMob beast){
        Vec3 direction=facing(beast);
        if(beast.onGround()&&beast.getDeltaMovement().horizontalDistanceSqr()>.004&&beast.tickCount%10==0){
            int phase=(beast.tickCount/10)%2==0?1:-1;
            for(int side:new int[]{-1,1}){Vec3 foot=point(beast,direction,phase*side*.65,side*.65,.02);dust(level,foot,3);}
            level.playSound(null,beast.blockPosition(),SoundEvents.RAVAGER_STEP,SoundSource.HOSTILE,.75F,.72F);
        }
        if(beast.tickCount%30==0){
            for(int side:new int[]{-1,1})spark(level,point(beast,direction,1.65,side*.16,1.3),direction.scale(.025).add(0,.012,0));
            spark(level,point(beast,direction,-.45,.3,2.65),new Vec3(0,.015,0));
        }
    }
    public static void begin(ServerLevel level,InfectedMob beast,Vec3 direction){
        FactionEffects.rune(level,beast.position(),1.7);shock(level,beast.position(),1.35);
        level.playSound(null,beast.blockPosition(),SoundEvents.RAVAGER_ROAR,SoundSource.HOSTILE,1.15F,.6F);
        // The committed lane is visible before the beast lunges. Nothing here can hurt or replace blocks.
        for(int i=1;i<=3;i++){
            Vec3 p=point(beast,direction,2+i*1.4,0,.02);BlockPos ground=BlockPos.containing(p).below();
            if(level.hasChunkAt(ground)&&level.getBlockState(ground).isFaceSturdy(level,ground,net.minecraft.core.Direction.UP))FactionEffects.rune(level,new Vec3(p.x,ground.getY()+1,p.z),.55+i*.12);
        }
    }
    public static void windup(ServerLevel level,InfectedMob beast,Vec3 direction,int tick){
        if(tick%3!=0)return;
        Vec3 left=point(beast,direction,1.2,-.55,2.4),right=point(beast,direction,1.2,.55,2.4);
        FactionEffects.gather(level,tick%2==0?left:right,2);
        for(int i=0;i<6;i++){
            Vec3 p=left.lerp(right,i/5.0).add(0,i%2==0?.1:-.07,0);spark(level,p,new Vec3(0,.004,0));
        }
        if(tick>=10){dust(level,point(beast,direction,-.3,-.7,.01),3);dust(level,point(beast,direction,-.3,.7,.01),3);}
    }
    public static void impact(ServerLevel level,InfectedMob beast,Vec3 direction){
        Vec3 center=point(beast,direction,1.4,0,0);shock(level,center,3.2);
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;Vec3 p=center.add(Math.cos(angle)*1.7,0,Math.sin(angle)*1.7);dust(level,p,4);
            spark(level,p.add(0,.2,0),p.subtract(center).normalize().scale(.12).add(0,.08,0));
        }
        FactionEffects.shatter(level,center,18);
        level.playSound(null,beast.blockPosition(),SoundEvents.RAVAGER_ATTACK,SoundSource.HOSTILE,1.25F,.65F);
        level.playSound(null,beast.blockPosition(),SoundEvents.AMETHYST_CLUSTER_BREAK,SoundSource.HOSTILE,.9F,.7F);
    }
    public static void wave(ServerLevel level,InfectedMob beast,double radius){
        shock(level,beast.position(),radius);
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;Vec3 p=beast.position().add(Math.cos(angle)*radius,.08,Math.sin(angle)*radius);
            spark(level,p,new Vec3(Math.cos(angle)*.06,.03,Math.sin(angle)*.06));
            FactionEffects.shatter(level,p,2);
        }
    }
    public static void hit(ServerLevel level,Vec3 victim){FactionEffects.shatter(level,victim,8);for(int i=0;i<4;i++)spark(level,victim.add(0,.6,0),new Vec3((i%2==0?.1:-.1),.05,(i<2?.1:-.1)));}
    public static void wound(ServerLevel level,InfectedMob beast){FactionEffects.shatter(level,beast.position().add(0,1.5,0),5);}
    public static void death(ServerLevel level,InfectedMob beast){
        shock(level,beast.position(),4);FactionEffects.shatter(level,beast.position().add(0,1.6,0),28);
        level.sendParticles(FactionParticles.SPORE.get(),beast.getX(),beast.getY()+1.2,beast.getZ(),18,.9,.8,.9,.04);
        level.playSound(null,beast.blockPosition(),SoundEvents.AMETHYST_CLUSTER_BREAK,SoundSource.HOSTILE,1.1F,.55F);
    }
}

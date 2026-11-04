package dev.hui.enderinfection.particle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Common-side emitters; particle providers remain isolated on the client. */
public final class FactionEffects {
    private FactionEffects(){}
    public static void suppress(ServerLevel level,Vec3 center){
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,center.x,center.y+.5,center.z,16,.8,.5,.8,.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,center.x,center.y+.6,center.z,5,.4,.6,.4,.01);
        rune(level,center,1.2);
    }
    public static void rune(ServerLevel level,Vec3 center,double radius){
        level.sendParticles(FactionParticles.RUNE.get(),center.x,center.y+.055,center.z,0,radius,0,0,1);
    }
    public static void shatter(ServerLevel level,Vec3 center,int count){
        for(int i=0;i<Math.min(count,28);i++){
            double angle=i*Math.PI*2/Math.max(1,count);
            level.sendParticles(FactionParticles.SHARD.get(),center.x,center.y+.4,center.z,0,Math.cos(angle)*.16,.08+level.random.nextDouble()*.08,Math.sin(angle)*.16,1);
        }
    }
    public static void gather(ServerLevel level,Vec3 core,int count){
        for(int i=0;i<Math.min(count,12);i++){
            double angle=level.random.nextDouble()*Math.PI*2,radius=1.0+level.random.nextDouble();
            Vec3 start=core.add(Math.cos(angle)*radius,(level.random.nextDouble()-.5)*1.2,Math.sin(angle)*radius);
            Vec3 speed=core.subtract(start).scale(1.0/18);
            level.sendParticles(FactionParticles.MOTE.get(),start.x,start.y,start.z,0,speed.x,speed.y,speed.z,1);
        }
    }
    public static void emerge(ServerLevel level,Vec3 center,int tick){
        if(tick==32)rune(level,center,1.2);
        if(tick%4==0){gather(level,center.add(0,.9,0),2);level.sendParticles(FactionParticles.SPORE.get(),center.x,center.y+.6,center.z,3,.35,.4,.35,.015);}
    }
    public static void harvest(ServerLevel level,Vec3 from,Vec3 to){
        Vec3 velocity=to.subtract(from).scale(1.0/18);
        for(int i=0;i<8;i++)level.sendParticles(FactionParticles.MOTE.get(),from.x,from.y+.15*i/8,from.z,0,velocity.x,velocity.y,velocity.z,1);
        shatter(level,from,8);
    }
}

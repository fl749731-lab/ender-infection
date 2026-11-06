package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.ContainmentWarden;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static dev.hui.enderinfection.client.WardenParticle.Kind.*;

/** Reconciles server snapshots once per client tick; no animation timers or client damage. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class WardenVfxClient {
    private static final class State {
        final ContainmentWarden owner;
        int sequence,clock,mask,seen;
        boolean exposed,active;
        WardenParticle warning,core;
        final List<WardenParticle> particles=new ArrayList<>();
        State(ContainmentWarden owner){this.owner=owner;sequence=owner.visualSequence();clock=owner.visualClock();mask=owner.visualConsoles();exposed=owner.exposed();active=owner.visualActive();}
    }
    private static final Map<UUID,State> states=new HashMap<>();
    private static final EnumMap<WardenParticle.Kind,Integer> counts=new EnumMap<>(WardenParticle.Kind.class);
    private static ClientLevel current;
    private static int ticks,peakQuads,peakCore,renderedQuads;
    static void rendered(ContainmentWarden owner,int quads,boolean core){renderedQuads+=quads;if(core)peakCore=Math.max(peakCore,quads);}
    public static String statistics(){return "events="+counts+" peak_budget="+peakQuads+" core_quads="+peakCore+" rendered_quads="+renderedQuads;}
    public static void verify(){
        for(var kind:WardenParticle.Kind.values())if(counts.getOrDefault(kind,0)<(kind==PULSE?3:kind==CONSOLE?2:1))throw new IllegalStateException("Missing Warden VFX: "+kind+" "+statistics());
        if(states.values().stream().anyMatch(s->s.particles.stream().anyMatch(WardenParticle::isAlive)))throw new IllegalStateException("Detached Warden VFX survived death/retirement");
        if(peakQuads>96||peakCore>16||peakCore==0||renderedQuads==0)throw new IllegalStateException("Warden VFX budget/render failure: "+statistics());
    }
    private static WardenParticle spawn(Minecraft mc,State state,WardenParticle.Kind kind,Vec3 at,float yaw,int age){
        boolean sparse=mc.options.particles().get()!=ParticleStatus.ALL;
        var effect=WardenParticle.create(mc.level,state.owner,kind,at,yaw,age,sparse);
        if(effect!=null){mc.particleEngine.add(effect);state.particles.add(effect);counts.merge(kind,1,Integer::sum);}return effect;
    }
    private static void stop(WardenParticle particle){if(particle!=null)particle.remove();}
    private static void warning(Minecraft mc,State s){
        var kind=switch(s.owner.attack()){case CLAW->CLAW_WARNING;case STOMP->STOMP_WARNING;case BURST->BURST_WARNING;default->null;};
        int end=s.owner.attack()==ContainmentWarden.Attack.CLAW?17:s.owner.attack()==ContainmentWarden.Attack.STOMP?22:32;
        if(kind!=null&&s.owner.visualClock()<end)s.warning=spawn(mc,s,kind,s.owner.position(),s.owner.visualAim(),s.owner.visualClock());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.isPaused())return;
        if(mc.level!=current){states.values().forEach(s->s.particles.forEach(WardenParticle::remove));states.clear();current=mc.level;}
        if(current==null)return;ticks++;
        for(var entity:current.entitiesForRendering()){
            if(!(entity instanceof ContainmentWarden mob))continue;
            State s=states.get(mob.getUUID());boolean fresh=s==null||s.owner!=mob;
            if(fresh){if(s!=null)s.particles.forEach(WardenParticle::remove);s=new State(mob);states.put(mob.getUUID(),s);}
            s.particles.forEach(WardenParticle::reconcileClock);
            s.particles.removeIf(p->!p.isAlive());
            s.seen=ticks;int sequence=mob.visualSequence(),clock=mob.visualClock(),mask=mob.visualConsoles();boolean active=mob.visualActive()&&mob.isAlive();
            boolean restarted=!s.active&&active;
            if(!active){s.particles.forEach(WardenParticle::remove);s.warning=null;s.core=null;}
            else {
                if(fresh||restarted||sequence!=s.sequence){stop(s.warning);s.warning=null;warning(mc,s);}
                // A newly tracked / resumed entity shows the remaining warning, never a historic hit.
                if(!fresh&&!restarted){
                    int before=sequence==s.sequence?s.clock:0;
                    int[] hits=switch(mob.attack()){case CLAW->new int[]{17};case STOMP->new int[]{22};case BURST->new int[]{20,26,32};default->new int[0];};
                    for(int hit:hits)if(before<hit&&clock>=hit&&clock-hit<=2){
                        var kind=mob.attack()==ContainmentWarden.Attack.CLAW?CLAW:mob.attack()==ContainmentWarden.Attack.STOMP?SHOCK:PULSE;
                        float yaw=mob.visualAim()+(kind==PULSE?((hit-20)/6-1)*20:0);
                        spawn(mc,s,kind,mob.position(),yaw,clock-hit);
                    }
                    for(int i=0;i<2;i++)if((mask&~s.mask&1<<i)!=0){final State state=s;mob.visualConsoleAnchor(i).ifPresent(p->spawn(mc,state,CONSOLE,Vec3.atCenterOf(p).add(0,.55,0),0,0));}
                    if(mob.exposed()!=s.exposed)spawn(mc,s,mob.exposed()?CORE_OPEN:CORE_CLOSE,mob.position(),mob.yBodyRot,0);
                }
                if(mob.attack()==ContainmentWarden.Attack.NONE){stop(s.warning);s.warning=null;}
                if(mob.exposed()){
                    if(s.core==null||!s.core.isAlive())s.core=spawn(mc,s,CORE,mob.position(),mob.yBodyRot,0);
                }else {stop(s.core);s.core=null;}
            }
            s.sequence=sequence;s.clock=clock;s.mask=mask;s.exposed=mob.exposed();s.active=active;
            s.particles.removeIf(p->!p.isAlive());
            int budget=s.particles.stream().mapToInt(WardenParticle::quadBudget).sum();peakQuads=Math.max(peakQuads,budget);
        }
        states.values().removeIf(s->{if(ticks-s.seen>40||s.owner.isRemoved()){s.particles.forEach(WardenParticle::remove);return true;}return false;});
    }
}

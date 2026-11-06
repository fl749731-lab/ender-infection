package dev.hui.enderinfection.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hui.enderinfection.entity.ContainmentWarden;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Oriented, depth-tested atlas quads. One composite particle owns each complete effect. */
public final class WardenParticle extends TextureSheetParticle {
    public enum Kind { CLAW_WARNING, STOMP_WARNING, BURST_WARNING, CLAW, SHOCK, PULSE, CORE_OPEN, CORE, CORE_CLOSE, CONSOLE }
    private static SpriteSet materials;
    private static final int LAST_MATERIAL=6;
    private static final int AMBER=0xE9B34F, VIOLET=0xC175EF, WHITE=0xFFF3FF, MINT=0x86D8C1;
    final ContainmentWarden owner;
    final Kind kind;
    private final float yaw;
    private final boolean sparse;
    private final int sequence,impactClock;
    private int submitted;
    private VertexConsumer vertices;
    private Vec3 eye;

    private WardenParticle(ClientLevel level, ContainmentWarden owner, Kind kind, Vec3 at, float yaw, int initialAge, boolean sparse) {
        super(level,at.x,at.y,at.z,0,0,0);
        this.owner=owner;this.kind=kind;this.yaw=yaw;this.sparse=sparse;hasPhysics=false;
        sequence=owner.visualSequence();impactClock=owner.visualClock()-initialAge;
        lifetime=switch(kind){case CLAW_WARNING->17;case STOMP_WARNING->22;case BURST_WARNING->33;case CORE->120;case CONSOLE->18;case CORE_OPEN,CORE_CLOSE->12;case CLAW->10;case SHOCK->12;case PULSE->12;};
        age=initialAge;setSize(26,8);setPos(at.x,at.y,at.z);setSprite(materials.get(3,LAST_MATERIAL));
    }
    static WardenParticle create(ClientLevel level, ContainmentWarden owner, Kind kind, Vec3 at, float yaw, int age, boolean sparse) {
        return materials==null?null:new WardenParticle(level,owner,kind,at,yaw,age,sparse);
    }
    public boolean warning(){return kind==Kind.CLAW_WARNING||kind==Kind.STOMP_WARNING||kind==Kind.BURST_WARNING;}
    /** Reconcile before another hit is emitted, including packets arriving after the particle tick. */
    void reconcileClock(){
        if((kind==Kind.CLAW||kind==Kind.SHOCK||kind==Kind.PULSE)&&owner.visualSequence()==sequence&&owner.attack()!=ContainmentWarden.Attack.NONE){
            age=Math.max(age,owner.visualClock()-impactClock);if(age>=lifetime)remove();
        }
    }
    /** Conservative simultaneous quad budget, including decorative fragments. */
    public int quadBudget(){return switch(kind){case CLAW_WARNING->20;case STOMP_WARNING->24;case BURST_WARNING->40;case CLAW->12;case SHOCK->32;case PULSE->20;case CORE->16;case CONSOLE->10;case CORE_OPEN,CORE_CLOSE->16;};}
    @Override public void tick(){
        xo=x;yo=y;zo=z;
        if(owner.isRemoved()||!owner.isAlive()||!owner.visualActive()){remove();return;}
        if(warning()){
            if(owner.attack()==ContainmentWarden.Attack.NONE){remove();return;}
            age=owner.visualClock();if(age>=lifetime)remove();
        }else if(kind==Kind.CORE){
            if(!owner.exposed()){remove();return;}age=(age+1)%80;
        }else if(++age>=lifetime)remove();
        reconcileClock();
        if(kind==Kind.CORE||kind==Kind.CORE_OPEN||kind==Kind.CORE_CLOSE)setPos(owner.getX(),owner.getY(),owner.getZ());
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    @Override protected int getLightColor(float partialTick){return 0xF000F0;}
    private static Vec3 forward(double yaw){double a=Math.toRadians(yaw);return new Vec3(-Math.sin(a),0,Math.cos(a));}
    private static Vec3 right(double yaw){double a=Math.toRadians(yaw);return new Vec3(-Math.cos(a),0,-Math.sin(a));}
    private void quad(Vec3 center,Vec3 u,Vec3 v,int material,int color,float opacity){
        surface(new Vec3[]{center.subtract(u).subtract(v),center.subtract(u).add(v),center.add(u).add(v),center.add(u).subtract(v)},material,color,opacity);
    }
    private void surface(Vec3[] corners,int material,int color,float opacity){
        if(opacity<=.002F)return;submitted++;
        var sprite=materials.get(material,LAST_MATERIAL);
        float[] us={sprite.getU1(),sprite.getU1(),sprite.getU0(),sprite.getU0()},vs={sprite.getV1(),sprite.getV0(),sprite.getV0(),sprite.getV1()};
        float red=(color>>16&255)/255F,green=(color>>8&255)/255F,blue=(color&255)/255F;
        for(int i=0;i<4;i++){
            var p=corners[i].subtract(eye);
            vertices.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(us[i],vs[i]).setColor(red,green,blue,opacity).setLight(0xF000F0);
        }
    }
    private void waveStrip(Vec3 center,double inner,double outer,double low,double high,double from,double sweep,int cells,int color,float opacity){
        inner=Math.max(.08,inner);outer=Math.max(.09,outer);
        for(int i=0;i<cells;i++){
            double a=from+sweep*i/cells,b=from+sweep*(i+1)/cells;
            surface(new Vec3[]{center.add(forward(a).scale(inner)).add(0,low,0),
                center.add(forward(b).scale(inner)).add(0,low,0),
                center.add(forward(b).scale(outer)).add(0,high,0),
                center.add(forward(a).scale(outer)).add(0,high,0)},4,color,opacity);
        }
    }
    private void line(Vec3 a,Vec3 b,double width,int color,float opacity){
        var u=b.subtract(a).scale(.5);var perpendicular=new Vec3(-u.z,0,u.x).normalize().scale(width);
        quad(a.add(b).scale(.5),u,perpendicular,2,color,opacity);
    }
    private void groundArc(Vec3 at,double r,double from,double sweep,int count,int color,float opacity,double width){
        for(int i=0;i<count;i++){
            double a=from+sweep*i/count,b=from+sweep*(i+.80)/count;
            line(at.add(forward(a).scale(r)),at.add(forward(b).scale(r)),width,color,opacity);
        }
    }
    private void billboard(Vec3 at,double size,int material,int color,float opacity,Camera camera){
        var u=new org.joml.Vector3f(1,0,0).rotate(camera.rotation()).mul((float)size);
        var v=new org.joml.Vector3f(0,1,0).rotate(camera.rotation()).mul((float)size);
        quad(at,new Vec3(u.x,u.y,u.z),new Vec3(v.x,v.y,v.z),material,color,opacity);
    }
    private void chips(Vec3 at,float t,int count,Camera camera,int color){
        if(sparse)count=Math.min(2,count);
        for(int i=0;i<count;i++){
            double a=i*2.399963;double speed=.6+(i%3)*.24;
            var offset=new Vec3(Math.cos(a)*speed*t,Math.sin(t*Math.PI)*(.5+i%3*.15),Math.sin(a)*speed*t);
            billboard(at.add(offset),.07*(1-t)+.02,1,color,(1-t)*.9F,camera);
        }
    }
    @Override public void render(VertexConsumer buffer,Camera camera,float partialTick){
        if(!isAlive()||owner.isRemoved()||!owner.visualActive())return;
        vertices=buffer;eye=camera.getPosition();submitted=0;
        var root=new Vec3(Mth.lerp(partialTick,xo,x),Mth.lerp(partialTick,yo,y),Mth.lerp(partialTick,zo,z));
        float clock=age+partialTick,t=Math.min(1,clock/lifetime),fade=(1-t)*(1-t);
        var floor=root.add(0,.045,0);var f=forward(yaw);var r=right(yaw);
        switch(kind){
            case CLAW_WARNING->{
                float a=.55F+.22F*(float)Math.sin(clock*.6);groundArc(floor,4.2,yaw-60,120,12,AMBER,a,.045);
                for(int side:new int[]{-1,1})for(int i=0;i<4;i++)line(floor.add(forward(yaw+side*60).scale(i+.15)),floor.add(forward(yaw+side*60).scale(i+.75)),.035,AMBER,a);
            }
            case STOMP_WARNING->groundArc(floor,5.5,0,360,24,AMBER,.55F+.24F*(float)Math.sin(clock*.55),.055);
            case BURST_WARNING->{
                float charge=Math.min(1,clock/20);var core=root.add(f.scale(.70)).add(0,2.125,0);
                for(int i=0;i<4;i++){
                    double angle=clock*.18+i*Math.PI/2,rad=.15+.62*(1-charge);
                    billboard(core.add(r.scale(Math.cos(angle)*rad)).add(0,Math.sin(angle)*rad,0),.035+charge*.025,3,i%2==0?WHITE:VIOLET,.35F+charge*.55F,camera);
                }
                for(int fan=-1;fan<=1;fan++){
                    if(clock>=26+fan*6)continue;
                    double angle=yaw+fan*20;float a=.58F+.20F*(float)Math.sin(clock*.6-fan);
                    groundArc(floor,12,angle-10,20,4,AMBER,a,.055);
                    for(int side:new int[]{-1,1})for(int i=0;i<4;i++)line(floor.add(forward(angle+side*10).scale(.5+i*2.9)),floor.add(forward(angle+side*10).scale(2.8+i*2.9)),.04,AMBER,a);
                }
            }
            case CLAW->{
                // Anatomical right hand: three curved atlas trails, offset like the three fingers.
                var hand=root.add(r.scale(.82)).add(f.scale(.8)).add(0,1.6,0);
                for(int i=0;i<3;i++)quad(hand.add(r.scale((i-1)*.15)).add(0,(i-1)*.23,0).add(f.scale(t*.85)),r.scale(1.1+t*.55).add(f.scale(.3)),new Vec3(0,.62,0),0,i==1?WHITE:VIOLET,Math.min(1,fade*1.5F));
                if(clock<3)billboard(hand.add(f.scale(.8)),.3*(1-clock/3),3,WHITE,1-clock/3,camera);
                chips(hand,t,8,camera,VIOLET);
            }
            case SHOCK->{
                double radius=.4+Math.min(1,clock/10)*5.1;
                // Broad, broken outer wake plus a delayed thin inner crest; both stay near the floor.
                quad(floor,new Vec3(radius,0,0),new Vec3(0,0,radius),6,VIOLET,(1-t)*.9F);
                double inner=radius*(.72+.08*t);
                quad(floor.add(0,.008,0),new Vec3(inner,0,0),new Vec3(0,0,inner),6,WHITE,fade*.85F);
                double crest=.12+.30*Math.sin(t*Math.PI);
                waveStrip(floor,radius-.45,radius-.06,.03,crest,0,360,12,VIOLET,(1-t)*.65F);
                waveStrip(floor,radius-.06,radius+.14,crest,.025,0,360,12,WHITE,fade*.6F);
                var foot=floor.add(r.scale(.45)).add(f.scale(.12));
                if(clock<4)for(int i=0;i<4;i++){var axis=forward(yaw+i*45);line(foot.subtract(axis.scale(.55)),foot.add(axis.scale(.55)),.05,WHITE,1-clock/4);}
                chips(foot,t,2,camera,VIOLET);
            }
            case PULSE->{
                double radius=.5+11.5*Math.min(1,clock/10);var center=root.add(0,.06,0);
                // Four cross-section slopes form a rising back, crest, forward curl and falling face.
                // The height swells then collapses while the lip rolls forward; this is real 3D geometry.
                double height=.30+.82*Math.pow(Math.sin(t*Math.PI),.7),curl=.22+.30*Math.sin(t*Math.PI);
                double[] distance={radius-1.25,radius-.58,radius,radius+curl,radius+curl+.25};
                double[] heights={.035,height*.30,height,height*.76,.035};
                for(int strip=0;strip<4;strip++)waveStrip(center,distance[strip],distance[strip+1],heights[strip],heights[strip+1],yaw-10,20,5,strip==2?WHITE:VIOLET,(1-t)*(strip==0?.35F:strip==2?.83F:.66F));
            }
            case CORE,CORE_OPEN,CORE_CLOSE->{
                var facing=forward(owner.yBodyRot);var side=right(owner.yBodyRot);
                var core=root.add(0,2.125,0).add(facing.scale(.70));
                float a=kind==Kind.CORE?.82F:fade;double radius=kind==Kind.CORE?.4+.025*Math.sin(clock*.15):kind==Kind.CORE_OPEN?.23+t*.38:.5*(1-t);
                for(int ring=0;ring<2;ring++)for(int i=0;i<6;i++){
                    double angle=i*Math.PI/3+clock*.025*(ring==0?1:-1),rad=radius+ring*.11;
                    var at=core.add(side.scale(Math.cos(angle)*rad)).add(0,Math.sin(angle)*rad,0);
                    var tangent=side.scale(-Math.sin(angle)*.10).add(0,Math.cos(angle)*.10,0);
                    // Reverse the radial axis so these upright atlas quads face the chest front.
                    var normal=side.scale(-Math.cos(angle)*.04).add(0,-Math.sin(angle)*.04,0);
                    quad(at,tangent,normal,2,ring==0?WHITE:VIOLET,a);
                }
                for(int i=0;i<4;i++){double angle=i*Math.PI/2+clock*.035;billboard(core.add(side.scale(Math.cos(angle)*radius*.65)).add(0,Math.sin(angle)*radius*.65,0),.035,3,VIOLET,a,camera);}
            }
            case CONSOLE->{
                for(int i=0;i<2;i++)billboard(root.add((i==0?-.15:.15),.12+t*.5,0),.36*(1-t)+.12,5,MINT,fade,camera);
                chips(root,t,8,camera,MINT);
            }
        }
        WardenVfxClient.rendered(owner,submitted,kind==Kind.CORE);
        if(submitted>quadBudget()&&Boolean.getBoolean("ender_infection.wardenVfxCheck"))throw new IllegalStateException("Warden VFX exceeded per-effect quad budget: "+kind+" "+submitted);
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType>{
        public Provider(SpriteSet sprites){materials=sprites;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double vx,double vy,double vz){
            // Direct /particle use stays a harmless atlas mote; combat uses explicit oriented patterns.
            return new TextureSheetParticle(level,x,y,z,0,0,0){
                {setSprite(materials.get(3,LAST_MATERIAL));lifetime=12;quadSize=.07F;hasPhysics=false;}
                @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
                @Override protected int getLightColor(float partialTick){return 0xF000F0;}
            };
        }
    }
}

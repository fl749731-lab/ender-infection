package dev.hui.enderinfection.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Four sprite-backed faction effects. Client-only; full-bright rendering does not create world lights. */
public final class FactionParticle extends TextureSheetParticle {
    public enum Kind { SHARD,SPORE,MOTE,RUNE }
    private final Kind kind;
    private final double startX,startY,startZ,targetX,targetY,targetZ;
    private final float targetSize;
    private FactionParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,Kind kind){
        super(level,x,y,z,0,0,0);this.kind=kind;this.hasPhysics=false;pickSprite(sprites);
        startX=x;startY=y;startZ=z;
        lifetime=switch(kind){case SHARD->20+random.nextInt(10);case SPORE->32+random.nextInt(16);case MOTE->18;case RUNE->14;};
        targetX=x+vx*lifetime;targetY=y+vy*lifetime;targetZ=z+vz*lifetime;
        xd=vx;yd=vy;zd=vz;friction=.94F;
        targetSize=kind==Kind.RUNE?(float)Math.max(.7,Math.min(8,Math.abs(vx))):.12F;
        quadSize=switch(kind){case SHARD->.10F+random.nextFloat()*.09F;case SPORE->.06F+random.nextFloat()*.06F;case MOTE->.10F;case RUNE->targetSize*.2F;};
        if(kind==Kind.SPORE){gravity=-.008F;xd*=.35;yd+=.009;zd*=.35;}
        if(kind==Kind.SHARD)gravity=.035F;
        if(kind==Kind.RUNE){xd=yd=zd=0;}
        roll=random.nextFloat()*(float)(Math.PI*2);oRoll=roll;
    }
    @Override public void tick(){
        super.tick();float t=(float)age/lifetime;
        alpha=kind==Kind.RUNE?.72F*(1-t):Math.min(1,Math.min(t*6,(1-t)*4));
        if(kind==Kind.SHARD){oRoll=roll;roll+=.18F;}
        if(kind==Kind.RUNE){quadSize=targetSize*(.2F+.8F*t);oRoll=roll;roll+=.012F;}
        if(kind==Kind.MOTE){
            double q=Math.min(1,t);double swirl=Math.sin(q*Math.PI)*.22;
            setPos(Mth.lerp(q,startX,targetX)+Math.sin(q*9)*swirl,Mth.lerp(q,startY,targetY),Mth.lerp(q,startZ,targetZ)+Math.cos(q*9)*swirl);
        }
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    @Override protected int getLightColor(float partialTick){return 0xF000F0;}
    @Override public void render(VertexConsumer buffer,Camera camera,float partialTick){
        if(kind!=Kind.RUNE){super.render(buffer,camera,partialTick);return;}
        var eye=camera.getPosition();
        float px=(float)(Mth.lerp(partialTick,xo,x)-eye.x),py=(float)(Mth.lerp(partialTick,yo,y)-eye.y),pz=(float)(Mth.lerp(partialTick,zo,z)-eye.z);
        Quaternionf rotation=new Quaternionf().rotationX((float)Math.PI/2).rotateZ(Mth.lerp(partialTick,oRoll,roll));
        Vector3f[] corners={new Vector3f(-1,-1,0),new Vector3f(-1,1,0),new Vector3f(1,1,0),new Vector3f(1,-1,0)};
        float[] us={getU1(),getU1(),getU0(),getU0()},vs={getV1(),getV0(),getV0(),getV1()};
        for(int i=0;i<4;i++){
            corners[i].rotate(rotation).mul(getQuadSize(partialTick)).add(px,py,pz);
            buffer.vertex(corners[i].x,corners[i].y,corners[i].z).uv(us[i],vs[i]).color(rCol,gCol,bCol,alpha).uv2(getLightColor(partialTick)).endVertex();
        }
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType>{
        private final SpriteSet sprites;private final Kind kind;
        public Provider(SpriteSet sprites,Kind kind){this.sprites=sprites;this.kind=kind;}
        @Override public Particle createParticle(SimpleParticleType options,ClientLevel level,double x,double y,double z,double vx,double vy,double vz){return new FactionParticle(level,x,y,z,vx,vy,vz,sprites,kind);}
    }
}

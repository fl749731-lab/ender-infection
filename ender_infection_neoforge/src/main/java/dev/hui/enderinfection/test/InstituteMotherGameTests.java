package dev.hui.enderinfection.test;

import com.google.gson.*;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.rift.RiftAnchorEntity;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ender_mother_chamber_checks")
@PrefixGameTestTemplate(false)
public final class InstituteMotherGameTests {
    private record Fixture(ServerLevel level,RiftAnchorEntity anchor,AABB bounds){
        InfectedMob mother(){
            var tag=anchor.saveWithoutMetadata(level.registryAccess());
            return tag.hasUUID("Mother")&&level.getEntity(tag.getUUID("Mother")) instanceof InfectedMob m?m:null;
        }
        int count(){return level.getEntitiesOfClass(InfectedMob.class,bounds,m->m.kind==InfectedKind.CORE).size();}
        void reload(){anchor.load(anchor.saveWithoutMetadata(level.registryAccess()));}
    }
    private static Fixture build(GameTestHelper h){
        var level=h.getLevel();JsonObject data;
        try(var reader=level.getServer().getResourceManager().getResourceOrThrow(CentralInstitute.id("central_institute.json")).openAsReader()){
            data=JsonParser.parseReader(reader).getAsJsonObject();
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        JsonObject room=null;
        for(var row:data.getAsJsonArray("rooms"))if(row.getAsJsonObject().get("id").getAsString().equals("F7"))room=row.getAsJsonObject();
        h.assertTrue(room!=null,"Missing F7 room");
        var corner=h.absolutePos(new BlockPos(8,2,8));
        int w=room.get("width").getAsInt(),d=room.get("length").getAsInt(),height=room.get("height").getAsInt();
        var origin=corner.offset(-room.get("x").getAsInt(),-room.get("y").getAsInt(),-room.get("z").getAsInt());
        var clip=new BoundingBox(corner.getX(),corner.getY(),corner.getZ(),corner.getX()+w-1,corner.getY()+height,corner.getZ()+d-1);
        for(int x=(clip.minX()>>4)-1;x<=(clip.maxX()>>4)+1;x++)for(int z=(clip.minZ()>>4)-1;z<=(clip.maxZ()>>4)+1;z++){
            level.setChunkForced(x,z,true);level.getChunk(x,z);
        }
        for(var tile:CentralInstitute.tiles()){
            var at=origin.offset(tile.x(),tile.y(),tile.z());
            var box=new BoundingBox(at.getX(),at.getY(),at.getZ(),at.getX()+tile.width()-1,at.getY()+tile.height()-1,at.getZ()+tile.length()-1);
            if(box.intersects(clip))h.assertTrue(CentralInstitute.template(level,tile).placeInWorld(level,at,at,CentralInstitute.settings(clip),RandomSource.create(17),CentralInstitute.UPDATE_FLAGS),"F7 template failed");
        }
        var a=data.getAsJsonObject("mother_chamber").getAsJsonArray("anchor_pos");
        var anchor=level.getBlockEntity(origin.offset(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));
        h.assertTrue(anchor instanceof RiftAnchorEntity,"F7 has no actual anchor block entity");
        return new Fixture(level,(RiftAnchorEntity)anchor,new AABB(corner.getX(),corner.getY(),corner.getZ(),corner.getX()+w,corner.getY()+height,corner.getZ()+d));
    }
    @GameTest(template="empty64",templateNamespace="ender_mother_chamber_checks",timeoutTicks=320,batch="institute_mother_persistence")
    public static void motherAndAnchorGenerateOncePayEnergyAndPersistAfterDeath(GameTestHelper h){
        var f=build(h);var identity=new java.util.UUID[1];
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.mother()!=null&&f.anchor.energy()>=26,"Waiting for generated mother and paid recharge"))
            .thenExecute(()->{
                var mother=f.mother();identity[0]=mother.getUUID();
                h.assertTrue(mother.motherStage()==2&&mother.instituteContained()&&!mother.isNoAi(),"Mother is the wrong stage or has disabled AI");
                h.assertTrue(mother.colonyBiomass()+f.anchor.energy()==88,"Anchor gained free energy or containment spent biomass on hatching");
                var snapshot=mother.saveWithoutId(new CompoundTag());var copy=InfectedContent.TYPES.get(InfectedKind.CORE).get().create(f.level);copy.load(snapshot);
                h.assertTrue(copy.instituteContained()&&copy.motherStage()==2&&copy.getUUID().equals(identity[0]),"Native mother NBT lost the containment binding");
                f.reload();h.assertTrue(f.mother()!=null&&f.mother().getUUID().equals(identity[0]),"Anchor reload lost its assigned mother");
            }).thenIdle(45).thenExecute(()->{
                h.assertTrue(f.count()==1&&f.mother().getUUID().equals(identity[0]),"Reload duplicated the mother");
                h.assertTrue(f.mother().colonyBiomass()+f.anchor.energy()==88,"Recharge stopped being a paid transfer");
                f.mother().discard();f.reload();
            }).thenIdle(45).thenExecute(()->{
                h.assertTrue(f.count()==0&&f.mother()==null,"Dead mother was regenerated");
                h.assertTrue(f.anchor.saveWithoutMetadata(f.level.registryAccess()).getCompound("InstituteChamber").getBoolean("Initialized"),"Death lost the initialization tombstone");
                System.out.println("INSTITUTE_MOTHER_NATIVE_PASS once=true paid_energy=true reload=true death_no_respawn=true");
            }).thenSucceed();
    }
    @GameTest(template="empty64",templateNamespace="ender_mother_chamber_checks",timeoutTicks=240,batch="institute_mother_breach")
    public static void brokenShellReleasesTheExistingMotherAndPreservesTheLink(GameTestHelper h){
        var f=build(h);var identity=new java.util.UUID[1];
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.mother()!=null,"Waiting for contained mother"))
            .thenExecute(()->{
                identity[0]=f.mother().getUUID();h.assertTrue(f.mother().instituteContained(),"New mother was not contained");
                f.level.setBlock(f.anchor.getBlockPos().offset(-10,0,0),Blocks.AIR.defaultBlockState(),3);
            }).thenWaitUntil(()->h.assertTrue(!f.mother().instituteContained(),"Waiting for shell breach detection"))
            .thenExecute(f::reload).thenIdle(25).thenExecute(()->{
                h.assertTrue(f.count()==1&&f.mother().getUUID().equals(identity[0])&&!f.mother().instituteContained(),"Breach reload replaced or recaged the mother");
                h.assertTrue(f.anchor.saveWithoutMetadata(f.level.registryAccess()).getCompound("InstituteChamber").getBoolean("Breached"),"Breach did not persist");
                f.mother().discard();System.out.println("INSTITUTE_MOTHER_NATIVE_PASS breach=true link_retained=true");
            }).thenSucceed();
    }
}

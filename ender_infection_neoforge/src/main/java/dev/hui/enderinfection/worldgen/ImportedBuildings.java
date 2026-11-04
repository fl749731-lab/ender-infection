package dev.hui.enderinfection.worldgen;

import com.google.gson.*;
import dev.hui.enderinfection.EnderInfection;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;

/** Attributed upstream buildings. Manual placement is chunk-bounded and resumes after restart. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class ImportedBuildings {
    public record Spec(String key,String title,int width,int height,int length,int ground,boolean local){}
    private static final String DATA="ender_imported_build_jobs";
    private static Path localDir(){return FMLPaths.CONFIGDIR.get().resolve("ender_infection/imports");}
    private static Spec parse(JsonObject o,boolean local){
        String key=o.get("key").getAsString();if(!key.matches("[a-z0-9_]{1,64}"))throw new IllegalArgumentException("Invalid building key");
        var s=o.getAsJsonArray("size");int w=s.get(0).getAsInt(),h=s.get(1).getAsInt(),d=s.get(2).getAsInt();
        if(w<1||h<1||d<1||w>512||h>192||d>512||(long)w*h*d>16000000)throw new IllegalArgumentException("Building exceeds import limits");
        return new Spec(key,o.get("title").getAsString(),w,h,d,o.get("ground_offset").getAsInt(),local);
    }
    public static List<Spec> catalog(ServerLevel level){
        List<Spec> specs=new ArrayList<>();
        level.getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("ender_infection","imported_buildings.json")).ifPresent(resource->{
            try(var reader=resource.openAsReader()){for(var e:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("buildings"))specs.add(parse(e.getAsJsonObject(),false));}catch(IOException e){throw new UncheckedIOException(e);}
        });
        // This local copy is deliberately separate from the public distribution.
        Path meta=localDir().resolve("lua_lab.json");
        if(Files.isRegularFile(meta))try(var reader=Files.newBufferedReader(meta)){specs.add(parse(JsonParser.parseReader(reader).getAsJsonObject(),true));}catch(Exception e){System.err.println("Cannot read local Lua Lab manifest: "+e.getMessage());}
        return List.copyOf(specs);
    }
    public static StructureTemplate template(ServerLevel level,Spec spec){
        if(!spec.local())return level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("ender_infection","imported/"+spec.key())).orElseThrow(()->new IllegalArgumentException("Missing template "+spec.key()));
        Path p=localDir().resolve(spec.key()+".nbt");
        try(var in=Files.newInputStream(p)){return level.getStructureManager().readStructure(NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap()));}catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static Jobs jobs(ServerLevel level){return level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(Jobs::new, (tag, registries) -> Jobs.load(tag), null), DATA);}
    public static boolean queue(ServerLevel level,Spec spec,BlockPos origin,UUID player){
        if(origin.getY()<level.getMinBuildHeight()||origin.getY()+spec.height()>=level.getMaxBuildHeight())return false;
        var jobs=jobs(level);if(!jobs.pending.isEmpty())return false;
        var template=template(level,spec);var size=template.getSize();
        if(size.getX()!=spec.width()||size.getY()!=spec.height()||size.getZ()!=spec.length())throw new IllegalArgumentException("Building dimensions disagree with manifest");
        jobs.pending.add(new Job(spec,origin,player,0,template));jobs.setDirty();return true;
    }
    public static void step(ServerLevel level){
        var data=jobs(level);try{data.process(level);}catch(RuntimeException e){
            for(var job:data.pending){var player=level.getServer().getPlayerList().getPlayer(job.player);if(player!=null)player.sendSystemMessage(Component.literal("建筑放置已停止："+e.getMessage()));}
            System.err.println("Imported building placement stopped: "+e);data.cancel();
        }
    }
    private static void process(ServerLevel level,Jobs data){
        if(data.pending.isEmpty())return;var job=data.pending.get(0);
        if(job.template==null)job.template=template(level,job.spec);
        int minX=job.origin.getX()>>4,minZ=job.origin.getZ()>>4;
        int nx=((job.origin.getX()+job.spec.width()-1)>>4)-minX+1;
        int nz=((job.origin.getZ()+job.spec.length()-1)>>4)-minZ+1;
        int cx=minX+job.cursor%nx,cz=minZ+job.cursor/nx;
        // FULL generation happens only on the server thread. The light worker never reads roots.
        level.getChunk(cx,cz);
        var clip=new BoundingBox(cx*16,level.getMinBuildHeight(),cz*16,cx*16+15,level.getMaxBuildHeight()-1,cz*16+15);
        var settings=new StructurePlaceSettings().setIgnoreEntities(true).setLiquidSettings(net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING).setBoundingBox(clip);
        job.template.placeInWorld(level,job.origin,job.origin,settings,level.random,2);job.cursor++;data.setDirty();
        if(job.cursor>=nx*nz){
            data.pending.remove(0);var player=level.getServer().getPlayerList().getPlayer(job.player);
            if(player!=null)player.sendSystemMessage(Component.literal("建筑已完成："+job.spec.title()+"，起点 "+job.origin.toShortString()+"。原作者与许可见项目说明。"));
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level&&level.getGameTime()%2==0)step(level);}
    public static final class Job {
        final Spec spec;final BlockPos origin;final UUID player;int cursor;StructureTemplate template;
        Job(Spec spec,BlockPos origin,UUID player,int cursor,StructureTemplate template){this.spec=spec;this.origin=origin;this.player=player;this.cursor=cursor;this.template=template;}
    }
    public static final class Jobs extends SavedData {
        private final List<Job> pending=new ArrayList<>();
        public boolean active(){return !pending.isEmpty();}
        public void process(ServerLevel level){ImportedBuildings.process(level,this);}
        public String status(){return pending.isEmpty()?"没有正在放置的建筑":pending.get(0).spec.title()+"，已完成 "+pending.get(0).cursor+" 个区块";}
        public void cancel(){pending.clear();setDirty();}
        public static Jobs load(CompoundTag root){var data=new Jobs();for(var entry:root.getList("Jobs",10)){
            var t=(CompoundTag)entry;String key=t.getString("Key");if(!key.matches("[a-z0-9_]{1,64}"))continue;
            var s=new Spec(key,t.getString("Title"),t.getInt("Width"),t.getInt("Height"),t.getInt("Length"),t.getInt("Ground"),t.getBoolean("Local"));
            if(s.width()<1||s.length()<1||s.height()<1||s.width()>512||s.length()>512||s.height()>192)continue;
            data.pending.add(new Job(s,BlockPos.of(t.getLong("Origin")),t.getUUID("Player"),Math.max(0,t.getInt("Cursor")),null));
        }return data;}
        public CompoundTag save(CompoundTag root){return save(root,net.minecraft.core.RegistryAccess.EMPTY);}
        @Override public CompoundTag save(CompoundTag root, net.minecraft.core.HolderLookup.Provider registries){var list=new ListTag();for(var j:pending){var t=new CompoundTag();t.putString("Key",j.spec.key());t.putString("Title",j.spec.title());t.putInt("Width",j.spec.width());t.putInt("Height",j.spec.height());t.putInt("Length",j.spec.length());t.putInt("Ground",j.spec.ground());t.putBoolean("Local",j.spec.local());t.putLong("Origin",j.origin.asLong());t.putUUID("Player",j.player);t.putInt("Cursor",j.cursor);list.add(t);}root.put("Jobs",list);return root;}
    }
}

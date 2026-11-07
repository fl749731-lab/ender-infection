package dev.hui.enderinfection.test;

import com.google.gson.*;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import java.io.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

/** One giant arena, selected exclusively with -PcentralInstituteChecks.
 * The empty64 template supplies an anchor, not a boundary: no other test shares this run.
 */
@GameTestHolder("ender_central_institute_checks")
@PrefixGameTestTemplate(false)
public final class CentralInstituteGameTests {
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static <T extends Comparable<T>> String propertyValue(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property){return property.getName(state.getValue(property));}
    private static JsonObject manifest(GameTestHelper h){
        try(var reader=new InputStreamReader(h.getLevel().getServer().getResourceManager().getResource(id("ender_infection:central_institute.json")).orElseThrow().open(),java.nio.charset.StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static BlockPos pos(JsonElement value){
        if(value.isJsonArray()){var a=value.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
        var o=value.getAsJsonObject();return new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt());
    }
    private static void loadArena(GameTestHelper h,BlockPos base){
        // Explicit test-fixture loads are separate from the production no-force contract.
        int minX=(base.getX()>>4)-1,maxX=((base.getX()+119)>>4)+1,minZ=(base.getZ()>>4)-1,maxZ=((base.getZ()+103)>>4)+1;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)h.getLevel().setChunkForced(x,z,true);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)h.getLevel().getChunk(x,z);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x,z)!=null,"Explicitly retained arena chunk is not FULL: "+x+","+z);
        System.out.println("CENTRAL_INSTITUTE_ARENA_FULL chunks="+((maxX-minX+1)*(maxZ-minZ+1)));
    }
    private static void finish(GameTestHelper h,CentralInstitute.Jobs jobs){
        int guard=0;while(jobs.active()&&!jobs.halted()&&guard++<4096)jobs.process(h.getLevel());
        h.assertTrue(!jobs.active(),"Central institute did not finish: "+jobs.status());
    }
    @GameTest(template="empty64",templateNamespace="ender_central_institute_checks",timeoutTicks=2400,batch="central_institute_only")
    public static void giantInstitutePreservesJobsAndHasFortyEightUsableRooms(GameTestHelper h){
        var level=h.getLevel();var data=manifest(h);var jobs=CentralInstitute.jobs(level);jobs.cancel();
        h.assertTrue(data.getAsJsonArray("rooms").size()==48&&data.getAsJsonArray("tiles").size()==56,"Wrong central institute manifest");
        // An unloaded request must wait without silently causing terrain generation.
        var far=h.absolutePos(new BlockPos(4096,5,4096));
        h.assertTrue(!level.hasChunkAt(far),"Unloaded test location was already generated");
        int before=level.getChunkSource().getLoadedChunksCount();
        h.assertTrue(CentralInstitute.queue(level,far,Rotation.NONE,UUID.randomUUID()),"Unloaded queue rejected");
        jobs.process(level);
        h.assertTrue(!level.hasChunkAt(far)&&level.getChunkSource().getLoadedChunksCount()==before&&jobs.cursor()==0,"Queue forced an unloaded chunk or advanced its placement cursor");
        var unloadedSave=jobs.save(new CompoundTag(),level.registryAccess());var unloadedJob=unloadedSave.getCompound("Job");unloadedJob.putString("Phase","PLACE");unloadedJob.putInt("Checked",jobs.total());unloadedJob.putInt("Cursor",0);
        var unloadedPlacement=CentralInstitute.Jobs.load(unloadedSave);h.assertTrue(unloadedPlacement.active()&&unloadedPlacement.phase().equals("PLACE"),"Could not restore an unloaded placement checkpoint");unloadedPlacement.process(level);
        h.assertTrue(!level.hasChunkAt(far)&&level.getChunkSource().getLoadedChunksCount()==before&&unloadedPlacement.cursor()==0&&!unloadedPlacement.halted(),"PLACE checkpoint forced an unloaded chunk or advanced its cursor: "+unloadedPlacement.status());jobs.cancel();
        // Misalignment exercises world-chunk clipping independently of the 16-cell tile seams.
        // Keep the 55-high building entirely outside the framework's 64x24x64 barrier shell.
        var anchor=h.absolutePos(new BlockPos(143,5,143));
        var base=new BlockPos((anchor.getX()&~15)+15,anchor.getY(),(anchor.getZ()&~15)+15);loadArena(h,base);
        System.out.println("CENTRAL_INSTITUTE_NATIVE_ORIGIN "+base);
        // An existing player container must abort the preflight, preserving its inventory.
        var protectedPos=base.offset(2,2,2);level.setBlock(protectedPos,Blocks.BARREL.defaultBlockState(),3);
        var protectedBarrel=(BarrelBlockEntity)level.getBlockEntity(protectedPos);protectedBarrel.setItem(0,new ItemStack(Items.DIAMOND,3));
        h.assertTrue(CentralInstitute.queue(level,base,Rotation.NONE,UUID.randomUUID()),"Protected-area queue rejected before its audit");
        int guard=0;while(jobs.active()&&!jobs.halted()&&guard++<4096)jobs.process(level);
        h.assertTrue(jobs.halted()&&level.getBlockEntity(protectedPos)==protectedBarrel&&protectedBarrel.getItem(0).getCount()==3,"Building overwrote an existing container");
        h.assertTrue(level.getBlockState(base).isAir(),"Preflight modified the site before finding a protected container");jobs.cancel();level.removeBlock(protectedPos,false);
        var preservedTerrain=base.offset(2,2,2);level.setBlock(preservedTerrain,Blocks.STONE.defaultBlockState(),2);
        long placementStarted=System.nanoTime();h.assertTrue(CentralInstitute.queue(level,base,Rotation.NONE,UUID.randomUUID()),"Main queue failed");
        guard=0;while(jobs.active()&&!jobs.halted()&&jobs.cursor()<12&&guard++<4096)jobs.process(level);
        h.assertTrue(jobs.active()&&!jobs.halted()&&jobs.cursor()>=12,"Queue did not expose an in-progress placement cursor after "+guard+" steps: "+jobs.status());
        var saved=jobs.save(new CompoundTag(),level.registryAccess());int cursor=jobs.cursor();jobs.cancel();
        var restored=CentralInstitute.Jobs.load(saved);h.assertTrue(restored.cursor()==cursor&&restored.active(),"Save/load lost the central institute cursor");
        // A completed chunk must not be replayed after restoring the job.
        var completedFoundation=base.offset(8,0,8);level.setBlock(completedFoundation,Blocks.GOLD_BLOCK.defaultBlockState(),2);finish(h,restored);
        h.assertTrue(level.getBlockState(completedFoundation).is(Blocks.GOLD_BLOCK),"Resume replayed a completed chunk and destroyed a later edit");
        h.assertTrue(level.getBlockState(preservedTerrain).is(Blocks.STONE),"Outdoor underground terrain was excavated by a preserve cell");
        System.out.println("CENTRAL_INSTITUTE_PLACEMENT_PERFORMANCE elapsed_ms="+((System.nanoTime()-placementStarted)/1_000_000)+" furniture_containers="+data.getAsJsonArray("storage_containers").size());
        verifyFinalTiles(h,data,base,completedFoundation);verifyRoomsAndDoors(h,data,base);verifyLift(h,data,base);verifyStairLandings(h,data,base);
        // GameTestServer runs without normal tick pacing. Drain the actual
        // asynchronous lighting tasks before its accelerated timeout expires.
        // Keep every emission and exposed-crystal brightness assertion below.
        var lighting=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(int x=(base.getX()>>4)-1;x<=((base.getX()+119)>>4)+1;x++)for(int z=(base.getZ()>>4)-1;z<=((base.getZ()+103)>>4)+1;z++)
            lighting.add(level.getChunkSource().getLightEngine().waitForPendingTasks(x,z));
        var lightBarrier=java.util.concurrent.CompletableFuture.allOf(lighting.toArray(java.util.concurrent.CompletableFuture[]::new));
        h.runAtTickTime(100,()->h.succeedWhen(()->{
            level.getChunkSource().getLightEngine().tryScheduleUpdate();
            if(!lightBarrier.isDone()){
                // Yield the accelerated test loop to the lighting worker while
                // still letting normal server ticks apply forced-chunk tickets.
                try{Thread.sleep(2);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            }
            h.assertTrue(lightBarrier.isDone(),"Native lighting tasks are still pending");lightBarrier.join();
            int lights=0;for(var item:data.getAsJsonArray("light_sources")){var light=item.getAsJsonObject();var at=base.offset(pos(light));int emission=level.getBlockState(at).getLightEmission();h.assertTrue(emission==light.get("light_level").getAsInt(),"Registered lamp emission differs at "+at);int actual=level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,at);h.assertTrue(actual>=emission,"Block lighting still pending at "+at+": actual="+actual+", expected="+emission);lights++;}
            int exposed=0,enclosed=0;for(var value:data.getAsJsonArray("crystal_lighting")){var crystal=value.getAsJsonObject();var at=base.offset(pos(crystal));int faces=0,brightest=0;
                for(Direction face:Direction.values()){var neighbour=at.relative(face);var state=level.getBlockState(neighbour);if(state.isAir()||state.is(Blocks.LIGHT)){faces++;brightest=Math.max(brightest,level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,neighbour));}}
                if(faces==0){enclosed++;continue;}exposed++;h.assertTrue(brightest>=12,"Visible crystal surface remains dark at "+at+": exposed_faces="+faces+", brightest_air_face="+brightest);
            }
            h.assertTrue(exposed+enclosed==data.getAsJsonArray("crystal_lighting").size(),"Crystal exposure coverage omitted a voxel");
            System.out.println("CENTRAL_INSTITUTE_NATIVE_PASS rooms=48 tiles=56 cursor_resume="+cursor+" physical_lights="+lights+" exposed_crystals="+exposed+" enclosed_crystals="+enclosed);
        }));
    }
    private static void verifyFinalTiles(GameTestHelper h,JsonObject data,BlockPos base,BlockPos preserved){
        var level=h.getLevel();int checked=0,parts=0,boundary=0;
        for(var item:data.getAsJsonArray("tiles")){
            var tile=item.getAsJsonObject();String template=tile.get("template").getAsString();var key=id(template);String resource="structure/"+key.getPath()+".nbt";
            try(var in=level.getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(key.getNamespace(),resource)).orElseThrow().open()){
                var raw=NbtIo.readCompressed(in,NbtAccounter.unlimitedHeap());var palette=raw.getList("palette",10);
                for(var value:palette){var entry=(CompoundTag)value;var block=BuiltInRegistries.BLOCK.getOptional(id(entry.getString("Name"))).orElseThrow();
                    var props=entry.getCompound("Properties");for(String name:props.getAllKeys()){var property=block.getStateDefinition().getProperty(name);h.assertTrue(property!=null&&property.getValue(props.getString(name)).isPresent(),"Invalid native palette: "+entry);}}
                var tileBase=base.offset(pos(tile));
                for(var value:raw.getList("blocks",10)){
                    var b=(CompoundTag)value;var local=b.getList("pos",3);var at=tileBase.offset(local.getInt(0),local.getInt(1),local.getInt(2));if(at.equals(preserved))continue;
                    var expected=palette.getCompound(b.getInt("state"));var actual=level.getBlockState(at);String name=expected.getString("Name");if(name.equals("minecraft:structure_void"))continue;
                    h.assertTrue(BuiltInRegistries.BLOCK.getKey(actual.getBlock()).equals(id(name)),"Final tile mismatch at "+at+": "+actual+" expected "+expected);checked++;
                    for(String propertyName:expected.getCompound("Properties").getAllKeys()){
                        var property=actual.getBlock().getStateDefinition().getProperty(propertyName);
                        h.assertTrue(property!=null&&propertyValue(actual,property).equals(expected.getCompound("Properties").getString(propertyName)),"Final state mismatch at "+at+" / "+propertyName+" actual="+actual+" expected="+expected+" base="+base);
                    }
                    if(actual.getBlock() instanceof LabPartBlock){parts++;var root=LabPartBlock.anchor(at,actual);h.assertTrue(level.getBlockState(root).getBlock() instanceof LabFixtureBlock,"Orphan furniture part at "+at);
                        if((root.getX()>>4)!=(at.getX()>>4)||(root.getZ()>>4)!=(at.getZ()>>4))boundary++;}
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        h.assertTrue(checked>400000&&parts>100,"Incomplete final central institute: "+checked+" cells, "+parts+" parts");
        System.out.println("CENTRAL_INSTITUTE_TILES checked="+checked+" multipart="+parts+" world_chunk_boundary_parts="+boundary);
        var furnitureCategories=new HashMap<String,LabStorageInventory>();int furniture=0,staticContainers=0;
        for(var item:data.getAsJsonArray("storage_containers")){var container=item.getAsJsonObject();var at=base.offset(pos(container));var be=level.getBlockEntity(at);
            h.assertTrue(be instanceof LabStorageInventory&&((LabStorageInventory)be).isStorage()&&((LabStorageInventory)be).getContainerSize()==27,"Generated furniture has no 27-slot native inventory at "+at);
            var inventory=(LabStorageInventory)be;var tag=inventory.saveInventoryTag();String table=container.get("loot_table").getAsString();
            h.assertTrue(tag.getString("LootTable").equals(table)&&tag.getList("Items",10).isEmpty(),"Generated furniture loot was lost or eagerly unpacked at "+at);
            h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).equals(id(container.get("block_entity_id").getAsString())),"Wrong generated furniture BE type at "+at);
            if(level.getBlockState(at).getBlock() instanceof LabStorageBlock storage){staticContainers++;h.assertTrue(storage.getTicker(level,level.getBlockState(at),DesignContent.STORAGE_ENTITY.get())==null,"Single-block storage acquired a per-tick BE cost at "+at);}
            furnitureCategories.putIfAbsent(table,inventory);furniture++;
        }
        h.assertTrue(furniture>0&&furnitureCategories.size()==8,"Generated furniture loot/category coverage incomplete");
        for(var inventory:furnitureCategories.values()){inventory.unpackLootTable(null);h.assertTrue(!inventory.saveInventoryTag().getList("Items",10).isEmpty(),"Native low-value furniture loot produced no items at "+inventory.getBlockPos());}
        System.out.println("CENTRAL_INSTITUTE_STORAGE_LAYOUT_PASS deferred_containers="+furniture+" static_no_ticker="+staticContainers+" native_loot_categories="+furnitureCategories.size());
        int signs=0;for(var value:data.getAsJsonArray("room_signs")){var entry=value.getAsJsonObject();var at=base.offset(pos(entry));var be=level.getBlockEntity(at);
            h.assertTrue(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity,"Room label has no native sign BE at "+at);var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)be;
            for(int line=0;line<4;line++)h.assertTrue(sign.getFrontText().getMessage(line,false).getString().equals(entry.getAsJsonArray("text").get(line).getAsString()),"Native room sign text was lost/converted incorrectly at "+at+" line="+line);signs++;
        }h.assertTrue(signs==48,"Native room sign coverage incomplete");
        var categories=new HashSet<String>();var samples=new ArrayList<BarrelBlockEntity>();
        for(var item:data.getAsJsonArray("loot_containers")){var container=item.getAsJsonObject();var at=base.offset(pos(container));var be=level.getBlockEntity(at);String table=container.get("loot_table").getAsString();
            h.assertTrue(be instanceof BarrelBlockEntity&&be.saveWithFullMetadata(level.registryAccess()).getString("LootTable").equals(table),"Missing themed room supply at "+at);
            if(categories.add(table))samples.add((BarrelBlockEntity)be);
        }
        h.assertTrue(samples.size()==8,"Missing a themed supply category");
        for(var barrel:samples){barrel.unpackLootTable(null);boolean nonempty=false;for(int slot=0;slot<barrel.getContainerSize();slot++)nonempty|=!barrel.getItem(slot).isEmpty();h.assertTrue(nonempty,"Native loot parser generated no supplies at "+barrel.getBlockPos());}
        System.out.println("CENTRAL_INSTITUTE_NATIVE_LOOT_PASS categories="+samples.size());
    }
    private static double walk(Player player,Vec3 start,Vec3 direction){
        player.getAbilities().flying=false;player.setPos(start.x,start.y,start.z);player.setOnGround(true);
        for(int i=0;i<26;i++){player.move(MoverType.SELF,direction.scale(.1).add(0,-.08,0));player.setOnGround(true);}
        return player.position().subtract(start).dot(direction);
    }
    private static void verifyRoomsAndDoors(GameTestHelper h,JsonObject data,BlockPos base){
        var level=h.getLevel();var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var seen=new HashSet<String>();int doors=0;
        for(var item:data.getAsJsonArray("rooms")){var room=item.getAsJsonObject();seen.add(room.get("id").getAsString());
            for(var entry:room.getAsJsonArray("doors")){var door=entry.getAsJsonObject();var at=base.offset(pos(door));var state=level.getBlockState(at);
                h.assertTrue(state.getBlock() instanceof LabFixtureBlock&&((LabFixtureBlock)state.getBlock()).base().equals("lab_door"),"Missing room door "+room.get("id"));
                var face=state.getValue(LabFixtureBlock.FACING);var direction=new Vec3(face.getStepX(),0,face.getStepZ());var start=Vec3.atBottomCenterOf(at).subtract(direction.scale(1.2));
                h.assertTrue(walk(player,start,direction)<1,"Closed room door allowed passage: "+room.get("id"));
                level.setBlock(at,state.setValue(LabFixtureBlock.OPEN,true),3);
                h.assertTrue(walk(player,start,direction)>2.3,"Standing player cannot pass open room door: "+room.get("id"));level.setBlock(at,state,3);doors++;
            }
        }player.discard();h.assertTrue(seen.size()==48&&doors>=48,"Room access coverage incomplete");
    }
    private static void verifyLift(GameTestHelper h,JsonObject data,BlockPos base){
        var level=h.getLevel();var landings=new ArrayList<BlockPos>();for(var value:data.getAsJsonObject("lift").getAsJsonArray("landings"))landings.add(base.offset(pos(value)));landings.sort(Comparator.comparingInt(BlockPos::getY));
        h.assertTrue(landings.size()==6,"Cargo lift lost a floor");var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for(int i=0;i<landings.size()-1;i++){var lo=landings.get(i);var hi=landings.get(i+1);h.assertTrue(level.getBlockState(lo).getBlock() instanceof LabLiftBlock,"Missing cargo platform");var lift=(LabLiftBlock)level.getBlockState(lo).getBlock();
            player.setPos(lo.getX()+.5,lo.getY()+1,lo.getZ()+.5);player.setShiftKeyDown(false);var hit=new BlockHitResult(Vec3.atCenterOf(lo),Direction.UP,lo,false);lift.use(level.getBlockState(lo),level,lo,player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(Math.abs(player.getY()-hi.getY()-1)<.01,"Cargo lift failed to ascend to floor "+(i+1));player.setShiftKeyDown(true);((LabLiftBlock)level.getBlockState(hi).getBlock()).use(level.getBlockState(hi),level,hi,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(hi),Direction.UP,hi,false));
            h.assertTrue(Math.abs(player.getY()-lo.getY()-1)<.01,"Cargo lift failed to return");
        }player.discard();
    }
    private static void verifyStairLandings(GameTestHelper h,JsonObject data,BlockPos base){
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var level=h.getLevel();int checked=0;
        for(var value:data.getAsJsonArray("stairs")){var flight=value.getAsJsonObject();var lo=base.offset(pos(flight.get("lower_landing"))).above();var hi=base.offset(pos(flight.get("upper_landing"))).above();
            var path=stairPath(h,player,lo,hi);h.assertTrue(path.size()>flight.get("rise").getAsInt(),"Stair path skipped its return landing");
            player.setPos(lo.getX()+.5,lo.getY(),lo.getZ()+.5);player.getAbilities().flying=false;player.setOnGround(true);
            followStairs(h,player,path,flight.toString());Collections.reverse(path);followStairs(h,player,path,"descending "+flight);checked+=2;
        }
        player.discard();h.assertTrue(checked==20,"Missing stair joins");
    }
    private static boolean standing(GameTestHelper h,Player player,BlockPos feet){
        var level=h.getLevel();var box=player.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(player.position()));var support=feet.below();
        return level.noCollision(player,box)&&!level.getBlockState(support).getCollisionShape(level,support).isEmpty();
    }
    private static ArrayList<BlockPos> stairPath(GameTestHelper h,Player player,BlockPos lo,BlockPos hi){
        // Discover the route from native collision shapes; do not repeat the generator's U-turn algorithm.
        var previous=new HashMap<BlockPos,BlockPos>();var queue=new ArrayDeque<BlockPos>();previous.put(lo,lo);queue.add(lo);
        while(!queue.isEmpty()&&!previous.containsKey(hi)){var at=queue.remove();for(Direction face:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
            var next=at.relative(face).above(dy);
            if(next.getY()<lo.getY()||next.getY()>hi.getY()||next.getX()<Math.min(lo.getX(),hi.getX())-1||next.getX()>Math.max(lo.getX(),hi.getX())+1||next.getZ()<Math.min(lo.getZ(),hi.getZ())-1||next.getZ()>Math.max(lo.getZ(),hi.getZ())+8||previous.containsKey(next)||!standing(h,player,next))continue;
            if(dy>0&&!(h.getLevel().getBlockState(next.below()).getBlock() instanceof StairBlock))continue;
            if(dy<0&&!(h.getLevel().getBlockState(at.below()).getBlock() instanceof StairBlock))continue;
            previous.put(next,at);queue.add(next);
        }}
        h.assertTrue(previous.containsKey(hi),"No continuous collision-safe stair route: "+lo+" -> "+hi);var result=new ArrayList<BlockPos>();var at=hi;while(!at.equals(lo)){result.add(at);at=previous.get(at);}result.add(lo);Collections.reverse(result);return result;
    }
    private static void followStairs(GameTestHelper h,Player player,List<BlockPos> path,String label){
        for(int i=1;i<path.size();i++){var target=Vec3.atBottomCenterOf(path.get(i));int guard=0;
            while(Math.hypot(target.x-player.getX(),target.z-player.getZ())>.025&&guard++<80){
                double dx=target.x-player.getX(),dz=target.z-player.getZ(),distance=Math.hypot(dx,dz),step=Math.min(.08,distance);
                player.move(MoverType.SELF,new Vec3(dx/distance*step,-.08,dz/distance*step));player.setOnGround(true);
            }
            // Gravity also settles a descending half-step after its horizontal move.
            for(int settle=0;settle<8;settle++){player.move(MoverType.SELF,new Vec3(0,-.08,0));player.setOnGround(true);}
            h.assertTrue(Math.hypot(target.x-player.getX(),target.z-player.getZ())<.12&&Math.abs(player.getY()-target.y)<.2,"Native standing player blocked on stair "+label+" step="+i+" expected="+target+" actual="+player.position());
        }
    }
}

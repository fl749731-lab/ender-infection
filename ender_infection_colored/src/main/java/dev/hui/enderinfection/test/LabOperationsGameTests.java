package dev.hui.enderinfection.test;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.api.institute.*;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.institute.*;
import dev.hui.enderinfection.processing.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** Small real-control fixtures. The 56-tile building is exercised by its independent suite. */
@GameTestHolder("ender_operations_checks")
@PrefixGameTestTemplate(false)
public final class LabOperationsGameTests {
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static Item item(String key) { return BuiltInRegistries.ITEM.getOptional(id(key.contains(":")?key:"ender_infection:"+key)).orElseThrow(); }
    private static Block block(String key) { return BuiltInRegistries.BLOCK.getOptional(id(key.contains(":")?key:"ender_infection:"+key)).orElseThrow(); }
    private static BlockPos vector(JsonElement json) { var a=json.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()); }
    private static void loadFixture(GameTestHelper h, LabFixtureEntity entity, CompoundTag tag) { entity.load(tag); }
    private static void loadMachine(GameTestHelper h, LabProcessingEntity entity, CompoundTag tag) { entity.load(tag); }
    private static boolean sameStack(ItemStack a, ItemStack b) { return a.getCount()==b.getCount()&&ItemStack.isSameItemSameTags(a,b); }
    private static List<ItemStack> inventory(ServerPlayer player) { var result=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++)result.add(player.getInventory().getItem(i).copy());return result; }
    private static boolean unchanged(ServerPlayer player,List<ItemStack> before) { for(int i=0;i<before.size();i++)if(!sameStack(before.get(i),player.getInventory().getItem(i)))return false;return true; }
    private static int count(ServerPlayer player,Item target) { int result=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(target))result+=player.getInventory().getItem(i).getCount();return result; }

    static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerLevel level; final BlockPos origin; final JsonObject operations; final CompoundTag previousData; boolean closed;
        final List<io.netty.channel.embedded.EmbeddedChannel> transports=new ArrayList<>();final Set<ChunkPos> forced=new LinkedHashSet<>();final Set<BlockPos> placed=new LinkedHashSet<>();final List<ServerPlayer> players=new ArrayList<>();final Map<String,JsonObject> controls=new LinkedHashMap<>();
        Fixture(GameTestHelper h) {
            this.h=h;level=h.getLevel();previousData=OperationsData.get(level).save(new CompoundTag());origin=new BlockPos(900000+NEXT.getAndIncrement()*512,level.getMinBuildHeight()+8,900000);
            try(var input=level.getServer().getResourceManager().getResource(id("ender_infection:central_institute.json")).orElseThrow().open();var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) { operations=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("operations"); }
            catch(Exception failure) { throw new IllegalStateException("Missing operations manifest",failure); }
        }
        static Fixture create(GameTestHelper h) {
            var fixture=new Fixture(h);
            try { for(var value:fixture.operations.getAsJsonArray("controls")) {
                var row=value.getAsJsonObject();var offset=vector(row.get("pos"));var pos=fixture.origin.offset(offset);fixture.controls.put(row.get("role").getAsString(),row);
                fixture.put(pos,block(row.get("block").getAsString()).defaultBlockState());
                h.assertTrue(fixture.level.getBlockEntity(pos) instanceof OperationsControlEntity,"Missing controller BE registration");((OperationsControlEntity)fixture.level.getBlockEntity(pos)).bind(offset);
            } return fixture; } catch(RuntimeException|Error failure) { fixture.close();throw failure; }
        }
        void force(BlockPos pos) { var chunk=new ChunkPos(pos);if(!level.getForcedChunks().contains(chunk.toLong())) { level.setChunkForced(chunk.x,chunk.z,true);forced.add(chunk); }level.getChunk(chunk.x,chunk.z); }
        void put(BlockPos pos,BlockState state) { force(pos);placed.add(pos.immutable());if(level.getBlockEntity(pos) instanceof LabProcessingEntity machine)machine.clearContent();level.setBlock(pos,state,3); }
        ServerPlayer player() {
            var profile=new GameProfile(UUID.randomUUID(),"OperationsTest");var player=new ServerPlayer(level.getServer(),level,profile);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);transports.add(channel);
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),connection,player);player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);players.add(player);
            // Control transactions need the real ServerPlayer inventory, not a player
            // tracking ticket. Keeping this actor unattached makes no-force checks meaningful.
            return player;
        }
        BlockPos control(String role) { return origin.offset(vector(controls.get(role).get("pos"))); }
        InteractionResult use(String role,ServerPlayer player) { var row=controls.get(role);var pos=control(role);var approach=row.has("approach_pos")?origin.offset(vector(row.get("approach_pos"))):pos.north();player.setPos(approach.getX()+.5,approach.getY(),approach.getZ()+.5);var state=level.getBlockState(pos);return ((OperationsControlBlock)state.getBlock()).use(state,level,pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false)); }
        FacilityStatus state() { return InstituteApi.facility(level,control("generator")).orElseThrow(); }
        void repair(ServerPlayer player) { player.getInventory().add(new ItemStack(item("lab_fuse")));player.getInventory().add(new ItemStack(Items.COPPER_INGOT,4));player.getInventory().add(new ItemStack(Items.REDSTONE,4));h.assertTrue(use("generator",player).consumesAction()&&state().repaired(),"Actual generator repair failed"); }
        void circuit(ServerPlayer player,boolean containment,boolean enabled) { var state=state();if((containment?state.containmentPowered():state.servicePowered())!=enabled) { var held=player.getMainHandItem();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setShiftKeyDown(containment);try{h.assertTrue(use("distribution",player).consumesAction(),"Actual distribution interaction failed");}finally{player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,held);} } }
        JsonObject first(String collection,String zone) { for(var value:operations.getAsJsonArray(collection)) { var row=value.getAsJsonObject();if(zone==null||row.get("zone").getAsString().equals(zone))return row; }throw new AssertionError("Missing "+collection+" / "+zone); }
        LabProcessingEntity machine(String device) {
            JsonObject selected=null;for(var value:operations.getAsJsonArray("machines")) {var row=value.getAsJsonObject();if(row.get("device").getAsString().endsWith(device)&&row.get("zone").getAsString().equals("service")){selected=row;break;}}
            h.assertTrue(selected!=null,"Missing real service processor "+device);var offset=vector(selected.get("pos"));var pos=origin.offset(offset);put(pos,block(device).defaultBlockState());var machine=(LabProcessingEntity)level.getBlockEntity(pos);var tag=machine.saveInventoryTag();tag.putBoolean("InstituteBound",true);tag.putInt("InstituteOffsetX",offset.getX());tag.putInt("InstituteOffsetY",offset.getY());tag.putInt("InstituteOffsetZ",offset.getZ());loadMachine(h,machine,tag);InstituteOperations.registerBound(level,pos,offset);return machine;
        }
        @Override public void close() {
            if(closed)return;closed=true;
            try {
                for(var player:players) {if(player.isSleeping())player.stopSleepInBed(true,true);player.closeContainer();player.discard();}
                for(var pos:placed) if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null) { if(level.getBlockEntity(pos) instanceof LabProcessingEntity machine)machine.clearContent();if(level.getBlockEntity(pos) instanceof LabStorageInventory inventory)inventory.clearContent();level.removeBlock(pos,false);for(var drop:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)))drop.discard(); }
            } finally {
                try {for(var channel:transports)channel.finishAndReleaseAll();}
                finally {try {for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);}finally {level.getDataStorage().set(OperationsData.NAME,OperationsData.load(previousData));}}
            }
        }
    }

    @GameTest(template="empty64",templateNamespace="ender_operations_checks",timeoutTicks=100,batch="operations_repair")
    public static void repairIsAtomicRejectsMissingMaterialsAndDispatchesOneDurableEvent(GameTestHelper h) throws Exception {
        try(var f=Fixture.create(h)) {
            var player=f.player();player.getInventory().add(new ItemStack(item("lab_fuse")));player.getInventory().add(new ItemStack(Items.COPPER_INGOT,4));player.getInventory().add(new ItemStack(Items.REDSTONE,3));var before=inventory(player);var events=new AtomicInteger();
            try(var listener=InstituteApi.onFacilityChange(event->{if(event.current().origin().equals(f.origin)&&event.reason().equals("generator_repaired"))events.incrementAndGet();})) {
                h.assertTrue(f.use("generator",player)==InteractionResult.FAIL&&!f.state().repaired()&&unchanged(player,before),"Incomplete repair partially consumed materials or repaired the generator");
                player.getInventory().add(new ItemStack(Items.REDSTONE));h.assertTrue(f.use("generator",player).consumesAction()&&f.state().repaired(),"Complete material transaction did not repair");
                h.assertTrue(count(player,item("lab_fuse"))==0&&count(player,Items.COPPER_INGOT)==0&&count(player,Items.REDSTONE)==0&&events.get()==1,"Repair did not consume exactly one fuse/four copper/four redstone or emit exactly one event");
                before=inventory(player);f.use("generator",player);h.assertTrue(events.get()==1&&unchanged(player,before)&&!f.state().servicePowered()&&!f.state().containmentPowered(),"Repeated repair charged again, duplicated event or enabled circuits implicitly");
                var beforeTerminal=f.state();before=inventory(player);h.assertTrue(f.use("terminal",player)==InteractionResult.FAIL&&count(player,item("research_keycard"))==0&&unchanged(player,before)&&f.state().equals(beforeTerminal),"A repaired generator issued a keycard while the service circuit was still off");
            }
        }h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_operations_checks",timeoutTicks=240,batch="operations_lights")
    public static void twoCircuitsUpdateLoadedLampsAndNeverLoadTheUntouchedCorner(GameTestHelper h) {
        var f=Fixture.create(h);
        try {
            var player=f.player();f.repair(player);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COBBLESTONE));var blockedCircuit=f.state();var blockedInventory=inventory(player);player.setShiftKeyDown(true);
            try{h.assertTrue(f.use("distribution",player)==InteractionResult.FAIL&&f.state().equals(blockedCircuit)&&unchanged(player,blockedInventory),"Sneaking with a held item changed containment power or consumed the item");}finally{player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
            var service=f.first("managed_lights","service");var containment=f.first("managed_lights","containment");var a=f.origin.offset(vector(service.get("pos")));var b=f.origin.offset(vector(containment.get("pos")));f.put(a,block(service.get("block").getAsString()).defaultBlockState());f.put(b,block(containment.get("block").getAsString()).defaultBlockState());
            BlockPos unseen=null;for(int x=0;x<120&&unseen==null;x+=16)for(int z=0;z<104;z+=16){var point=f.origin.offset(x,2,z);var candidate=new ChunkPos(point);boolean outsideTicketHalo=f.forced.stream().allMatch(chunk->Math.max(Math.abs(chunk.x-candidate.x),Math.abs(chunk.z-candidate.z))>=3);if(outsideTicketHalo&&f.level.getChunkSource().getChunkNow(candidate.x,candidate.z)==null&&!f.level.getForcedChunks().contains(candidate.toLong())){unseen=point;break;}}
            h.assertTrue(unseen!=null,"Fixture accidentally loaded the entire institute footprint");var untouched=unseen;int forced=f.level.getForcedChunks().size();f.circuit(player,false,true);
            h.runAfterDelay(70,()->{
                try {
                    h.assertTrue(f.level.getBlockState(a).getValue(LabDetailBlock.POWERED)&&!f.level.getBlockState(b).getValue(LabDetailBlock.POWERED)&&f.state().servicePowered()&&!f.state().containmentPowered(),"Service switch also powered containment or failed to update the real lamp");
                    h.assertTrue(f.level.getBlockState(a).getLightEmission()==14&&f.level.getBrightness(LightLayer.BLOCK,a)>=12&&f.level.getBlockState(b).getLightEmission()==0,"Service lamp changed state without actual block-light emission");
                    h.assertTrue(f.level.getChunkSource().getChunkNow(untouched.getX()>>4,untouched.getZ()>>4)==null&&f.level.getForcedChunks().size()==forced,"Power refresh force-loaded an untouched target");
                    f.circuit(player,false,false);f.circuit(player,true,true);
                    h.runAfterDelay(70,()->{try {h.assertTrue(!f.level.getBlockState(a).getValue(LabDetailBlock.POWERED)&&f.level.getBlockState(b).getValue(LabDetailBlock.POWERED)&&!f.state().servicePowered()&&f.state().containmentPowered(),"Containment and service failed independent on/off transitions");h.assertTrue(f.level.getBlockState(b).getLightEmission()==14&&f.level.getBrightness(LightLayer.BLOCK,b)>=12&&f.level.getBlockState(a).getLightEmission()==0,"Containment lamp did not physically light or service lamp failed to turn off");h.assertTrue(f.level.getChunkSource().getChunkNow(untouched.getX()>>4,untouched.getZ()>>4)==null&&f.level.getForcedChunks().size()==forced,"Second circuit update force-loaded a target");}finally{f.close();}h.succeed();});
                } catch(RuntimeException|Error failure) {f.close();throw failure;}
            });
        } catch(RuntimeException|Error failure) {f.close();throw failure;}
    }

    @GameTest(template="empty64",templateNamespace="ender_operations_checks",timeoutTicks=100,batch="operations_doors")
    public static void securedRootAndUpperPartDenyOutsideButAlwaysPermitInsideEmergencyExit(GameTestHelper h) {
        try(var f=Fixture.create(h)) {
            var row=f.first("secured_doors",null);var offset=vector(row.get("pos"));var pos=f.origin.offset(offset);var door=(LabFixtureBlock)block(row.get("block").getAsString());f.put(pos,door.defaultBlockState().setValue(LabFixtureBlock.FACING,Direction.NORTH));door.fillParts(f.level,pos,f.level.getBlockState(pos));f.placed.add(pos.above());var entity=(LabFixtureEntity)f.level.getBlockEntity(pos);var tag=entity.saveInventoryTag();tag.putBoolean("InstituteBound",true);tag.putInt("InstituteOffsetX",offset.getX());tag.putInt("InstituteOffsetY",offset.getY());tag.putInt("InstituteOffsetZ",offset.getZ());loadFixture(h,entity,tag);InstituteOperations.registerBound(f.level,pos,offset);var player=f.player();
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);h.assertTrue(doorUse(f,player,pos)==InteractionResult.FAIL&&doorUse(f,player,pos.above())==InteractionResult.FAIL&&!f.level.getBlockState(pos).getValue(LabFixtureBlock.OPEN),"Unpowered outside interaction bypassed root/upper-part door security");
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);doorUse(f,player,pos.above());h.assertTrue(f.level.getBlockState(pos).getValue(LabFixtureBlock.OPEN),"Inside emergency exit requires a card or power");doorUse(f,player,pos);
            f.repair(player);f.circuit(player,true,true);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);h.assertTrue(doorUse(f,player,pos.above())==InteractionResult.FAIL,"Powered outside door accepted a player without a keycard");player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("research_keycard")));doorUse(f,player,pos.above());h.assertTrue(f.level.getBlockState(pos).getValue(LabFixtureBlock.OPEN),"Held keycard did not unlock the actual upper-part interaction");doorUse(f,player,pos);
            f.circuit(player,true,false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);doorUse(f,player,pos);h.assertTrue(f.level.getBlockState(pos).getValue(LabFixtureBlock.OPEN),"Switching power off trapped an inside player");
            // Clicking an unmanaged neighbouring leaf must still check the secured facility leaf.
            doorUse(f,player,pos);var mate=pos.east();f.put(mate,door.defaultBlockState().setValue(LabFixtureBlock.FACING,Direction.NORTH));door.fillParts(f.level,mate,f.level.getBlockState(mate));f.placed.add(mate.above());
            player.setPos(mate.getX()+.5,mate.getY(),mate.getZ()+2.5);
            h.assertTrue(doorUse(f,player,mate.above())==InteractionResult.FAIL&&!f.level.getBlockState(pos).getValue(LabFixtureBlock.OPEN)&&!f.level.getBlockState(mate).getValue(LabFixtureBlock.OPEN),"Unmanaged leaf bypassed the adjacent facility door's power/card guard");
            player.setPos(mate.getX()+.5,mate.getY(),mate.getZ()-1.5);doorUse(f,player,mate.above());
            h.assertTrue(f.level.getBlockState(pos).getValue(LabFixtureBlock.OPEN)&&f.level.getBlockState(mate).getValue(LabFixtureBlock.OPEN),"Emergency exit did not open both leaves");

        }h.succeed();
    }
    private static InteractionResult doorUse(Fixture f,ServerPlayer player,BlockPos at) {var state=f.level.getBlockState(at);var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false);return state.getBlock() instanceof LabPartBlock part?part.use(state,f.level,at,player,InteractionHand.MAIN_HAND,hit):((LabFixtureBlock)state.getBlock()).use(state,f.level,at,player,InteractionHand.MAIN_HAND,hit);}

    @GameTest(template="empty64",templateNamespace="ender_operations_checks",timeoutTicks=140,batch="operations_archive")
    public static void realProcessesShareProgressAndSavedDataKeepsCardsAndOneAtomicArchiveReward(GameTestHelper h) {
        try(var f=Fixture.create(h)) {
            var first=f.player();var second=f.player();f.repair(first);f.circuit(first,false,true);f.circuit(first,true,true);
            for(String device:List.of("lab_microscope","lab_centrifuge")) {var machine=f.machine(device);var recipe=InstituteApi.processingRecipes().values().stream().filter(r->r.devices().contains("ender_infection:"+device)).findFirst().orElseThrow();machine.setItem(0,new ItemStack(item(recipe.inputId()),recipe.inputCount()));if(recipe.reagentCount()>0)machine.setItem(1,new ItemStack(item(recipe.reagentId()),recipe.reagentCount()));for(int i=0;i<recipe.ticks();i++)LabProcessingEntity.serverTick(f.level,machine.getBlockPos(),machine.getBlockState(),machine);h.assertTrue(machine.getItem(2).is(item(recipe.outputId())),"Real facility process did not produce its required document/reagent");second.getInventory().add(machine.removeItem(2,64));}
            h.assertTrue(f.state().analyzed()&&f.state().separated(),"Two machines did not share completed facility milestones");
            f.use("terminal",first);f.use("terminal",first);f.use("terminal",second);h.assertTrue(count(first,item("research_keycard"))==1&&count(second,item("research_keycard"))==1,"Keycard issuance was global, missing or repeatable per player");
            var restored=OperationsData.load(OperationsData.get(f.level).save(new CompoundTag()));f.level.getDataStorage().set(OperationsData.NAME,restored);var saved=restored.find(f.origin);h.assertTrue(saved.analyzed()&&saved.separated()&&saved.receivedKey(first.getUUID())&&saved.receivedKey(second.getUUID()),"SavedData lost shared task progress or player card receipts");f.use("terminal",first);h.assertTrue(count(first,item("research_keycard"))==1,"Reload reset a player's card receipt");
            f.circuit(first,true,false);var noPowerSnapshot=f.state();var noPowerData=OperationsData.get(f.level).save(new CompoundTag());var noPowerInventory=inventory(second);
            h.assertTrue(count(second,item("research_record"))==1&&count(second,item("separated_reagent"))==1,"Archive power guard fixture lacks the real machine products");
            h.assertTrue(f.use("archive",second)==InteractionResult.FAIL&&unchanged(second,noPowerInventory)&&f.state().equals(noPowerSnapshot)&&OperationsData.get(f.level).save(new CompoundTag()).equals(noPowerData),"An unpowered containment archive consumed documents, changed saved progress or awarded loot");f.circuit(first,true,true);
            second.getInventory().clearContent();for(int i=0;i<36;i++)second.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));second.getInventory().setItem(0,new ItemStack(item("research_record"),64));second.getInventory().setItem(1,new ItemStack(item("separated_reagent"),64));var before=inventory(second);
            h.assertTrue(f.use("archive",second)==InteractionResult.FAIL&&unchanged(second,before)&&!f.state().archiveClaimed(),"Full inventory partially paid costs, dropped rewards or consumed the facility claim");
            h.assertTrue(f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.control("archive")).inflate(3)).isEmpty(),"Failed archive transaction spilled a reward");
            second.getInventory().clearContent();second.getInventory().add(new ItemStack(item("research_record")));second.getInventory().add(new ItemStack(item("separated_reagent")));h.assertTrue(f.use("archive",second).consumesAction()&&f.state().archiveClaimed()&&count(second,item("research_record"))==0&&count(second,item("separated_reagent"))==0&&count(second,item("core_archive"))==1&&count(second,item("void_crystal"))==2,"Successful archive exchange did not pay/award exactly once");
            f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(OperationsData.get(f.level).save(new CompoundTag())));first.getInventory().add(new ItemStack(item("research_record")));first.getInventory().add(new ItemStack(item("separated_reagent")));before=inventory(first);h.assertTrue(f.use("archive",first)==InteractionResult.FAIL&&unchanged(first,before)&&f.state().archiveClaimed(),"Another player or reload duplicated the shared archive reward");
        }h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_operations_checks",timeoutTicks=240,batch="operations_medical_bed")
    public static void medicalBedUsesNativePlacementSleepOccupancyAndSingleDropFromEitherHalf(GameTestHelper h) {
        long daytime=h.getLevel().getDayTime();var f=Fixture.create(h);var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable close=()->{if(closed.compareAndSet(false,true))try{f.close();}finally{h.getLevel().setDayTime(daytime);h.getLevel().updateSkyBrightness();}};
        try {
            var player=f.player();var root=f.origin.offset(104,2,90);for(var pos:BlockPos.betweenClosed(root.offset(-2,-1,-2),root.offset(2,-1,3)))f.put(pos,Blocks.STONE.defaultBlockState());
            class BedCheck implements Runnable {
                int cycle,phase,stableTicks,headLoot;BlockPos head=root.south();
                int drops() {return f.level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4)).stream().filter(drop->drop.getItem().is(item("lab_medical_bed"))).mapToInt(drop->drop.getItem().getCount()).sum();}
                String diagnostic() {var chunk=f.level.getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);return "half="+(cycle==0?"foot":"head")+" phase="+phase+" drops="+drops()+" head_loot="+headLoot+" entities_loaded="+f.level.areEntitiesLoaded(new ChunkPos(root).toLong())+" full_status="+(chunk==null?"UNLOADED":chunk.getFullStatus())+" root="+f.level.getBlockState(root)+" head="+f.level.getBlockState(head);}
                void placeSleepAndBreak() {
                    f.level.setDayTime(13000);f.level.updateSkyBrightness();player.setPos(root.getX()+.5,root.getY(),root.getZ()-1.5);player.setYRot(0);var stack=new ItemStack(item("lab_medical_bed"));player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var hit=new BlockHitResult(Vec3.atCenterOf(root.below()).add(0,.5,0),Direction.UP,root.below(),false);var result=((BlockItem)stack.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit));h.assertTrue(result.consumesAction(),"Native BlockItem could not place the medical bed");
                    var state=f.level.getBlockState(root);h.assertTrue(state.getBlock() instanceof LabMedicalBedBlock&&state.getValue(BedBlock.PART)==BedPart.FOOT,"Medical bed is not a native foot half");head=root.relative(state.getValue(BedBlock.FACING));f.placed.add(root);f.placed.add(head);
                    h.assertTrue(f.level.getBlockState(head).is(state.getBlock())&&f.level.getBlockState(head).getValue(BedBlock.PART)==BedPart.HEAD&&f.level.getBlockEntity(root)==null&&f.level.getBlockEntity(head)==null,"Native placement failed to reserve exactly two baked bed halves");
                    player.setPos(head.getX()+.5,head.getY()+.5,head.getZ()+.5);var sleep=player.startSleepInBed(head);h.assertTrue(sleep.left().isEmpty()&&player.isSleeping()&&f.level.getBlockState(head).getValue(BedBlock.OCCUPIED)&&head.equals(player.getRespawnPosition()),"Native sleeping/occupancy/respawn failed: "+sleep);player.stopSleepInBed(true,true);
                    h.assertTrue(!f.level.getBlockState(head).getValue(BedBlock.OCCUPIED),"Waking left the medical bed occupied");
                    headLoot=Block.getDrops(f.level.getBlockState(head),f.level,head,null).stream().filter(stack0->stack0.is(item("lab_medical_bed"))).mapToInt(ItemStack::getCount).sum();
                    player.setPos(root.getX()+.5,root.getY(),root.getZ()-1.5);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.assertTrue(player.gameMode.destroyBlock(cycle==0?root:head),"Native survival destruction refused the medical bed");
                    h.assertTrue(f.level.getBlockState(root).isAir()&&f.level.getBlockState(head).isAir(),"Breaking a bed half left an orphan: "+diagnostic());phase=1;stableTicks=0;
                }
                @Override public void run() {
                    if(closed.get())return;
                    try {
                        if(h.getTick()%20==0)System.out.println("OPERATIONS_BED_WAIT "+diagnostic());
                        if(phase==0) {if(!f.level.areEntitiesLoaded(new ChunkPos(root).toLong())||!f.level.areEntitiesLoaded(new ChunkPos(root.south()).toLong()))return;placeSleepAndBreak();return;}
                        h.assertTrue(f.level.getBlockState(root).isAir()&&f.level.getBlockState(head).isAir(),"A destroyed medical bed restored an orphan: "+diagnostic());int count=drops();
                        if(count==0){stableTicks=0;return;}h.assertTrue(count==1,"Bed destruction duplicated items: "+diagnostic());if(++stableTicks<3)return;
                        System.out.println("OPERATIONS_BED_HALF_PASS "+diagnostic());for(var drop:f.level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(4)))if(drop.getItem().is(item("lab_medical_bed")))drop.discard();
                        if(cycle++==0){phase=0;stableTicks=0;}else{close.run();h.succeed();}
                    } catch(RuntimeException|Error failure) {close.run();throw failure;}
                }
            }
            var check=new BedCheck();h.runAfterDelay(200,()->{try{h.fail("Native bed placement/drop did not settle within 200 ticks: "+check.diagnostic());}finally{close.run();}});h.onEachTick(check);
        } catch(RuntimeException|Error failure) {close.run();throw failure;}
    }

    @GameTest(template="empty64",templateNamespace="ender_operations_checks",timeoutTicks=80,batch="operations_api")
    public static void processingRegistrationRollsBackDuplicatesFreezesAndListenerCanUnsubscribe(GameTestHelper h) throws Exception {
        var registry=new InstituteRegistry();var a=new ProcessingRecipe("ops_probe:a",Set.of("ender_infection:lab_microscope"),"minecraft:paper",1,"",0,"minecraft:book",1,40);var b=new ProcessingRecipe("ops_probe:b",Set.of("ender_infection:lab_centrifuge"),"minecraft:paper",1,"minecraft:glass_bottle",1,"minecraft:book",1,60);boolean rejected=false;
        try {registry.registerAddon("ops_probe",r->{r.processing(a);throw new IllegalArgumentException("deliberate registration failure");});}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&registry.processingRecipes().isEmpty(),"Failed registration leaked its processing recipe");registry.registerAddon("ops_probe",r->r.processing(a));var snapshot=registry.processingRecipes();rejected=false;
        try {registry.registerAddon("ops_probe",r->{r.processing(b);r.processing(a);});}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&registry.processingRecipes().size()==1&&!registry.processingRecipes().containsKey(b.id()),"Duplicate registration partially committed the preceding recipe");rejected=false;try{registry.registerAddon("foreign",r->r.processing(b));}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Recipe registration ignored namespace ownership");registry.freeze();rejected=false;try{registry.registerAddon("ops_probe",r->r.processing(b));}catch(IllegalStateException expected){rejected=true;}h.assertTrue(rejected&&snapshot.equals(registry.processingRecipes()),"Frozen registry changed");rejected=false;try{snapshot.clear();}catch(UnsupportedOperationException expected){rejected=true;}h.assertTrue(rejected,"Discovered recipes are mutable");
        var origin=h.absolutePos(BlockPos.ZERO);var state=new FacilityStatus(origin,false,false,false,false,false,false);var received=new AtomicInteger();try(var subscription=InstituteApi.onFacilityChange(event->{if(event.reason().equals("ops_probe"))received.incrementAndGet();})){InstituteApi.fireFacilityEvent(new FacilityEvent(h.getLevel(),state,state,"ops_probe"));h.assertTrue(received.get()==1,"Facility listener missed server dispatch");}InstituteApi.fireFacilityEvent(new FacilityEvent(h.getLevel(),state,state,"ops_probe"));h.assertTrue(received.get()==1,"Closed subscription still received facility events");h.succeed();
    }
}

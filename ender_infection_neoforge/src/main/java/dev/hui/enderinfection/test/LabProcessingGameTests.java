package dev.hui.enderinfection.test;

import com.mojang.authlib.GameProfile;
import com.google.gson.*;
import dev.hui.enderinfection.api.institute.*;
import dev.hui.enderinfection.institute.InstituteOperations;
import dev.hui.enderinfection.processing.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

/** Tests actual devices, native menus and the installed facility service; no fake power provider. */
@GameTestHolder("ender_processing_checks")
@PrefixGameTestTemplate(false)
public final class LabProcessingGameTests {
    private static final Map<UUID,io.netty.channel.embedded.EmbeddedChannel> TRANSPORTS = new HashMap<>();
    private static final String[] DEVICES = {"lab_microscope", "lab_centrifuge"};
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static Item item(String value) { return BuiltInRegistries.ITEM.getOptional(id(value)).orElseThrow(); }
    private static Block block(String key) { return BuiltInRegistries.BLOCK.getOptional(id("ender_infection:"+key)).orElseThrow(); }
    private static ProcessingRecipe recipe(String device) { return InstituteApi.processingRecipes().values().stream().filter(r -> r.devices().contains("ender_infection:"+device)).findFirst().orElseThrow(); }
    private static ServerPlayer player(GameTestHelper h, BlockPos pos) {
        var profile = new GameProfile(UUID.randomUUID(),"ProcessTest");
        var player = new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);TRANSPORTS.put(profile.getId(),channel);
        net.neoforged.neoforge.network.registration.ChannelAttributes.setConnectionType(connection,net.neoforged.neoforge.network.connection.ConnectionType.OTHER);
        net.neoforged.neoforge.network.registration.ChannelAttributes.setPayloadSetup(connection,net.neoforged.neoforge.network.registration.NetworkPayloadSetup.empty());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,player,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false));
        player.setGameMode(GameType.SURVIVAL); player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5); h.getLevel().addNewPlayer(player); return player;
    }
    private static void removePlayer(GameTestHelper h, ServerPlayer player) {
        try {player.closeContainer();h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        finally {var channel=TRANSPORTS.remove(player.getUUID());if(channel!=null)channel.finishAndReleaseAll();}
    }
    private static LabProcessingEntity place(GameTestHelper h, BlockPos pos, String device) {
        var level = h.getLevel(); level.getChunk(pos); level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
        if (level.getBlockEntity(pos) instanceof LabProcessingEntity old) old.clearContent(); level.removeBlock(pos,false);
        level.setBlock(pos,block(device).defaultBlockState(),3);
        h.assertTrue(level.getBlockEntity(pos) instanceof LabProcessingEntity,"Missing processor BE/ticker registration: "+device);
        return (LabProcessingEntity)level.getBlockEntity(pos);
    }
    private static void tick(GameTestHelper h, LabProcessingEntity machine, int count) { for(int i=0;i<count;i++) LabProcessingEntity.serverTick(h.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine); }
    private static void feed(LabProcessingEntity machine, ProcessingRecipe recipe, int batches) {
        machine.setItem(0,new ItemStack(item(recipe.inputId()),recipe.inputCount()*batches));
        machine.setItem(1,recipe.reagentCount()==0?ItemStack.EMPTY:new ItemStack(item(recipe.reagentId()),recipe.reagentCount()*batches));
    }
    private static void load(GameTestHelper h, LabProcessingEntity machine, CompoundTag tag) { machine.loadWithComponents(tag,h.getLevel().registryAccess()); }
    private static LabProcessingEntity reload(GameTestHelper h, LabProcessingEntity old, CompoundTag tag) {
        var next = new LabProcessingEntity(old.getBlockPos(),old.getBlockState()); h.getLevel().setBlockEntity(next); load(h,next,tag); return next;
    }
    private static LabProcessingMenu open(GameTestHelper h, ServerPlayer player, LabProcessingEntity machine) {
        player.setPos(machine.getBlockPos().getX()+.5,machine.getBlockPos().getY(),machine.getBlockPos().getZ()-1.5);
        var state = machine.getBlockState(); ((LabProcessingBlock)state.getBlock()).use(state,h.getLevel(),machine.getBlockPos(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(machine.getBlockPos()),Direction.NORTH,machine.getBlockPos(),false));
        h.assertTrue(player.containerMenu instanceof LabProcessingMenu,"Device failed to open the native processor menu");
        var menu = (LabProcessingMenu)player.containerMenu; h.assertTrue(menu.slots.size()==39&&menu.machine()==machine,"Menu does not expose 3+36 synchronized slots"); return menu;
    }
    private record Fixture(BlockPos origin, BlockPos generator, BlockPos distribution, LabProcessingEntity machine, ServerPlayer player, Set<ChunkPos> forced) {}
    private static BlockPos vector(JsonElement json) { var a=json.getAsJsonArray(); return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()); }
    private static JsonObject operations(GameTestHelper h) {
        try(var input=h.getLevel().getServer().getResourceManager().getResource(id("ender_infection:central_institute.json")).orElseThrow().open(); var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("operations");
        } catch(Exception failure) { throw new IllegalStateException("Missing actual institute operations manifest",failure); }
    }
    private static Fixture poweredFacility(GameTestHelper h, String device) {
        var operations=operations(h); JsonObject machineRow=null,generatorRow=null,distributionRow=null;
        for(var entry:operations.getAsJsonArray("machines")) { var row=entry.getAsJsonObject(); if(row.get("device").getAsString().endsWith(device)&&row.get("zone").getAsString().equals("service")) { machineRow=row; break; } }
        for(var entry:operations.getAsJsonArray("controls")) { var row=entry.getAsJsonObject(); var role=row.get("role").getAsString(); if(role.equals("generator"))generatorRow=row; if(role.equals("distribution"))distributionRow=row; }
        h.assertTrue(machineRow!=null&&generatorRow!=null&&distributionRow!=null,"Real operations manifest lacks service machine or controls");
        var machineOffset=vector(machineRow.get("pos")); var origin=h.absolutePos(new BlockPos(320,4,320));
        var generatorOffset=vector(generatorRow.get("pos")); var distributionOffset=vector(distributionRow.get("pos"));
        var generator=origin.offset(generatorOffset);var distribution=origin.offset(distributionOffset);var pos=origin.offset(machineOffset); var forced=new HashSet<ChunkPos>();
        for(var point:List.of(pos,generator,distribution)) { var chunk=new ChunkPos(point); forced.add(chunk); h.getLevel().setChunkForced(chunk.x,chunk.z,true); h.getLevel().getChunk(chunk.x,chunk.z); }
        for(var pair:List.of(Map.entry(generator,"lab_power_generator"),Map.entry(distribution,"lab_distribution_panel"))) {
            h.getLevel().setBlock(pair.getKey().below(),Blocks.STONE.defaultBlockState(),2); h.getLevel().setBlock(pair.getKey(),block(pair.getValue()).defaultBlockState(),3);
        }
        var machine=place(h,pos,device); var tag=machine.saveInventoryTag(); tag.putBoolean("InstituteBound",true); tag.putInt("InstituteOffsetX",machineOffset.getX());tag.putInt("InstituteOffsetY",machineOffset.getY());tag.putInt("InstituteOffsetZ",machineOffset.getZ());load(h,machine,tag);
        ((dev.hui.enderinfection.institute.OperationsControlEntity)h.getLevel().getBlockEntity(generator)).bind(generatorOffset);
        ((dev.hui.enderinfection.institute.OperationsControlEntity)h.getLevel().getBlockEntity(distribution)).bind(distributionOffset);
        InstituteOperations.registerBound(h.getLevel(),generator,generatorOffset); InstituteOperations.registerBound(h.getLevel(),distribution,distributionOffset); InstituteOperations.registerBound(h.getLevel(),pos,machineOffset);
        var player=player(h,generator); player.getInventory().add(new ItemStack(item("ender_infection:lab_fuse"))); player.getInventory().add(new ItemStack(Items.COPPER_INGOT,4));player.getInventory().add(new ItemStack(Items.REDSTONE,4));
        InstituteOperations.useControl(h.getLevel(),generator,player,"generator");
        h.assertTrue(InstituteApi.facility(h.getLevel(),pos).orElseThrow().repaired(),"Actual generator repair failed");
        if(!InstituteApi.facility(h.getLevel(),pos).orElseThrow().servicePowered()) { player.setPos(distribution.getX()+.5,distribution.getY(),distribution.getZ()-1.5); InstituteOperations.useControl(h.getLevel(),distribution,player,"distribution"); }
        h.assertTrue(InstituteApi.isPowered(h.getLevel(),pos),"Actual facility service zone did not power the processor");
        return new Fixture(origin,generator,distribution,machine,player,forced);
    }
    private static void cleanup(GameTestHelper h, Fixture fixture) {
        removePlayer(h,fixture.player); fixture.machine.clearContent(); h.getLevel().removeBlock(fixture.machine.getBlockPos(),false); h.getLevel().removeBlock(fixture.generator,false);h.getLevel().removeBlock(fixture.distribution,false);
        for(var chunk:fixture.forced) h.getLevel().setChunkForced(chunk.x,chunk.z,false);
    }

    @GameTest(template="empty64",templateNamespace="ender_processing_checks",timeoutTicks=80,batch="processing_unpowered")
    public static void independentlyPlacedDevicesNeverInventPowerOrConsumeInputs(GameTestHelper h) {
        for(int i=0;i<DEVICES.length;i++) {
            var pos=h.absolutePos(new BlockPos(12+i*4,2,12));h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);var owner=player(h,pos);
            var stack=new ItemStack(block(DEVICES[i]).asItem());owner.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
            var result=((BlockItem)stack.getItem()).place(new BlockPlaceContext(owner,InteractionHand.MAIN_HAND,stack,hit));
            h.assertTrue(result.consumesAction()&&h.getLevel().getBlockEntity(pos) instanceof LabProcessingEntity,"Native player BlockItem placement failed");
            var machine=(LabProcessingEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(!machine.instituteBound(),"Player placement inherited a facility binding");removePlayer(h,owner);
            var recipe=recipe(DEVICES[i]);feed(machine,recipe,2);var before=machine.saveInventoryTag().getList("Items",10).copy();
            tick(h,machine,recipe.ticks()+10);
            h.assertTrue(!machine.powered()&&machine.progress()==0&&machine.getItem(2).isEmpty()&&before.equals(machine.saveInventoryTag().getList("Items",10)),"Unbound device fabricated power or consumed materials");
        }
        h.runAfterDelay(8,()->{for(int i=0;i<DEVICES.length;i++) { var machine=(LabProcessingEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(12+i*4,2,12)));h.assertTrue(machine.progress()==0&&!machine.powered(),"Native ticker powered an independent device");machine.clearContent(); }h.succeed();});
    }

    @GameTest(template="empty64",templateNamespace="ender_processing_checks",timeoutTicks=100,batch="processing_recipe")
    public static void realFacilityProcessesBothRecipesExactlyOnceAndRecordsSharedFlags(GameTestHelper h) {
        for(String device:DEVICES) {
            var original=poweredFacility(h,device);var bindingTag=original.machine.saveInventoryTag();var recipe=recipe(device);
            var beforeReplacement=InstituteApi.facility(h.getLevel(),original.generator).orElseThrow();
            h.assertTrue(beforeReplacement.repaired()&&beforeReplacement.servicePowered()&&!(device.equals("lab_microscope")?beforeReplacement.analyzed():beforeReplacement.separated()),"Unbound replacement regression must begin with a powered facility and an unfinished milestone");
            var machine=place(h,original.machine.getBlockPos(),device);var fixture=new Fixture(original.origin,original.generator,original.distribution,machine,original.player,original.forced);
            try {
                feed(machine,recipe,1);var untouchedItems=machine.saveInventoryTag().getList("Items",10).copy();
                h.assertTrue(!machine.instituteBound()&&!InstituteApi.isPowered(h.getLevel(),machine.getBlockPos()),"A fresh unbound processor borrowed facility power merely by occupying its manifest coordinate");
                tick(h,machine,recipe.ticks()+5);InstituteApi.recordProcess(h.getLevel(),machine.getBlockPos(),recipe.id());
                h.assertTrue(!machine.powered()&&machine.progress()==0&&machine.getItem(2).isEmpty()&&untouchedItems.equals(machine.saveInventoryTag().getList("Items",10)),"Unbound manifest-position device consumed materials or advanced processing");
                h.assertTrue(InstituteApi.facility(h.getLevel(),fixture.generator).orElseThrow().equals(beforeReplacement),"An unbound replacement or its public completion callback advanced shared facility progress");
                System.out.println("PROCESSING_UNBOUND_REPLACEMENT_PASS device="+device+" powered=false progress=0 materials_unchanged=true");
                machine.clearContent();load(h,machine,bindingTag);InstituteOperations.registerBound(h.getLevel(),machine.getBlockPos(),machine.instituteOffset());h.assertTrue(InstituteApi.isPowered(h.getLevel(),machine.getBlockPos()),"Restoring the real template binding did not restore eligible power");
                feed(machine,recipe,2);tick(h,machine,recipe.ticks()-1);
                h.assertTrue(machine.getItem(2).isEmpty()&&machine.getItem(0).getCount()==recipe.inputCount()*2,"Processor consumed before its last powered tick");tick(h,machine,1);
                h.assertTrue(machine.getItem(2).is(item(recipe.outputId()))&&machine.getItem(2).getCount()==recipe.outputCount()&&machine.getItem(0).getCount()==recipe.inputCount(),"Actual recipe did not atomically exchange one batch");
                tick(h,machine,recipe.ticks());h.assertTrue(machine.getItem(2).getCount()==recipe.outputCount()*2&&machine.getItem(0).isEmpty(),"Second batch failed to stack or duplicated input");
                var state=InstituteApi.facility(h.getLevel(),machine.getBlockPos()).orElseThrow();h.assertTrue(device.equals("lab_microscope")?state.analyzed():state.separated(),"Completion was not recorded on shared facility progress");
            } finally {cleanup(h,fixture);}
        }
        h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_processing_checks",timeoutTicks=100,batch="processing_pause_save")
    public static void powerLossAndFullOutputPausePersistedProgressWithoutConsuming(GameTestHelper h) {
        var fixture=poweredFacility(h,"lab_centrifuge"); var machine=fixture.machine; var recipe=recipe("lab_centrifuge");feed(machine,recipe,1); tick(h,machine,37);
        fixture.player.setPos(fixture.distribution.getX()+.5,fixture.distribution.getY(),fixture.distribution.getZ()-1.5);InstituteOperations.useControl(h.getLevel(),fixture.distribution,fixture.player,"distribution");tick(h,machine,50);
        h.assertTrue(!machine.powered()&&machine.progress()==37&&machine.getItem(0).getCount()==recipe.inputCount(),"Power loss discarded progress or consumed input");
        var tag=machine.saveInventoryTag();machine=reload(h,machine,tag);InstituteOperations.registerBound(h.getLevel(),machine.getBlockPos(),machine.instituteOffset());
        h.assertTrue(machine.progress()==37&&machine.instituteBound(),"Save/reload lost progress or facility binding");
        InstituteOperations.useControl(h.getLevel(),fixture.distribution,fixture.player,"distribution");machine.setItem(2,new ItemStack(item(recipe.outputId()),64));tick(h,machine,50);
        h.assertTrue(machine.powered()&&machine.progress()==37&&machine.getItem(1).getCount()==recipe.reagentCount(),"Full output consumed materials or lost paused progress");
        machine.setItem(2,ItemStack.EMPTY);tick(h,machine,recipe.ticks()-37);h.assertTrue(machine.getItem(2).getCount()==recipe.outputCount()&&machine.getItem(0).isEmpty(),"Restored machine did not finish the remaining powered ticks");
        cleanup(h,new Fixture(fixture.origin,fixture.generator,fixture.distribution,machine,fixture.player,fixture.forced));h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_processing_checks",timeoutTicks=140,batch="processing_change")
    public static void changedInputsClearProgressAndNativeTickerCompletesRemainingWork(GameTestHelper h) {
        var fixture=poweredFacility(h,"lab_microscope");var machine=fixture.machine;var recipe=recipe("lab_microscope");feed(machine,recipe,1);tick(h,machine,23);machine.setItem(0,new ItemStack(Items.DIRT));tick(h,machine,1);
        h.assertTrue(machine.progress()==0&&machine.activeRecipe().isEmpty()&&machine.getItem(2).isEmpty(),"Recipe change retained old progress");feed(machine,recipe,1);tick(h,machine,recipe.ticks()-4);
        var cleaned=new java.util.concurrent.atomic.AtomicBoolean();Runnable close=()->{if(cleaned.compareAndSet(false,true))cleanup(h,fixture);};
        java.util.function.Supplier<String> diagnostic=()->{var pos=machine.getBlockPos();var chunk=h.getLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return "progress="+machine.progress()+" powered="+machine.powered()+" removed="+machine.isRemoved()+" same_be="+(chunk!=null&&h.getLevel().getBlockEntity(pos)==machine)+" full_status="+(chunk==null?"UNLOADED":chunk.getFullStatus())+" should_tick="+h.getLevel().shouldTickBlocksAt(new ChunkPos(pos).toLong());};
        h.runAfterDelay(100,()->{try{h.fail("Native processor did not finish within 100 ticks: "+diagnostic.get());}finally{close.run();}});
        h.succeedWhen(()->{
            if(h.getTick()%20==0)System.out.println("PROCESSING_NATIVE_TICK_WAIT "+diagnostic.get());
            h.assertTrue(machine.getItem(2).is(item(recipe.outputId()))&&machine.getItem(2).getCount()==recipe.outputCount()&&machine.getItem(0).isEmpty(),"Registered native BE ticker has not completed: "+diagnostic.get());
            System.out.println("PROCESSING_NATIVE_TICK_PASS "+diagnostic.get());close.run();
        });
    }

    @GameTest(template="empty64",templateNamespace="ender_processing_checks",timeoutTicks=100,batch="processing_menus")
    public static void realMenusShareInventoryFilterShiftClickAndNeverAcceptOutputInjection(GameTestHelper h) {
        var machine=place(h,h.absolutePos(new BlockPos(12,2,12)),"lab_centrifuge");var recipe=recipe("lab_centrifuge");var first=player(h,machine.getBlockPos());var second=player(h,machine.getBlockPos());var a=open(h,first,machine);var b=open(h,second,machine);
        first.getInventory().setItem(9,new ItemStack(item(recipe.inputId()),3));a.quickMoveStack(first,3);first.getInventory().setItem(9,new ItemStack(item(recipe.reagentId()),2));a.quickMoveStack(first,3);
        h.assertTrue(machine.getItem(0).getCount()==3&&machine.getItem(1).getCount()==2&&b.getSlot(0).getItem()==machine.getItem(0),"Menus failed to share/filter real server inventory");
        a.setCarried(new ItemStack(item(recipe.outputId()),5)); a.clicked(2,0,ClickType.PICKUP,first);
        h.assertTrue(machine.getItem(2).isEmpty()&&a.getCarried().getCount()==5,"Clicked output accepted injected items");a.setCarried(ItemStack.EMPTY);
        first.getInventory().setItem(9,new ItemStack(item(recipe.outputId()),5));a.quickMoveStack(first,3);h.assertTrue(machine.getItem(2).isEmpty(),"Shift-click inserted crafted output");
        machine.setItem(2,new ItemStack(item(recipe.outputId()),7));a.quickMoveStack(first,2);h.assertTrue(machine.getItem(2).isEmpty(),"Shift-click could not extract actual output");
        var update=machine.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(update.isEmpty(),"Chunk packet disclosed private processing slots/progress or binding");
        removePlayer(h,first);removePlayer(h,second);machine.clearContent();h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_processing_checks",timeoutTicks=100,batch="processing_drops")
    public static void destroyingDeviceDropsEveryRemainingStackOnceAndInvalidatesMenus(GameTestHelper h) {
        for(int i=0;i<DEVICES.length;i++) {
            var pos=h.absolutePos(new BlockPos(12+i*6,2,12));var machine=place(h,pos,DEVICES[i]);var recipe=recipe(DEVICES[i]);feed(machine,recipe,3);machine.setItem(2,new ItemStack(item(recipe.outputId()),4));var player=player(h,pos);var menu=open(h,player,machine);
            h.getLevel().destroyBlock(pos,false);h.getLevel().destroyBlock(pos,false);
            int inputs=0,reagents=0,outputs=0;for(var drop:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2))) {var stack=drop.getItem();if(stack.is(item(recipe.inputId())))inputs+=stack.getCount();if(recipe.reagentCount()>0&&stack.is(item(recipe.reagentId())))reagents+=stack.getCount();if(stack.is(item(recipe.outputId())))outputs+=stack.getCount();drop.discard();}
            h.assertTrue(inputs==recipe.inputCount()*3&&reagents==recipe.reagentCount()*3&&outputs==4&&!menu.stillValid(player)&&machine.isEmpty(),"Destroyed processing inventory lost or duplicated stacks or retained usable menu");removePlayer(h,player);
        }
        h.succeed();
    }
}

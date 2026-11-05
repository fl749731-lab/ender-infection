package dev.hui.enderinfection.client;

import com.google.gson.*;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.api.institute.*;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.institute.*;
import dev.hui.enderinfection.processing.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real-client acceptance. Business changes use native player interaction/menu packets. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class InstituteOperationsVisualCheck {
    private static final String WORLD="InstituteOperations_"+System.currentTimeMillis();
    private static final BlockPos ORIGIN=new BlockPos(0,-64,0),MEDICAL=ORIGIN.offset(108,29,94);
    private static final Map<String,BlockPos> CONTROLS=new HashMap<>();
    private static final Set<ChunkPos> CHUNKS=new LinkedHashSet<>();
    private static final List<BlockPos> TARGETS=new ArrayList<>();
    private static final List<String> CAPTURED=new ArrayList<>();
    private static final Set<String> MENUS=new HashSet<>();
    private static BlockPos door,microscope,centrifuge,serviceLamp,containmentLamp;
    private static BlockPos aimAt;private static boolean inside,frame;
    private static double aimHeight=.6;private static int step,age,stable,ticks;
    private static long started,actionEvents;private static volatile long receivedInteractions;
    private static boolean launched,finished;private static volatile boolean built,busy,initialLightSync;
    private static volatile String failure;private static volatile View view;
    private static boolean emergencyPassed,archiveOnce;
    private record View(FacilityStatus facility,Vec3 playerPos,long interactions,int minimumLight,
            boolean doorOpen,boolean emptyHand,int fuses,int copper,int redstone,int cards,int records,int reagents,int archives,int crystals,
            int menuId,BlockPos menuMachine,int microProgress,String microOutput,int centrifugeProgress,String centrifugeOutput){}
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.operationsVisualCheck");}
    private static ResourceLocation id(String value){return new ResourceLocation(value.contains(":")?value:"ender_infection:"+value);}
    private static Block block(String key){return BuiltInRegistries.BLOCK.getOptional(id(key)).orElseThrow();}
    private static Item item(String key){return BuiltInRegistries.ITEM.getOptional(id(key)).orElseThrow();}
    private static BlockPos pos(JsonElement value){if(value.isJsonArray()){var a=value.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}var o=value.getAsJsonObject();return new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt());}
    private static boolean is(ItemStack stack,String key){return stack.is(item(key));}
    private static String stackId(ItemStack stack){return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private static int count(ServerPlayer player,String key){int n=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(is(player.getInventory().getItem(i),key))n+=player.getInventory().getItem(i).getCount();return n;}
    private static int clientCount(Minecraft mc,String key){int n=0;for(int i=0;i<mc.player.getInventory().getContainerSize();i++)if(is(mc.player.getInventory().getItem(i),key))n+=mc.player.getInventory().getItem(i).getCount();return n;}
    @SubscribeEvent public static void interaction(PlayerInteractEvent.RightClickBlock event){
        if(enabled()&&event.getEntity() instanceof ServerPlayer player&&player.server.getWorldData().getLevelName().equals(WORLD))receivedInteractions++;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            JsonObject data;try(var reader=player.server.getResourceManager().getResourceOrThrow(id("central_institute.json")).openAsReader()){data=JsonParser.parseReader(reader).getAsJsonObject();}
            var ops=data.getAsJsonObject("operations");
            for(var value:ops.getAsJsonArray("controls")){var entry=value.getAsJsonObject();CONTROLS.put(entry.get("role").getAsString(),ORIGIN.offset(pos(entry.get("pos"))));}
            if(!CONTROLS.keySet().containsAll(Set.of("generator","distribution","terminal","archive")))throw new IllegalStateException("Missing four real manifest controls");
            door=ORIGIN.offset(pos(ops.getAsJsonArray("secured_doors").get(0).getAsJsonObject().get("pos")));
            for(var value:ops.getAsJsonArray("machines")){var entry=value.getAsJsonObject();String device=entry.get("device").getAsString();
                if(microscope==null&&device.endsWith(":lab_microscope")&&entry.get("zone").getAsString().equals("service"))microscope=ORIGIN.offset(pos(entry.get("pos")));
                if(centrifuge==null&&device.endsWith(":lab_centrifuge")&&entry.get("zone").getAsString().equals("containment"))centrifuge=ORIGIN.offset(pos(entry.get("pos")));
            }
            for(var value:ops.getAsJsonArray("managed_lights")){var entry=value.getAsJsonObject();var at=ORIGIN.offset(pos(entry.get("pos")));
                if(entry.get("zone").getAsString().equals("service")&&(serviceLamp==null||at.distSqr(CONTROLS.get("distribution"))<serviceLamp.distSqr(CONTROLS.get("distribution"))))serviceLamp=at;
                if(containmentLamp==null&&entry.get("zone").getAsString().equals("containment"))containmentLamp=at;
            }
            if(microscope==null||centrifuge==null||serviceLamp==null||containmentLamp==null)throw new IllegalStateException("Missing two device/circuit manifest fixtures");
            TARGETS.addAll(CONTROLS.values());TARGETS.addAll(List.of(door,microscope,centrifuge,serviceLamp,containmentLamp,MEDICAL));
            for(var at:TARGETS)for(int x=(at.getX()>>4)-1;x<=(at.getX()>>4)+1;x++)for(int z=(at.getZ()>>4)-1;z<=(at.getZ()>>4)+1;z++)CHUNKS.add(new ChunkPos(x,z));
            var requests=new ArrayList<CompletableFuture<?>>();for(var chunk:CHUNKS){level.setChunkForced(chunk.x,chunk.z,true);requests.add(level.getChunkSource().getChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true));}
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenRun(()->player.server.execute(()->{try{createScene(player,ops);}catch(Exception e){failure=e.toString();}})).exceptionally(e->{failure=e.toString();return null;});
        }catch(Exception e){failure=e.toString();}
    }
    private static CompoundTag bindingTag(BlockPos world){var offset=world.subtract(ORIGIN);var tag=new CompoundTag();tag.putBoolean("InstituteBound",true);tag.putInt("InstituteOffsetX",offset.getX());tag.putInt("InstituteOffsetY",offset.getY());tag.putInt("InstituteOffsetZ",offset.getZ());return tag;}
    private static void createScene(ServerPlayer player,JsonObject ops){
        var level=player.serverLevel();
        for(var at:TARGETS)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=0;y<5;y++)level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
        for(var at:TARGETS)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlock(at.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),18);
        for(var entry:CONTROLS.entrySet()){
            String key=switch(entry.getKey()){case "generator"->"lab_power_generator";case "distribution"->"lab_distribution_panel";case "terminal"->"lab_research_terminal";default->"lab_archive_terminal";};
            var at=entry.getValue();level.setBlock(at,block(key).defaultBlockState().setValue(OperationsControlBlock.FACING,Direction.NORTH),18);
            ((OperationsControlEntity)level.getBlockEntity(at)).bind(at.subtract(ORIGIN));
        }
        var doorBlock=(LabFixtureBlock)block("lab_door");var state=doorBlock.defaultBlockState().setValue(LabFixtureBlock.FACING,Direction.SOUTH).setValue(LabFixtureBlock.SECURED,true);
        level.setBlock(door,state,18);doorBlock.fillParts(level,door,state);var doorEntity=(LabFixtureEntity)level.getBlockEntity(door);doorEntity.load(bindingTag(door));
        for(var at:List.of(microscope,centrifuge)){
            level.setBlock(at,block(at.equals(microscope)?"lab_microscope":"lab_centrifuge").defaultBlockState().setValue(LabProcessingBlock.FACING,Direction.NORTH),18);
            var processor=(LabProcessingEntity)level.getBlockEntity(at);processor.load(bindingTag(at));InstituteOperations.registerBound(level,at,at.subtract(ORIGIN));
        }
        for(var value:ops.getAsJsonArray("managed_lights")){var entry=value.getAsJsonObject();var at=ORIGIN.offset(pos(entry.get("pos")));
            if(at.equals(serviceLamp)||at.equals(containmentLamp))level.setBlock(at,block(entry.get("block").getAsString()).defaultBlockState(),18);
        }
        for(var at:TARGETS){level.setBlock(at.north(2).above(3),Blocks.SEA_LANTERN.defaultBlockState(),3);for(int x=-3;x<=3;x++)for(int y=0;y<4;y++)if(!at.equals(door)&&!reserved(at.offset(x,y,4)))level.setBlock(at.offset(x,y,4),Blocks.WHITE_CONCRETE.defaultBlockState(),18);}
        var bed=block("lab_medical_bed");for(int dx:new int[]{-2,1}){var foot=MEDICAL.offset(dx,0,1);level.setBlock(foot,bed.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,BedPart.FOOT),18);level.setBlock(foot.north(),bed.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,BedPart.HEAD),18);}
        for(int dx:new int[]{-1,0,1})level.setBlock(MEDICAL.offset(dx,0,3),block("lab_waiting_sofa").defaultBlockState().setValue(LabDetailBlock.FACING,Direction.NORTH),18);
        player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;player.getInventory().clearContent();setAim(serviceLamp,false,false,.7);teleport(player);built=true;
        System.out.println("INSTITUTE_OPERATIONS_SCENE origin="+ORIGIN+" loaded_chunks="+CHUNKS.size()+" manifest_controls=4 managed_lights=2 machines=2");
    }
    private static boolean reserved(BlockPos cell){for(var target:TARGETS){var d=cell.subtract(target);if(Math.abs(d.getX())<=2&&Math.abs(d.getZ())<=2&&d.getY()>=0&&d.getY()<=3)return true;}return false;}
    private static Vec3 camera(){return new Vec3(aimAt.getX()+.5,aimAt.getY(),aimAt.getZ()+(inside?3.5:-2.5));}
    private static Vec3 target(){return new Vec3(aimAt.getX()+(frame?.03:aimAt.equals(microscope)?.62:.5),aimAt.getY()+aimHeight,aimAt.getZ()+.5);}
    private static float yaw(){var d=target().subtract(camera());return (float)Math.toDegrees(Math.atan2(-d.x,d.z));}
    private static float pitch(){var d=target().subtract(camera().add(0,1.62,0));return (float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
    private static void setAim(BlockPos at,boolean inner,boolean edge,double height){aimAt=at;inside=inner;frame=edge;aimHeight=height;age=stable=0;}
    private static void teleport(ServerPlayer player){var p=camera();player.teleportTo(player.serverLevel(),p.x,p.y,p.z,yaw(),pitch());}
    private static void call(Minecraft mc,Consumer<ServerPlayer> action){if(busy)return;busy=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{try{var player=server.getPlayerList().getPlayer(uuid);if(player!=null)action.accept(player);}catch(Exception e){failure=e.toString();}finally{busy=false;}});}
    private static void go(Minecraft mc,BlockPos at,boolean inner,boolean edge,double height,int next){setAim(at,inner,edge,height);step=next;call(mc,InstituteOperationsVisualCheck::teleport);}
    private static void selected(Minecraft mc,int slot){mc.player.getInventory().selected=slot;mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));}
    private static void localCamera(Minecraft mc){var p=camera();mc.setCameraEntity(mc.player);mc.player.setPos(p.x,p.y,p.z);mc.player.setYRot(yaw());mc.player.setXRot(pitch());mc.player.yRotO=yaw();mc.player.xRotO=pitch();mc.player.setDeltaMovement(0,0,0);}
    private static void sendLight(ServerPlayer player,boolean full){for(var at:CHUNKS){var chunk=player.serverLevel().getChunkSource().getChunkNow(at.x,at.z);if(chunk!=null)player.connection.send(full?new ClientboundLevelChunkWithLightPacket(chunk,player.serverLevel().getLightEngine(),null,null):new ClientboundLightUpdatePacket(at,player.serverLevel().getLightEngine(),null,null));}}
    private static void poll(Minecraft mc){call(mc,player->{
        var level=player.serverLevel();int light=15;for(var at:TARGETS)light=Math.min(light,level.getBrightness(LightLayer.BLOCK,at.north().above()));
        if(light>=12&&!initialLightSync){sendLight(player,true);initialLightSync=true;}
        var micro=(LabProcessingEntity)level.getBlockEntity(microscope);var centrif=(LabProcessingEntity)level.getBlockEntity(centrifuge);
        BlockPos menuPos=null;int menuId=0;if(player.containerMenu instanceof LabProcessingMenu menu&&menu.machine() instanceof LabProcessingEntity processor){menuPos=processor.getBlockPos();menuId=menu.containerId;}
        var facility=InstituteApi.facility(level,CONTROLS.get("generator")).orElseThrow();
        view=new View(facility,player.position(),receivedInteractions,light,level.getBlockState(door).getValue(LabFixtureBlock.OPEN),player.getMainHandItem().isEmpty(),count(player,"lab_fuse"),count(player,"minecraft:copper_ingot"),count(player,"minecraft:redstone"),count(player,"research_keycard"),count(player,"research_record"),count(player,"separated_reagent"),count(player,"core_archive"),count(player,"void_crystal"),menuId,menuPos,micro.progress(),stackId(micro.getItem(2)),centrif.progress(),stackId(centrif.getItem(2)));
    });}
    private static boolean lamp(Minecraft mc,BlockPos at,boolean expected){var state=mc.level.getBlockState(at);return state.hasProperty(LabDetailBlock.POWERED)&&state.getValue(LabDetailBlock.POWERED)==expected;}
    private static boolean ready(Minecraft mc){return view!=null&&initialLightSync&&!busy&&mc.screen==null&&mc.getOverlay()==null&&view.playerPos.distanceToSqr(camera())<.05&&mc.level.getBrightness(LightLayer.BLOCK,aimAt.north().above())>=12&&mc.levelRenderer.isChunkCompiled(aimAt)&&mc.levelRenderer.hasRenderedAllChunks()&&age>=30;}
    private static void use(Minecraft mc,boolean shift,int next){
        if(shift){mc.player.setShiftKeyDown(true);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));}
        var from=mc.player.getEyePosition(1);var hit=mc.level.clip(new ClipContext(from,from.add(target().subtract(from).normalize().scale(4.8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!(hit.getBlockPos().equals(aimAt)||aimAt.equals(door)&&hit.getBlockPos().equals(door.above()))){failure="Native ray missed intended target step="+step+" hit="+hit.getBlockPos()+" expected="+aimAt;return;}
        actionEvents=receivedInteractions;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
        if(shift){mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
        step=next;age=stable=0;
    }
    private static boolean receipt(){return view!=null&&view.interactions>actionEvents&&age>=12;}
    private static LabProcessingMenu menu(Minecraft mc,BlockPos expected){
        if(!(mc.screen instanceof LabProcessingScreen screen)||!(mc.player.containerMenu instanceof LabProcessingMenu menu))return null;
        if(screen.getMenu()!=menu||menu.slots.size()!=39||view.menuId!=menu.containerId||!expected.equals(view.menuMachine))return null;
        return menu;
    }
    private static void move(Minecraft mc,LabProcessingMenu menu,int slot,int next){mc.gameMode.handleInventoryMouseClick(menu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);step=next;age=stable=0;}
    private static void capture(Minecraft mc,String name)throws Exception{if(CAPTURED.contains(name))throw new IllegalStateException("Repeated capture "+name);var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(folder.resolve(name+".png"));}CAPTURED.add(name);System.out.println("INSTITUTE_OPERATIONS_CAPTURE "+name);}
    private static void finish(Minecraft mc,boolean pass){finished=true;String result=pass?"INSTITUTE_OPERATIONS_VISUAL_PASS screenshots="+CAPTURED.size()+" processing_menus="+MENUS.size()+" archive_once="+archiveOnce:"INSTITUTE_OPERATIONS_VISUAL_FAIL step="+step+" "+failure;System.out.println(result);try{Files.writeString(mc.gameDirectory.toPath().resolve("operations-visual-result.txt"),result+"\nshots="+String.join(",",CAPTURED)+"\n");}catch(Exception e){throw new RuntimeException(e);}mc.stop();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){launched=true;started=System.currentTimeMillis();mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.renderDistance().set(6);mc.options.hideGui=false;mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
        if(failure!=null){finish(mc,false);return;}if(System.currentTimeMillis()-started>600000){failure="timeout before completion step="+step;finish(mc,false);return;}if(!built||mc.player==null||mc.level==null)return;
        age++;ticks++;localCamera(mc);if(ticks%5==0&&!busy)poll(mc);if(view==null)return;
        try{if(!busy)switch(step){
            case 0->{if(ready(mc)&&!view.facility.repaired()&&lamp(mc,serviceLamp,false)&&lamp(mc,containmentLamp,false)){
                capture(mc,"power_off");call(mc,p->{p.getInventory().setItem(0,new ItemStack(item("lab_fuse")));p.getInventory().setItem(9,new ItemStack(Items.COPPER_INGOT,4));p.getInventory().setItem(10,new ItemStack(Items.REDSTONE,4));p.inventoryMenu.broadcastChanges();});selected(mc,0);setAim(CONTROLS.get("generator"),false,false,.6);step=1;}}
            case 1->{if(!busy){go(mc,CONTROLS.get("generator"),false,false,.6,2);}}
            case 2->{if(ready(mc)&&is(mc.player.getMainHandItem(),"lab_fuse"))use(mc,false,3);}
            case 3->{if(receipt()&&view.facility.repaired()&&view.fuses==0&&view.copper==0&&view.redstone==0){System.out.println("INSTITUTE_OPERATIONS_REPAIR_PASS paid_fuse=1 copper=4 redstone=4");selected(mc,8);go(mc,CONTROLS.get("distribution"),false,false,.6,4);}}
            case 4->{if(ready(mc))use(mc,false,5);}
            case 5->{if(receipt()&&view.facility.servicePowered())use(mc,true,6);}
            case 6->{if(receipt()&&view.facility.servicePowered()&&view.facility.containmentPowered()&&lamp(mc,serviceLamp,true)&&lamp(mc,containmentLamp,true)){go(mc,serviceLamp,false,false,.7,7);}}
            case 7->{if(ready(mc)){capture(mc,"power_on");selected(mc,8);go(mc,door,false,false,1.2,8);}}
            case 8->{if(ready(mc))use(mc,false,9);}
            case 9->{if(receipt()&&!view.doorOpen&&view.cards==0){capture(mc,"door_denied");System.out.println("INSTITUTE_OPERATIONS_DOOR_DENIED_PASS powered=true keycard=false");go(mc,CONTROLS.get("terminal"),false,false,.6,10);}}
            case 10->{if(ready(mc))use(mc,false,11);}
            case 11->{if(receipt()&&view.cards==1){selected(mc,0);go(mc,door,false,false,1.2,12);}}
            case 12->{if(ready(mc)&&is(mc.player.getMainHandItem(),"research_keycard"))use(mc,false,13);}
            case 13->{if(receipt()&&view.doorOpen&&mc.level.getBlockState(door).getValue(LabFixtureBlock.OPEN)){capture(mc,"door_unlocked");go(mc,door,false,true,1.1,14);}}
            case 14->{if(ready(mc))use(mc,false,15);}
            case 15->{if(receipt()&&!view.doorOpen){selected(mc,8);go(mc,CONTROLS.get("distribution"),false,false,.6,16);}}
            case 16->{if(ready(mc))use(mc,true,17);}
            case 17->{if(receipt()&&!view.facility.containmentPowered()){go(mc,door,true,false,1.2,18);}}
            case 18->{if(ready(mc))use(mc,false,19);}
            case 19->{if(receipt()&&view.doorOpen&&view.emptyHand&&!view.facility.containmentPowered()){emergencyPassed=true;System.out.println("INSTITUTE_OPERATIONS_EMERGENCY_PASS hand_empty=true containment=false");go(mc,door,true,true,1.1,20);}}
            case 20->{if(ready(mc))use(mc,false,21);}
            case 21->{if(receipt()&&!view.doorOpen){go(mc,CONTROLS.get("distribution"),false,false,.6,22);}}
            case 22->{if(ready(mc))use(mc,true,23);}
            case 23->{if(receipt()&&view.facility.containmentPowered()){call(mc,p->{p.getInventory().setItem(9,new ItemStack(item("raw_tissue_sample")));p.getInventory().setItem(10,new ItemStack(item("raw_tissue_sample")));p.getInventory().setItem(11,new ItemStack(Items.GLASS_BOTTLE));p.inventoryMenu.broadcastChanges();});step=24;age=0;}}
            case 24->{if(!busy)go(mc,microscope,false,false,.55,25);}
            case 25->{if(ready(mc))use(mc,false,26);}
            case 26->{var m=menu(mc,microscope);if(m!=null&&m.powered()&&age>=20&&is(m.getSlot(3).getItem(),"raw_tissue_sample"))move(mc,m,3,27);}
            case 27->{var m=menu(mc,microscope);if(m!=null&&m.powered()&&m.progress()>=12&&view.microProgress>=12&&m.duration()>0&&age>=20&&++stable>=8){capture(mc,"microscope_running");MENUS.add("lab_microscope");System.out.println("INSTITUTE_OPERATIONS_MENU_PASS lab_microscope machine_slots=3 player_slots=36 powered=true progress="+m.progress()+" duration="+m.duration()+" container_id="+m.containerId);step=28;age=stable=0;}}
            case 28->{var m=menu(mc,microscope);if(m!=null&&is(m.getSlot(2).getItem(),"research_record")&&view.facility.analyzed()&&view.microOutput.equals("ender_infection:research_record")&&++stable>=8){capture(mc,"microscope_done");move(mc,m,2,29);}}
            case 29->{if(view.records==1&&view.microOutput.isEmpty()){mc.player.closeContainer();go(mc,centrifuge,false,false,.3,30);}}
            case 30->{if(ready(mc))use(mc,false,31);}
            case 31->{var m=menu(mc,centrifuge);if(m!=null&&m.powered()&&age>=20&&is(m.getSlot(4).getItem(),"raw_tissue_sample"))move(mc,m,4,32);}
            case 32->{var m=menu(mc,centrifuge);if(m!=null&&is(m.getSlot(0).getItem(),"raw_tissue_sample")&&m.getSlot(5).getItem().is(Items.GLASS_BOTTLE))move(mc,m,5,33);}
            case 33->{var m=menu(mc,centrifuge);if(m!=null&&m.powered()&&m.progress()>=20&&view.centrifugeProgress>=20&&m.duration()>0&&age>=20&&++stable>=8){capture(mc,"centrifuge_running");MENUS.add("lab_centrifuge");System.out.println("INSTITUTE_OPERATIONS_MENU_PASS lab_centrifuge machine_slots=3 player_slots=36 powered=true progress="+m.progress()+" duration="+m.duration()+" container_id="+m.containerId);step=34;age=stable=0;}}
            case 34->{var m=menu(mc,centrifuge);if(m!=null&&is(m.getSlot(2).getItem(),"separated_reagent")&&view.facility.separated()&&view.centrifugeOutput.equals("ender_infection:separated_reagent")&&++stable>=8){capture(mc,"centrifuge_done");move(mc,m,2,35);}}
            case 35->{if(view.records==1&&view.reagents==1&&view.centrifugeOutput.isEmpty()){mc.player.closeContainer();go(mc,CONTROLS.get("archive"),false,false,.6,36);}}
            case 36->{if(ready(mc))use(mc,false,37);}
            case 37->{if(receipt()&&view.facility.archiveClaimed()&&view.archives==1&&view.crystals==2&&view.records==0&&view.reagents==0&&ready(mc))use(mc,false,38);}
            case 38->{if(receipt()&&view.facility.archiveClaimed()&&view.archives==1&&view.crystals==2){archiveOnce=true;mc.setScreen(new InventoryScreen(mc.player));step=39;age=0;}}
            case 39->{if(mc.screen instanceof InventoryScreen&&age>=20&&clientCount(mc,"core_archive")==1&&clientCount(mc,"void_crystal")==2){capture(mc,"archive_reward");mc.player.closeContainer();mc.options.hideGui=true;go(mc,MEDICAL,false,false,.8,40);}}
            case 40->{if(ready(mc)&&emergencyPassed&&archiveOnce&&MENUS.size()==2){if(!mc.level.getBlockState(MEDICAL.offset(-2,0,1)).is(block("lab_medical_bed"))||!mc.level.getBlockState(MEDICAL.offset(-2,0,0)).is(block("lab_medical_bed")))throw new IllegalStateException("Medical bed lost a native half");capture(mc,"medical_furniture");if(CAPTURED.size()!=10)throw new IllegalStateException("Wrong final screenshot count "+CAPTURED.size());finish(mc,true);}}
        }}catch(Exception e){failure=e.toString();}
        if(ticks%100==0){System.out.println("INSTITUTE_OPERATIONS_VISUAL_WAIT step="+step+" age="+age+" light="+mc.level.getBrightness(LightLayer.BLOCK,aimAt.north().above())+" server_light="+view.minimumLight+" screen="+(mc.screen==null?"none":mc.screen.getClass().getSimpleName()));if(!busy)call(mc,p->sendLight(p,false));}
        if(System.currentTimeMillis()-started>600000){failure="timeout step="+step;finish(mc,false);}
    }
}

package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.institute.*;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real template room + native client interaction packets. Combat kills are acceptance-fixture actions. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class ContainmentIncidentVisualCheck {
    private static final String WORLD="E8Incident_"+System.currentTimeMillis();
    private static final BlockPos ORIGIN=new BlockPos(0,-40,0);
    private static final List<String> SHOTS=new ArrayList<>();
    private static volatile ContainmentIncident.Definition definition;
    private static volatile ContainmentIncident.Status status;
    private static volatile String failure;
    private static volatile boolean built,busy;
    private static boolean launched,finished;
    private static long began;
    private static int step,age;
    private static BlockPos aim,stand;
    private static boolean shifted;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.incidentVisualCheck");}
    private static Item item(String name){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(name.contains(":")?name:"ender_infection:"+name));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=p.serverLevel();definition=ContainmentIncident.definition(level).orElseThrow();
            var requests=new ArrayList<CompletableFuture<?>>();
            for(int x=4;x<=7;x++)for(int z=3;z<=6;z++){level.setChunkForced(x,z,true);requests.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));}
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenRun(()->p.server.execute(()->{
                try{
                    var clip=new BoundingBox(80,ORIGIN.getY()+13,64,111,ORIGIN.getY()+22,95);int tiles=0;
                    for(var tile:CentralInstitute.tiles())if(tile.x()>=80&&tile.x()<112&&tile.z()>=64&&tile.z()<96){var at=ORIGIN.offset(tile.x(),tile.y(),tile.z());if(!CentralInstitute.template(level,tile).placeInWorld(level,at,at,CentralInstitute.settings(clip),level.random,18))throw new IllegalStateException("Room tile failed");tiles++;}
                    if(tiles!=4)throw new IllegalStateException("Expected four clipped real templates");
                    InstituteOperations.registerFacility(level,ORIGIN);var data=OperationsData.get(level).save(new CompoundTag());for(var value:data.getList("Facilities",10)){var f=(CompoundTag)value;if(f.getLong("Origin")==ORIGIN.asLong()){for(String key:List.of("Repaired","ContainmentPowered","Analyzed","Separated"))f.putBoolean(key,true);}}
                    level.getDataStorage().set(OperationsData.NAME,OperationsData.load(data));
                    ((OperationsControlEntity)level.getBlockEntity(ORIGIN.offset(definition.control().pos()))).bind(definition.control().pos());
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);p.server.setDifficulty(Difficulty.NORMAL,true);
                    p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.setNoGravity(true);p.getInventory().clearContent();
                    for(var key:List.of("research_keycard","minecraft:copper_ingot","minecraft:redstone"))p.getInventory().add(new ItemStack(item(key),key.endsWith("copper_ingot")?2:1));
                    point(p,definition.control());built=true;System.out.println("INCIDENT_VISUAL_SCENE actual_template_tiles=4 clipped_room=E8 research_prerequisites=fixture");
                }catch(Exception e){failure=e.toString();}
            })).exceptionally(e->{failure=e.toString();return null;});
        }catch(Exception e){failure=e.toString();}
    }
    private static void call(Minecraft mc,Consumer<ServerPlayer> work){
        if(busy)return;busy=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);work.accept(p);}catch(Exception e){failure=e.toString();}finally{busy=false;}});
    }
    private static void point(ServerPlayer p,ContainmentIncident.Target target){aim=ORIGIN.offset(target.pos());stand=ORIGIN.offset(target.approach());var d=Vec3.atCenterOf(aim).subtract(Vec3.atBottomCenterOf(stand).add(0,1.62,0));p.teleportTo(p.serverLevel(),stand.getX()+.5,stand.getY(),stand.getZ()+.5,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    private static void camera(Minecraft mc){if(stand==null)return;var from=Vec3.atBottomCenterOf(stand);var delta=Vec3.atCenterOf(aim).subtract(from.add(0,1.62,0));mc.player.setPos(from);mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
    private static void equip(Minecraft mc,String key){int found=-1;for(int i=0;i<9;i++)if(key.isEmpty()?mc.player.getInventory().getItem(i).isEmpty():mc.player.getInventory().getItem(i).is(item(key))){found=i;break;}if(found<0)throw new IllegalStateException("Missing expected hotbar item "+key);mc.player.getInventory().selected=found;}
    private static boolean use(Minecraft mc){
        if(age<25||busy||mc.screen!=null)return false;
        var from=mc.player.getEyePosition(1);var hit=mc.level.clip(new ClipContext(from,from.add(Vec3.atCenterOf(aim).add(0,.2,.1).subtract(from).normalize().scale(4.8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(aim))throw new IllegalStateException("Native ray missed: "+hit.getBlockPos()+" expected "+aim);
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,shifted?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);age=0;return true;
    }
    private static void shot(Minecraft mc,String name) throws Exception{
        if(SHOTS.contains(name))throw new IllegalStateException("Duplicate screenshot");var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(dir.resolve(name+".png"));}SHOTS.add(name);System.out.println("INCIDENT_VISUAL_CAPTURE "+name);
    }
    private static void overview(Minecraft mc){mc.options.hideGui=true;call(mc,p->{aim=ORIGIN.offset(96,17,73);stand=ORIGIN.offset(104,15,81);p.teleportTo(p.serverLevel(),stand.getX()+.5,stand.getY(),stand.getZ()+.5,135,0);});age=0;}
    private static void fixture(Minecraft mc,String role){call(mc,p->point(p,definition.faults().stream().filter(t->t.role().equals(role)).findFirst().orElseThrow()));age=0;}
    private static void kill(Minecraft mc){call(mc,p->{for(var mob:p.serverLevel().getEntitiesOfClass(InfectedMob.class,definition.bounds(ORIGIN)))mob.hurt(p.serverLevel().damageSources().generic(),100000F);});age=0;}
    private static void finish(Minecraft mc,boolean pass){finished=true;String result=(pass?"INCIDENT_VISUAL_PASS":"INCIDENT_VISUAL_FAIL")+" step="+step+" shots="+SHOTS.size()+" "+failure;System.out.println(result);try{Files.writeString(mc.gameDirectory.toPath().resolve("incident-visual-result.txt"),result+"\n"+String.join("\n",SHOTS)+"\n");}catch(Exception e){throw new RuntimeException(e);}mc.stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished)return;var mc=Minecraft.getInstance();if(began==0)began=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){launched=true;mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.renderDistance().set(4);mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;}
        if(failure!=null||System.currentTimeMillis()-began>360000){if(failure==null)failure="timeout";finish(mc,false);return;}if(!built||mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        camera(mc);age++;
        if(age%5==0&&!busy)call(mc,p->{for(var mob:p.serverLevel().getEntitiesOfClass(InfectedMob.class,definition.bounds(ORIGIN)))mob.setNoAi(true);status=ContainmentIncident.status(p.serverLevel(),ORIGIN.offset(definition.control().pos())).orElseThrow();});
        if(status==null||busy)return;
        if(age>220&&step>0){failure="No progress at step="+step+" state="+status;finish(mc,false);return;}
        try{switch(step){
            case 0->{if(age>50){equip(mc,"research_keycard");if(use(mc)){shot(mc,"terminal_ready");step=1;}}}
            case 1->{if(status.phase()==ContainmentIncident.Phase.WARNING&&age>8){shot(mc,"warning_terminal");overview(mc);step=2;}}
            case 2->{if(status.phase()==ContainmentIncident.Phase.WAVE_ONE&&status.spawned()==3&&age>60){shot(mc,"wave_one");kill(mc);step=3;}}
            case 3->{if(status.phase()==ContainmentIncident.Phase.WAVE_TWO&&status.spawned()==6&&age>90){shot(mc,"wave_two");kill(mc);step=4;}}
            case 4->{if(status.phase()==ContainmentIncident.Phase.ISOLATION){mc.options.hideGui=false;fixture(mc,"cooling");step=5;}}
            case 5->{equip(mc,"minecraft:copper_ingot");if(use(mc))step=6;}
            case 6->{if((status.faults()&1)==0&&age>25){shot(mc,"cooling_fixed");fixture(mc,"sensor");step=7;}}
            case 7->{equip(mc,"minecraft:redstone");if(use(mc))step=8;}
            case 8->{if((status.faults()&2)==0&&age>25){shot(mc,"sensor_fixed");fixture(mc,"valve");step=9;}}
            case 9->{equip(mc,"");shifted=true;if(use(mc))step=10;}
            case 10->{if(status.phase()==ContainmentIncident.Phase.COMPLETE){shifted=false;mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));shot(mc,"isolation_complete");call(mc,p->point(p,definition.control()));step=11;age=0;}}
            case 11->{if(use(mc))step=12;}
            case 12->{if(status.rewardClaimed()&&age>25){shot(mc,"reward_claimed");if(use(mc))step=13;}}
            case 13->{if(age>30){int count=0;for(int i=0;i<36;i++)if(mc.player.getInventory().getItem(i).is(item("void_crystal")))count+=mc.player.getInventory().getItem(i).getCount();if(count!=1)throw new IllegalStateException("Native reward count "+count);finish(mc,true);}}
            default->throw new IllegalStateException("Unknown client step");
        }}catch(Exception e){failure=e.toString();finish(mc,false);}
    }
}

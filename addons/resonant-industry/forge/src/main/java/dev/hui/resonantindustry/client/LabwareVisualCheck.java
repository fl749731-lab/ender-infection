package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated throwaway client world; all player actions go through native packets. */
@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT)
public final class LabwareVisualCheck {
    private static final String WORLD="Labware-"+System.currentTimeMillis();private static final BlockPos POS=new BlockPos(0,64,0);
    private static final BlockPos HEATER = POS.west(), STIRRER = POS.east();
    private static final BlockPos FILTER = POS.east(3), CONDENSER = HEATER.west();
    private static BlockPos target = POS;
    private static final List<String> SHOTS=new ArrayList<>();private static boolean launched,built,finished;private static volatile boolean busy;private static volatile String failure;
    private static int step,age;private static long began;
    private static boolean enabled(){return Boolean.getBoolean("resonant_industry.labwareVisualCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
        try{var level=p.serverLevel();level.getDataStorage().computeIfAbsent(dev.hui.enderinfection.entity.WorldIntroduction.Data::load,()->dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(200),"ender_infection_world_introduction").complete();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);level.setDayTime(6000);
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(x,63,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);level.setBlock(POS,IndustryContent.block("lab_bench").defaultBlockState(),3);
            p.setGameMode(GameType.ADVENTURE);p.setNoGravity(true);p.getAbilities().invulnerable=true;p.getInventory().clearContent();String[] names={"beaker","pipette","dropper_bottle","test_tube","reagent_bottle","beaker"};for(int i=0;i<6;i++)p.getInventory().setItem(i,new ItemStack(IndustryContent.item(names[i])));p.getInventory().setItem(6,new ItemStack(Items.WATER_BUCKET));p.teleportTo(level,.5,64,-2.0,0,25);built=true;
        }catch(Exception e){failure=e.toString();}
    }
    private static LabBenchEntity bench(Minecraft mc){return (LabBenchEntity)mc.level.getBlockEntity(target);}
    private static int amount(Minecraft mc,int slot){return LabPlatform.data(bench(mc).getItem(slot)).getInt("LabAmount");}
    private static int held(Minecraft mc){return LabPlatform.data(mc.player.getMainHandItem()).getInt("LabAmount");}
    private static void next(){step++;age=0;}
    private static void use(Minecraft mc,int slot){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(bench(mc).worldCenter(slot),Direction.UP,target,false));next();}
    private static void select(Minecraft mc,int slot){mc.player.getInventory().selected=slot;}
    private static void shift(Minecraft mc,boolean value){mc.options.keyShift.setDown(value);mc.player.setShiftKeyDown(value);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,value?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void button(Minecraft mc,int id){mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,id);}
    private static void call(Minecraft mc,Consumer<ServerPlayer> action){busy=true;var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Exception|AssertionError e){failure=e.toString();}finally{busy=false;}});}
    private static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static ItemStack filledVessel(ServerPlayer player, String vessel, String id, int amount) {
        var bottle = new ItemStack(IndustryContent.item(vessel));
        var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(id));
        LabFluids.write(bottle, new net.minecraftforge.fluids.FluidStack(fluid, amount), player.serverLevel());
        return bottle;
    }

    private static void prepareChemistry(ServerPlayer player) {
        var level = player.serverLevel();
        level.setBlock(HEATER, IndustryContent.block("hot_plate").defaultBlockState(), 3);
        level.setBlock(STIRRER, IndustryContent.block("magnetic_stirrer").defaultBlockState(), 3);
        var stirrer = (LabBenchEntity) level.getBlockEntity(STIRRER);
        stirrer.setItem(2, filledVessel(player, "reagent_bottle", "acid_extract", 250));
        stirrer.setItem(3, filledVessel(player, "reagent_bottle", "alkaline_extract", 250));
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(IndustryContent.item("test_tube")));
        player.getInventory().setItem(1, new ItemStack(IndustryContent.item("dropper_bottle")));
        player.getInventory().setItem(2, new ItemStack(Items.COAL));
    }

    private static void prepareSeparation(ServerPlayer player) {
        var level = player.serverLevel();
        level.setBlock(STIRRER.east(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(FILTER, IndustryContent.block("filter_funnel").defaultBlockState(), 3);
        level.setBlock(CONDENSER, IndustryContent.block("condenser").defaultBlockState(), 3);
        var heater = (LabBenchEntity) level.getBlockEntity(HEATER);
        heater.setItem(0, ItemStack.EMPTY);
        heater.chemistry().clearContent();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, filledVessel(player, "test_tube", "calcium_suspension", 50));
        player.getInventory().setItem(1, new ItemStack(IndustryContent.item("test_tube")));
        player.getInventory().setItem(2, new ItemStack(IndustryContent.item("filter_paper")));
        player.getInventory().setItem(3, new ItemStack(Items.COAL));
        player.getInventory().setItem(5, new ItemStack(IndustryContent.item("test_tube")));
    }

    private static LabBenchMenu stationMenu(Minecraft mc) {
        return mc.player.containerMenu instanceof LabBenchMenu menu && menu.station() ? menu : null;
    }

    private static boolean sample(Minecraft mc, int slot, String id, int volume) {
        return sample(bench(mc).getItem(slot), id, volume);
    }

    private static boolean sample(ItemStack stack, String id, int volume) {
        var data = LabPlatform.data(stack);
        return data.getInt("LabAmount") == volume && data.getString("LabFluid").equals(IndustryContent.id(id).toString());
    }

    private static boolean status(Minecraft mc, ChemistryStation.Status status) {
        var menu = stationMenu(mc);
        return menu != null && menu.chemistry(1) == status.ordinal();
    }

    private static void verifyChemistry(ServerPlayer player) {
        var level = player.serverLevel();
        var heater = (LabBenchEntity) level.getBlockEntity(HEATER);
        var stirrer = (LabBenchEntity) level.getBlockEntity(STIRRER);
        int liquid = 0;
        for (int slot = 0; slot < 6; slot++) {
            liquid += LabFluids.read(heater.getItem(slot), level).getAmount();
            liquid += LabFluids.read(stirrer.getItem(slot), level).getAmount();
        }
        require(liquid == 450, "Expected 500 mB stock minus 50 mB evaporation, got " + liquid);
        var menu = (LabBenchMenu) heater.createMenu(1, player.getInventory(), player);
        var crystals = menu.getSlot(44).getItem();
        require(crystals.is(IndustryContent.item("neutral_salt")) && crystals.getCount() == 1 && menu.chemistry(8) == 50,
                "Wrong solid or evaporation outcome after native interactions");
        System.out.println("CHEMISTRY_NATIVE_INTERACTIONS_PASS input=500 remaining=450 evaporated=50 crystals=1");
    }

    private static void verifySeparation(ServerPlayer player) {
        var level = player.serverLevel();
        var heater = (LabBenchEntity) level.getBlockEntity(HEATER);
        var filter = (LabBenchEntity) level.getBlockEntity(FILTER);
        var receiver = (LabBenchEntity) level.getBlockEntity(CONDENSER);
        require(sample(heater.getItem(0), "filtrate_concentrate", 10)
                && sample(player.getMainHandItem(), "distilled_water", 40) && receiver.getItem(0).isEmpty(),
                "Distillation did not conserve 50 mB through recovery");
        require(filter.chemistry().getItem(ChemistryStation.INGREDIENT).isEmpty()
                && filter.chemistry().getItem(ChemistryStation.PRODUCT).is(Items.CALCITE)
                && filter.chemistry().getItem(ChemistryStation.PRODUCT).getCount() == 1,
                "Native filtering did not consume paper or retain its precipitate");
        System.out.println("SEPARATION_NATIVE_INTERACTIONS_PASS suspension=50 filtrate=50 distilled=40 concentrate=10 calcite=1");
    }
    private static void shot(Minecraft mc,String name)throws Exception{var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(name+".png"));}SHOTS.add(name);System.out.println("LABWARE_VISUAL_CAPTURE "+name);}
    private static void finish(Minecraft mc,boolean pass){finished=true;String text=(pass?"LABWARE_VISUAL_PASS":"LABWARE_VISUAL_FAIL")+" step="+step+" shots="+SHOTS.size()+" "+failure;System.out.println(text);try{Files.writeString(mc.gameDirectory.toPath().resolve("labware-visual-result.txt"),text+"\n"+String.join("\n",SHOTS));}catch(Exception e){throw new RuntimeException(e);}mc.stop();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(began==0)began=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){launched=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
        if(failure!=null||System.currentTimeMillis()-began>360000){if(failure==null)failure="timeout";finish(mc,false);return;}if(!built||mc.player==null||mc.level==null||mc.getOverlay()!=null||busy)return;
        if (step >= 57) mc.player.setPos(.5, 64.5, -4.3);
        else mc.player.setPos(.5, 64, -2.0);
        mc.player.setYRot(0);mc.player.setXRot(25);mc.player.setDeltaMovement(Vec3.ZERO);if(++age<18||bench(mc)==null)return;
        if(age>400){failure="step timeout "+step+" held="+held(mc)+" bench="+java.util.stream.IntStream.range(0,6).map(i->amount(mc,i)).boxed().toList();finish(mc,false);return;}
        try{switch(step){
            case 0->{if(age>60){shot(mc,"lab_empty");select(mc,0);use(mc,0);}}
            case 1->{if(!bench(mc).getItem(0).isEmpty()){select(mc,6);use(mc,0);}}
            case 2->{if(amount(mc,0)==1000&&mc.player.getMainHandItem().is(Items.BUCKET)){shot(mc,"lab_water");select(mc,1);use(mc,0);}}
            case 3->{if(held(mc)==25&&amount(mc,0)==975){shot(mc,"lab_pipette_sample");select(mc,2);use(mc,1);}}
            case 4->{if(!bench(mc).getItem(1).isEmpty()){select(mc,3);use(mc,2);}}
            case 5->{if(!bench(mc).getItem(2).isEmpty()){select(mc,4);use(mc,3);}}
            case 6->{if(!bench(mc).getItem(3).isEmpty()){select(mc,5);use(mc,4);}}
            case 7->{if(!bench(mc).getItem(4).isEmpty()){select(mc,1);use(mc,1);}}
            case 8->{if(amount(mc,1)==5&&held(mc)==20){shift(mc,true);next();}}
            case 9->use(mc,0);
            case 10->{if(held(mc)==25&&amount(mc,0)==970){shift(mc,false);next();}}
            case 11->use(mc,5);
            case 12->{if(!bench(mc).getItem(5).isEmpty()){mc.options.hideGui=true;shot(mc,"lab_six_vessels");mc.options.hideGui=false;select(mc,7);use(mc,0);}}
            case 13->{if(mc.player.containerMenu instanceof LabBenchMenu&&mc.screen instanceof LabBenchScreen){button(mc,5);button(mc,8);button(mc,13);next();}}
            case 14->{if(mc.player.containerMenu instanceof LabBenchMenu m&&m.value(3)==25&&amount(mc,2)==25&&amount(mc,5)==0){shot(mc,"lab_transfer");button(mc,13);next();}}
            case 15->{if(mc.player.containerMenu instanceof LabBenchMenu m&&m.value(3)==-1){shot(mc,"lab_blocked");button(mc,0);button(mc,7);for(int i=0;i<6;i++)button(mc,12);next();}}
            case 16->{if(mc.player.containerMenu instanceof LabBenchMenu m&&m.value(2)==1){button(mc,13);next();}}
            case 17->{if(amount(mc,0)==965&&amount(mc,1)==10){shot(mc,"lab_five_mb");mc.player.closeContainer();shift(mc,true);next();}}
            case 18->use(mc,0);
            case 19->{if(held(mc)==965&&bench(mc).getItem(0).isEmpty()){shift(mc,false);shot(mc,"lab_retrieve");call(mc,p->{var b=(LabBenchEntity)p.serverLevel().getBlockEntity(POS);int sum=LabFluids.read(p.getMainHandItem(),p.serverLevel()).getAmount();for(int i=0;i<6;i++)sum+=LabFluids.read(b.getItem(i),p.serverLevel()).getAmount();require(sum==1000,"Client actions changed total volume: "+sum);require(LabFluids.read(b.getItem(1),p.serverLevel()).getAmount()==10&&LabFluids.read(b.getItem(2),p.serverLevel()).getAmount()==25,"Native quantitative transfer mismatch");System.out.println("LABWARE_NATIVE_INTERACTIONS_PASS volume=1000 pipette_sample=25 pipette_dose=5 bench_dose=5");});next();}}
            case 20->{mc.setScreen(new IndustryManualScreen());for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(">")){for(int i=0;i<7;i++)b.onPress();break;}next();}
            case 21 -> {
                shot(mc, "lab_handbook");
                require(SHOTS.size() == 9, "Expected nine labware screenshots");
                mc.setScreen(null);
                target = STIRRER;
                call(mc, LabwareVisualCheck::prepareChemistry);
                next();
            }
            case 22 -> { select(mc, 0); use(mc, 0); }
            case 23 -> {
                if (bench(mc).getItem(0).is(IndustryContent.item("test_tube"))) { select(mc, 1); use(mc, 1); }
            }
            case 24 -> {
                if (bench(mc).getItem(1).is(IndustryContent.item("dropper_bottle"))) { select(mc, 3); use(mc, 0); }
            }
            case 25 -> {
                if (stationMenu(mc) != null) {
                    button(mc, 2);
                    button(mc, 6);
                    button(mc, 13);
                    button(mc, 3);
                    button(mc, 7);
                    button(mc, 13);
                    next();
                }
            }
            case 26 -> {
                if (sample(mc, 0, "acid_extract", 25) && sample(mc, 1, "alkaline_extract", 25)) {
                    shot(mc, "chemistry_measured");
                    button(mc, 14);
                    next();
                }
            }
            case 27 -> {
                if (status(mc, ChemistryStation.Status.SIGNAL)) {
                    require(stationMenu(mc).chemistry(3) == 0, "Unpowered GUI experiment advanced");
                    shot(mc, "chemistry_waiting_signal");
                    call(mc, p -> p.serverLevel().setBlock(STIRRER.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
                    next();
                }
            }
            case 28 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.chemistry(3) > 24 && status(mc, ChemistryStation.Status.RUNNING)) {
                    shot(mc, "chemistry_stirring");
                    next();
                }
            }
            case 29 -> {
                if (status(mc, ChemistryStation.Status.COMPLETE) && sample(mc, 0, "neutral_solution", 50) && amount(mc, 1) == 0) {
                    shot(mc, "chemistry_neutralized");
                    mc.player.closeContainer();
                    shift(mc, true);
                    next();
                }
            }
            case 30 -> use(mc, 0);
            case 31 -> {
                if (held(mc) == 50 && bench(mc).getItem(0).isEmpty()) {
                    shift(mc, false);
                    target = HEATER;
                    use(mc, 0);
                }
            }
            case 32 -> { if (held(mc) == 0 && sample(mc, 0, "neutral_solution", 50)) use(mc, 0); }
            case 33 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.heater()) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, 35, 0, ClickType.QUICK_MOVE, mc.player);
                    next();
                }
            }
            case 34 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.getSlot(43).getItem().is(Items.COAL)) { button(mc, 14); next(); }
            }
            case 35 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.chemistry(3) > 20 && status(mc, ChemistryStation.Status.RUNNING)) {
                    require(menu.chemistry(2) >= 100, "Crystallization advanced below its temperature threshold");
                    shot(mc, "chemistry_crystallizing");
                    next();
                }
            }
            case 36 -> {
                var menu = stationMenu(mc);
                if (status(mc, ChemistryStation.Status.COMPLETE) && menu.getSlot(44).hasItem() && amount(mc, 0) == 0) {
                    shot(mc, "chemistry_crystals");
                    mc.player.closeContainer();
                    mc.options.hideGui = true;
                    next();
                }
            }
            case 37 -> {
                shot(mc, "chemistry_stations");
                call(mc, LabwareVisualCheck::verifyChemistry);
                mc.options.hideGui = false;
                next();
            }
            case 38 -> {
                mc.setScreen(new IndustryManualScreen());
                for (var child : mc.screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(">")) {
                        for (int i = 0; i < 9; i++) b.onPress();
                        break;
                    }
                }
                next();
            }
            case 39 -> {
                shot(mc, "chemistry_handbook");
                require(SHOTS.size() == 17, "Expected 17 labware and chemistry screenshots");
                mc.setScreen(null);
                target = FILTER;
                call(mc, LabwareVisualCheck::prepareSeparation);
                next();
            }
            case 40 -> { select(mc, 0); use(mc, 0); }
            case 41 -> {
                if (sample(mc, 0, "calcium_suspension", 50)) { select(mc, 1); use(mc, 1); }
            }
            case 42 -> {
                if (bench(mc).getItem(1).is(IndustryContent.item("test_tube"))) { select(mc, 4); use(mc, 0); }
            }
            case 43 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.kind() == Chemistry.Station.FILTER) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, 35, 0, ClickType.QUICK_MOVE, mc.player);
                    next();
                }
            }
            case 44 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.getSlot(42).getItem().is(IndustryContent.item("filter_paper"))) { button(mc, 14); next(); }
            }
            case 45 -> {
                if (status(mc, ChemistryStation.Status.COMPLETE) && sample(mc, 1, "clear_filtrate", 50)
                        && stationMenu(mc).getSlot(44).getItem().is(Items.CALCITE)) {
                    shot(mc, "separation_filtered");
                    mc.player.closeContainer();
                    shift(mc, true);
                    next();
                }
            }
            case 46 -> use(mc, 1);
            case 47 -> {
                if (sample(mc.player.getMainHandItem(), "clear_filtrate", 50)) {
                    shift(mc, false);
                    target = HEATER;
                    use(mc, 0);
                }
            }
            case 48 -> { if (sample(mc, 0, "clear_filtrate", 50) && mc.player.getMainHandItem().isEmpty()) use(mc, 0); }
            case 49 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.heater()) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, 36, 0, ClickType.QUICK_MOVE, mc.player);
                    next();
                }
            }
            case 50 -> {
                var menu = stationMenu(mc);
                if (menu != null && menu.getSlot(43).getItem().is(Items.COAL)) { button(mc, 16); next(); }
            }
            case 51 -> {
                if (status(mc, ChemistryStation.Status.RECEIVER)) {
                    require(stationMenu(mc).chemistry(3) == 0 && sample(mc, 0, "clear_filtrate", 50), "Missing receiver changed the still charge");
                    shot(mc, "separation_waiting_receiver");
                    mc.player.closeContainer();
                    target = CONDENSER;
                    select(mc, 5);
                    use(mc, 0);
                }
            }
            case 52 -> {
                if (bench(mc).getItem(0).is(IndustryContent.item("test_tube"))) {
                    target = HEATER;
                    select(mc, 4);
                    use(mc, 0);
                }
            }
            case 53 -> {
                if (status(mc, ChemistryStation.Status.RUNNING) && stationMenu(mc).chemistry(3) > 20) {
                    shot(mc, "separation_distilling");
                    next();
                }
            }
            case 54 -> {
                if (status(mc, ChemistryStation.Status.COMPLETE) && sample(mc, 0, "filtrate_concentrate", 10)
                        && stationMenu(mc).chemistry(9) == 40) {
                    shot(mc, "separation_recovered");
                    mc.player.closeContainer();
                    target = CONDENSER;
                    shift(mc, true);
                    next();
                }
            }
            case 55 -> { if (sample(mc, 0, "distilled_water", 40)) use(mc, 0); }
            case 56 -> {
                if (sample(mc.player.getMainHandItem(), "distilled_water", 40) && bench(mc).getItem(0).isEmpty()) {
                    shift(mc, false);
                    mc.options.hideGui = true;
                    call(mc, LabwareVisualCheck::verifySeparation);
                    next();
                }
            }
            case 57 -> {
                shot(mc, "separation_stations");
                mc.options.hideGui = false;
                next();
            }
            case 58 -> {
                mc.setScreen(new IndustryManualScreen());
                for (var child : mc.screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(">")) {
                        for (int i = 0; i < 11; i++) b.onPress();
                        break;
                    }
                }
                next();
            }
            case 59 -> {
                shot(mc, "separation_handbook");
                require(SHOTS.size() == 23, "Expected 23 laboratory screenshots");
                finish(mc, true);
            }
        }}catch(Exception|AssertionError e){failure=e.toString();finish(mc,false);}
    }
}

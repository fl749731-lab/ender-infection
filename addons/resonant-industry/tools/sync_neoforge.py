"""Forge is the addon source of truth; adapt exact 1.21.1 API boundaries into build/."""
from pathlib import Path
import json, sys

BASE=Path(__file__).resolve().parents[1]
ROOT=BASE.parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from sync_neoforge_resources import target,transform,write_if_changed

def adapt(s,name):
    if name in ('IndustryManualScreen.java','LabBenchScreen.java'):s=s.replace('renderBackground(g);','renderBackground(g,mx,my,partial);')
    if name=='IndustryManualScreen.java':
        s=s.replace('"filtration","distillation"}','"filtration","distillation","plate_press","metering","minerals"}')
    s=s.replace('new ResourceLocation(id.contains(":")?id:IndustryApi.MOD_ID+":"+id)','ResourceLocation.parse(id.contains(":")?id:IndustryApi.MOD_ID+":"+id)')
    if name=='LabVesselItem.java':
        start=s.index('    // FORGE_CAP_START');end=s.index('    // FORGE_CAP_END')+len('    // FORGE_CAP_END');s=s[:start]+s[end:]
        s=s.replace('ItemStack stack,Level level,List<Component> lines','ItemStack stack,Item.TooltipContext context,List<Component> lines')
    if name=='LabBenchEntity.java':
        start=s.index('    // FORGE_CAP_START');end=s.index('    // FORGE_CAP_END')+len('    // FORGE_CAP_END')
        s=s[:start]+s[end:]
        s=s.replace('saveAdditional(CompoundTag t){super.saveAdditional(t);','saveAdditional(CompoundTag t,net.minecraft.core.HolderLookup.Provider registries){super.saveAdditional(t,registries);').replace('ContainerHelper.saveAllItems(t,items);','ContainerHelper.saveAllItems(t,items,registries);')
        s=s.replace('public void load(CompoundTag t){super.load(t);','protected void loadAdditional(CompoundTag t,net.minecraft.core.HolderLookup.Provider registries){super.loadAdditional(t,registries);').replace('ContainerHelper.loadAllItems(t,items);','ContainerHelper.loadAllItems(t,items,registries);')
        s=s.replace('ContainerHelper.saveAllItems(reaction, chemistry.items);','ContainerHelper.saveAllItems(reaction, chemistry.items,registries);').replace('ContainerHelper.loadAllItems(reaction, chemistry.items);','ContainerHelper.loadAllItems(reaction, chemistry.items,registries);')
        s=s.replace('public CompoundTag getUpdateTag(){return saveWithoutMetadata();}','public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries){return saveWithoutMetadata(registries);}')
    if name=='LabBenchBlock.java':
        s=s.replace('public final class LabBenchBlock extends BaseEntityBlock {','''public final class LabBenchBlock extends BaseEntityBlock {
    @Override protected com.mojang.serialization.MapCodec<LabBenchBlock> codec(){return com.mojang.serialization.Codec.STRING.fieldOf("station").xmap(name->new LabBenchBlock(Chemistry.Station.valueOf(name)),block->block.station().name());}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return use(s,l,p,player,hand,hit)==InteractionResult.PASS?net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:net.minecraft.world.ItemInteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){return use(s,l,p,player,InteractionHand.MAIN_HAND,hit);}
''').replace('@Override public InteractionResult use(','public InteractionResult use(')
    if name=='IndustryBlock.java':
        s=s.replace('public final class IndustryBlock extends BaseEntityBlock {','''public final class IndustryBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<IndustryBlock> CODEC=com.mojang.serialization.Codec.STRING.fieldOf("role").xmap(IndustryBlock::new,b->b.role);
    @Override protected com.mojang.serialization.MapCodec<IndustryBlock> codec(){return CODEC;}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        var result=use(s,l,p,player,hand,hit);
        return switch(result){case SUCCESS->net.minecraft.world.ItemInteractionResult.SUCCESS;case CONSUME,CONSUME_PARTIAL->net.minecraft.world.ItemInteractionResult.CONSUME;case FAIL->net.minecraft.world.ItemInteractionResult.FAIL;default->net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;};
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){return use(s,l,p,player,InteractionHand.MAIN_HAND,hit);}
''').replace('@Override public InteractionResult use(','public InteractionResult use(')
    if name=='IndustryNode.java':
        s=s.replace('!stack.hasTag()','stack.getComponentsPatch().isEmpty()')
        s=s.replace('saveAdditional(CompoundTag t){super.saveAdditional(t);state(t);ContainerHelper.saveAllItems(t,items);}', 'saveAdditional(CompoundTag t,net.minecraft.core.HolderLookup.Provider registries){super.saveAdditional(t,registries);state(t);ContainerHelper.saveAllItems(t,items,registries);}')
        s=s.replace('public void load(CompoundTag t){\n        super.load(t);','protected void loadAdditional(CompoundTag t,net.minecraft.core.HolderLookup.Provider registries){\n        super.loadAdditional(t,registries);')
        s=s.replace('ContainerHelper.loadAllItems(t,items);','ContainerHelper.loadAllItems(t,items,registries);')
        s=s.replace('public CompoundTag getUpdateTag(){','public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries){')
    if name in ('IndustryGameTests.java','IndustryPersistenceCheck.java','LabwareGameTests.java','LabwarePersistenceCheck.java','ChemistryGameTests.java'):
        s=s.replace('n.saveWithoutMetadata()','n.saveWithoutMetadata(n.getLevel().registryAccess())').replace('n.load(tag)','n.loadWithComponents(tag,n.getLevel().registryAccess())')
    if name in ('IndustryPersistenceCheck.java','LabwarePersistenceCheck.java'):
        s=s.replace('net.minecraftforge.event.TickEvent','net.neoforged.neoforge.event.tick.ServerTickEvent').replace('TickEvent.ServerTickEvent event','ServerTickEvent.Post event').replace('||event.phase!=TickEvent.Phase.END','')
    if name in ('LabwareGameTests.java','ChemistryGameTests.java','SeparationGameTests.java'):s=s.replace('h.makeMockSurvivalPlayer()', 'h.makeMockPlayer(GameType.SURVIVAL)').replace('h.makeMockPlayer()', 'h.makeMockPlayer(GameType.CREATIVE)').replace('.orElseThrow().getResultItem(','.orElseThrow().value().getResultItem(').replace('sample.setTag(marker);','sample.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(marker));').replace('chemical.setTag(tag);','chemical.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));')
    s=s.replace('net.minecraftforge.gametest','net.neoforged.neoforge.gametest')
    s=s.replace('net.minecraftforge.api.distmarker','net.neoforged.api.distmarker').replace('net.minecraftforge.client','net.neoforged.neoforge.client')
    s=s.replace('net.minecraftforge.eventbus.api','net.neoforged.bus.api').replace('net.minecraftforge.event.','net.neoforged.neoforge.event.').replace('net.minecraftforge.fml','net.neoforged.fml')
    s=s.replace('net.minecraftforge.energy','net.neoforged.neoforge.energy').replace('net.minecraftforge.fluids','net.neoforged.neoforge.fluids').replace('net.minecraftforge.items','net.neoforged.neoforge.items')
    s=s.replace('import net.neoforged.fml.common.Mod;','import net.neoforged.fml.common.Mod;\nimport net.neoforged.fml.common.EventBusSubscriber;')
    s=s.replace('@Mod.EventBusSubscriber','@EventBusSubscriber').replace('Mod.EventBusSubscriber.Bus.','EventBusSubscriber.Bus.')
    if name in ('IndustryVisualCheck.java','LabwareVisualCheck.java'):
        s=s.replace('import net.neoforged.neoforge.event.TickEvent;','import net.neoforged.neoforge.client.event.ClientTickEvent;')
        s=s.replace('TickEvent.ClientTickEvent event','ClientTickEvent.Post event').replace('||event.phase!=TickEvent.Phase.END','')
        s=s.replace('level.getDataStorage().computeIfAbsent(dev.hui.enderinfection.entity.WorldIntroduction.Data::load,()->dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(200),"ender_infection_world_introduction")','level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(()->dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(200),(tag,registries)->dev.hui.enderinfection.entity.WorldIntroduction.Data.load(tag),null),"ender_infection_world_introduction")')
        s=s.replace('.value().createWorldDimensions());return;','.value().createWorldDimensions(),null);return;')
    if name=='IndustryClient.java':
        s=s.replace(',bus=EventBusSubscriber.Bus.MOD','')
        s=s.replace('MenuScreens.register(ResonantIndustry.menuType(),IndustryScreen::new);','').replace('MenuScreens.register(ResonantIndustry.benchMenuType(),LabBenchScreen::new);','').replace('MenuScreens.register(ResonantIndustry.stationMenuType(),LabBenchScreen::new);','')
        s=s.replace('public final class IndustryClient {','''public final class IndustryClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(ResonantIndustry.menuType(),IndustryScreen::new);e.register(ResonantIndustry.benchMenuType(),LabBenchScreen::new);e.register(ResonantIndustry.stationMenuType(),LabBenchScreen::new);}
    @SubscribeEvent public static void extensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e){
        e.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions(){
            public net.minecraft.resources.ResourceLocation getStillTexture(){return IndustryContent.id("minecraft:block/water_still");}
            public net.minecraft.resources.ResourceLocation getFlowingTexture(){return IndustryContent.id("minecraft:block/water_flow");}
            public int getTintColor(){return 0xffa96acf;}
        },ResonantIndustry.slurryType());
        for (var liquid : Chemistry.Liquid.values()) {
            e.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                public net.minecraft.resources.ResourceLocation getStillTexture() { return IndustryContent.id("minecraft:block/water_still"); }
                public net.minecraft.resources.ResourceLocation getFlowingTexture() { return IndustryContent.id("minecraft:block/water_flow"); }
                public int getTintColor() { return liquid.tint; }
            }, ResonantIndustry.chemicalType(liquid));
        }
    }
''')
    if name=='ResonantIndustry.java':
        s=s.replace('net.minecraftforge.registries.*','net.neoforged.neoforge.registries.*').replace('ForgeFlowingFluid','BaseFlowingFluid')
        for a,b in {'ForgeRegistries.BLOCKS':'Registries.BLOCK','ForgeRegistries.ITEMS':'Registries.ITEM','ForgeRegistries.BLOCK_ENTITY_TYPES':'Registries.BLOCK_ENTITY_TYPE','ForgeRegistries.MENU_TYPES':'Registries.MENU','ForgeRegistries.FLUIDS':'Registries.FLUID','ForgeRegistries.Keys.FLUID_TYPES':'NeoForgeRegistries.Keys.FLUID_TYPES'}.items():s=s.replace(a,b)
        s=s.replace('import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;','')
        s=s.replace('public ResonantIndustry(){','public ResonantIndustry(net.neoforged.bus.api.IEventBus bus){').replace('IndustryApi.bootstrap();var bus=FMLJavaModLoadingContext.get().getModEventBus();','IndustryApi.bootstrap();LabPlatform.register(bus);WorkshopContent.register(bus);')
        s=s.replace('bus.addListener((BuildCreativeModeTabContentsEvent','bus.addListener(Platform::registerCapabilities);\n        bus.addListener((BuildCreativeModeTabContentsEvent')
        start=s.index('        @Override public void initializeClient(')
        brace=s.index('{',start);depth=1;end=brace+1
        while depth:
            if s[end]=='{':depth+=1
            if s[end]=='}':depth-=1
            end+=1
        s=s[:start]+s[end:]
    return s

def main():
    base=BASE/'neoforge/build/generated';written=set()
    for folder,sub in [('src/main/java','sharedJava'),('src/main/resources','sharedResources')]:
        for p in (BASE/'forge'/folder).rglob('*'):
            if not p.is_file():continue
            rel=p.relative_to(BASE/'forge'/folder)
            if folder.endswith('java') and p.name in ('Platform.java','PlatformNodeEntity.java','LabPlatform.java'):continue
            if folder.endswith('resources') and (str(rel).startswith('META-INF') or rel.as_posix()=='pack.mcmeta'):continue
            if folder.endswith('resources') and rel.parts[0]=='neoforge_only':rel=Path(*rel.parts[1:])
            dest=base/sub/(target(rel) if sub=='sharedResources' else rel)
            if p.suffix=='.java':content=adapt(p.read_text('utf-8'),p.name).encode('utf-8')
            elif p.suffix=='.json':content=(json.dumps(transform(json.loads(p.read_text('utf-8')),len(rel.parts)>2 and rel.parts[2]=='recipes'),ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')
            else:content=p.read_bytes()
            write_if_changed(dest,content);written.add(dest.resolve())
    for sub in ['sharedJava','sharedResources']:
        for p in (base/sub).rglob('*'):
            if p.is_file() and p.resolve() not in written:
                assert p.resolve().is_relative_to(base.resolve());p.unlink()
    print(f'Industry shared-source sync: {len(written)} files')

if __name__=='__main__':main()

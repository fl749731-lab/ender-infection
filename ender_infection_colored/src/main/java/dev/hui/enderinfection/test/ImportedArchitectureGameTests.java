package dev.hui.enderinfection.test;
import dev.hui.enderinfection.worldgen.ImportedBuildings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.*;

@GameTestHolder("ender_lab_checks")
@PrefixGameTestTemplate(false)
public final class ImportedArchitectureGameTests {
 @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=60,batch="imported_palette")
 public static void importedBuildingsUseRegisteredBlocksAndValidStateProperties(GameTestHelper h){
  var level=h.getLevel();var entries=ImportedBuildings.catalog(level);h.assertTrue(entries.size()>=4,"Imported catalog did not load");
  for(var spec:entries){
   var resource=level.getServer().getResourceManager().getResource(new ResourceLocation("ender_infection","structures/imported/"+spec.key()+".nbt")).orElseThrow();
   try(var in=resource.open()){
    var tag=NbtIo.readCompressed(in);for(var entry:tag.getList("palette",10)){
     var n=(CompoundTag)entry;var key=new ResourceLocation(n.getString("Name"));var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(key).orElseThrow(()->new AssertionError("Unknown imported block "+key));
     var props=n.getCompound("Properties");for(var name:props.getAllKeys()){
      var property=block.getStateDefinition().getProperty(name);h.assertTrue(property!=null&&property.getValue(props.getString(name)).isPresent(),"Invalid imported property "+key+" / "+name+"="+props.getString(name));
     }
    }
   }catch(java.io.IOException e){throw new RuntimeException(e);}
   var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(new ResourceLocation("ender_infection","imported/"+spec.key()));
   // This assertion promises flat dry terrain. Do not inherit the host world's noise terrain:
   // adding unrelated tests changes the chunk coordinates and can otherwise turn this into ocean.
   var access=level.registryAccess();
   var flat=new net.minecraft.world.level.levelgen.FlatLevelSource(net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.getDefault(access.registryOrThrow(net.minecraft.core.registries.Registries.BIOME).asLookup(),access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).asLookup(),access.registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE).asLookup()));
   var generation=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(access,flat,flat.getBiomeSource(),level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),new net.minecraft.world.level.ChunkPos(h.absolutePos(BlockPos.ZERO)),level,b->true);
   var point=structure.findValidGenerationPoint(generation);h.assertTrue(point.isPresent()&&!point.get().getPiecesBuilder().isEmpty(),"Imported structure produces no pieces on flat dry terrain: "+spec.key());
   var template=ImportedBuildings.template(level,spec);h.assertTrue(template.getSize().getX()==spec.width()&&template.getSize().getY()==spec.height()&&template.getSize().getZ()==spec.length(),"Import size mismatch "+spec.key());
  }h.succeed();
 }
 @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="imported_resume")
 public static void queuedBuildingSurvivesSerializationAndPlacesOnlyItsCurrentChunk(GameTestHelper h){
  var level=h.getLevel();var spec=ImportedBuildings.catalog(level).stream().filter(s->s.key().equals("hbm_factory")).findFirst().orElseThrow();var base=h.absolutePos(new BlockPos(3,1,3));
  var jobs=ImportedBuildings.jobs(level);jobs.cancel();h.assertTrue(ImportedBuildings.queue(level,spec,base,java.util.UUID.randomUUID()),"Could not queue factory");
  ImportedBuildings.step(level);h.assertTrue(jobs.active(),"Whole building was placed synchronously");var saved=jobs.save(new CompoundTag());jobs.cancel();var restored=ImportedBuildings.Jobs.load(saved);h.assertTrue(restored.active()&&restored.status().contains("1 个区块"),"Lost in-progress chunk cursor");
  int guard=0;while(restored.active()&&guard++<32)restored.process(level);h.assertTrue(!restored.active(),"Restored job failed to complete");
  var raw=new CompoundTag();ImportedBuildings.template(level,spec).save(raw);var palette=raw.getList("palette",10);int checked=0;
  for(var entry:raw.getList("blocks",10)){
   var b=(CompoundTag)entry;String name=palette.getCompound(b.getInt("state")).getString("Name");if(name.equals("minecraft:air"))continue;var p=b.getList("pos",3);var expected=new ResourceLocation(name);var actual=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(base.offset(p.getInt(0),p.getInt(1),p.getInt(2))).getBlock());
   h.assertTrue(actual.equals(expected),"Chunk clipping skipped or damaged a structure block at "+p+": "+actual+" != "+expected);checked++;
  }
  h.assertTrue(checked>5000,"Factory lost most of its architecture");h.succeed();
 }
}

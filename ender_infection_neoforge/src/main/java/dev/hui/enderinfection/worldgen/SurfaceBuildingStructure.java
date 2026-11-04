package dev.hui.enderinfection.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.neoforged.neoforge.registries.*;

/** A native jigsaw start with terrain-only eligibility; never reads or requests world chunks. */
public final class SurfaceBuildingStructure extends Structure {
 public static final DeferredRegister<StructureType<?>> TYPES=DeferredRegister.create(Registries.STRUCTURE_TYPE,"ender_infection");
 public static final DeferredHolder<StructureType<?>,StructureType<SurfaceBuildingStructure>> TYPE=TYPES.register("surface_building",()->()->SurfaceBuildingStructure.CODEC);
 public static final com.mojang.serialization.MapCodec<SurfaceBuildingStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
  settingsCodec(i),StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s->s.pool),Codec.intRange(0,32).fieldOf("ground_offset").forGetter(s->s.ground)
 ).apply(i,SurfaceBuildingStructure::new));
 private final Holder<StructureTemplatePool> pool;private final int ground;
 public SurfaceBuildingStructure(StructureSettings settings,Holder<StructureTemplatePool> pool,int ground){super(settings);this.pool=pool;this.ground=ground;}
 @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context){
  var origin=new BlockPos(context.chunkPos().getMinBlockX(),-ground,context.chunkPos().getMinBlockZ());
  var candidate=JigsawPlacement.addPieces(context,pool,Optional.empty(),1,origin,false,Optional.of(Heightmap.Types.WORLD_SURFACE_WG),116,net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup.EMPTY,net.minecraft.world.level.levelgen.structure.pools.DimensionPadding.ZERO,net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.APPLY_WATERLOGGING);
  if(candidate.isEmpty())return Optional.empty();var stub=candidate.get();var pieces=stub.getPiecesBuilder();if(pieces.isEmpty())return Optional.empty();var box=pieces.getBoundingBox();
  int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
  for(int x:new int[]{box.minX()+1,(box.minX()+box.maxX())/2,box.maxX()-1})for(int z:new int[]{box.minZ()+1,(box.minZ()+box.maxZ())/2,box.maxZ()-1}){
   int y=context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
   int surface=context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
   if(surface>y)return Optional.empty();
   low=Math.min(low,y);high=Math.max(high,y);
  }
  if(low<=context.heightAccessor().getMinBuildHeight()||high-low>5)return Optional.empty();
  // Reuse the evaluated builder so random rotations and offsets cannot change after the check.
  return Optional.of(new GenerationStub(stub.position(),Either.right(pieces)));
 }
 @Override public StructureType<?> type(){return TYPE.get();}
}

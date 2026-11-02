package dev.hui.enderinfection.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/** Structure set style spacing for features: one candidate per grid cell, so landmarks never clump. */
public class GridPlacement extends PlacementModifier {
    public static final Codec<GridPlacement> CODEC=RecordCodecBuilder.create(instance->instance.group(
        Codec.intRange(1,512).fieldOf("spacing").forGetter(p->p.spacing/16),
        Codec.intRange(0,511).fieldOf("separation").forGetter(p->p.separation/16),
        Codec.INT.fieldOf("salt").orElse(0).forGetter(p->p.salt)
    ).apply(instance,GridPlacement::new));
    private final int spacing,separation,salt;
    public GridPlacement(int spacing,int separation,int salt){this.spacing=Math.max(16,spacing*16);this.separation=Math.min(Math.max(0,spacing*16-1),separation*16);this.salt=salt;}
    @Override public Stream<BlockPos> getPositions(PlacementContext context,RandomSource random,BlockPos pos){
        int cellX=Math.floorDiv(pos.getX(),spacing),cellZ=Math.floorDiv(pos.getZ(),spacing);
        RandomSource cell=RandomSource.create(cellX*341873128712L+cellZ*132897987541L+salt+context.getLevel().getSeed());
        int span=Math.max(1,spacing-separation);
        int x=cellX*spacing+cell.nextInt(span);
        int z=cellZ*spacing+cell.nextInt(span);
        if(Math.floorDiv(x,16)!=Math.floorDiv(pos.getX(),16)||Math.floorDiv(z,16)!=Math.floorDiv(pos.getZ(),16))return Stream.empty();
        return Stream.of(new BlockPos(x,pos.getY(),z));
    }
    @Override public PlacementModifierType<?> type(){return InfectedContent.GRID_PLACEMENT.get();}
}

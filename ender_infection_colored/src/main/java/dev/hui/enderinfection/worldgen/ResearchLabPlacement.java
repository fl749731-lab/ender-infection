package dev.hui.enderinfection.worldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class ResearchLabPlacement {
    private ResearchLabPlacement(){}
    public static boolean place(ServerLevel level,BlockPos origin){
        if(origin.getY()<level.getMinBuildHeight()+1||origin.getY()+26>=level.getMaxBuildHeight())return false;
        var template=level.getStructureManager().get(new ResourceLocation("ender_infection","research_lab"));
        return template.isPresent()&&template.get().placeInWorld(level,origin,origin,new StructurePlaceSettings().setIgnoreEntities(true),level.random,2);
    }
}

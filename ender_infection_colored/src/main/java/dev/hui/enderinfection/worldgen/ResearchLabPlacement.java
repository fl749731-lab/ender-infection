package dev.hui.enderinfection.worldgen;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class ResearchLabPlacement {
    public record Spec(String key,String command,String biome,int width,int length,int floors,int rooms){
        public int depth(){return floors*5;}
        public int height(){return depth()+6;}
        public BlockPos entrance(){return new BlockPos(width/2,depth()+1,3);}
        public BlockPos lift(int floor){return new BlockPos(width/2,floor*5,5);}
    }
    public static final List<Spec> SPECS=List.of(
        new Spec("research_lab","research","mushroom_fields",40,36,2,8),
        new Spec("wetland_lab","swamp","swamp",42,34,2,6),
        new Spec("forest_lab","forest","dark_forest",42,34,3,12),
        new Spec("medical_lab","medical","plains",40,34,2,10),
        new Spec("cryo_lab","snow","snowy_taiga",32,40,2,8),
        new Spec("badlands_lab","badlands","badlands",44,36,3,12));
    private ResearchLabPlacement(){}
    public static Spec find(String command){
        String normalized=switch(command){case "hospital"->"medical";case "prison"->"badlands";default->command;};
        return SPECS.stream().filter(s->s.command.equals(normalized)).findFirst().orElse(null);
    }
    public static boolean place(ServerLevel level,BlockPos origin){return place(level,origin,SPECS.get(0));}
    public static boolean place(ServerLevel level,BlockPos origin,Spec spec){
        if(spec==null||origin.getY()<level.getMinBuildHeight()+1||origin.getY()+spec.height()>=level.getMaxBuildHeight())return false;
        var template=level.getStructureManager().get(new ResourceLocation("ender_infection",spec.key));
        return template.isPresent()&&template.get().placeInWorld(level,origin,origin,new StructurePlaceSettings().setIgnoreEntities(true),level.random,2);
    }
}

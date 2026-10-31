package dev.hui.enderinfection.biomass;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

/** Soil history belongs to a world position, never to a replaceable node or mother. */
public final class LandNutrients extends SavedData {
    public static final int CYCLES=120,INTERVAL=200;
    /** Credits needed per biomass. Halved from the 32-column first balance so a young colony
     *  pays for new terrain twice as fast without touching the ten second harvest cycle. */
    public static final int CREDITS_PER_BIOMASS=16,DENOMINATOR=CREDITS_PER_BIOMASS*CYCLES;
    private record Soil(int used,long lastHarvest){}
    private final Map<Long,Soil> soils=new HashMap<>();
    public static LandNutrients get(ServerLevel level){return level.getDataStorage().computeIfAbsent(LandNutrients::load,LandNutrients::new,"ender_infection_land_nutrients");}
    public int remaining(BlockPos pos){Soil soil=soils.get(pos.asLong());return soil==null?CYCLES:Math.max(0,CYCLES-soil.used());}
    public int harvest(BlockPos pos,int weight,long now){
        if(weight<=0)return 0;Soil previous=soils.get(pos.asLong());
        if(previous!=null&&(previous.used()>=CYCLES||now-previous.lastHarvest()<INTERVAL))return 0;
        int used=previous==null?0:previous.used();int credits=weight*(CYCLES-used);
        soils.put(pos.asLong(),new Soil(used+1,now));setDirty();return credits;
    }
    public static LandNutrients load(CompoundTag tag){
        var data=new LandNutrients();
        for(var entry:tag.getList("Soils",10)){
            CompoundTag row=(CompoundTag)entry;data.soils.put(row.getLong("Pos"),new Soil(Math.max(0,Math.min(CYCLES,row.getInt("Used"))),row.getLong("LastHarvest")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag rows=new ListTag();
        soils.forEach((pos,soil)->{CompoundTag row=new CompoundTag();row.putLong("Pos",pos);row.putInt("Used",soil.used());row.putLong("LastHarvest",soil.lastHarvest());rows.add(row);});tag.put("Soils",rows);return tag;
    }
}

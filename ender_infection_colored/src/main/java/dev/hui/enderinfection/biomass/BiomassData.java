package dev.hui.enderinfection.biomass;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared by the infected faction and persisted in the overworld's saved data. Server thread only. */
public final class BiomassData extends SavedData {
    private int biomass;
    public static BiomassData get(ServerLevel level){
        return level.getServer().overworld().getDataStorage().computeIfAbsent(BiomassData::load,BiomassData::new,"ender_infection_biomass");
    }
    public static BiomassData load(CompoundTag tag){
        BiomassData data=new BiomassData();data.biomass=Math.max(0,Math.min(100000,tag.getInt("Biomass")));return data;
    }
    public int amount(){return biomass;}
    public void add(int amount){if(amount>0){biomass=Math.min(100000,biomass+amount);setDirty();}}
    public boolean spend(int amount){
        if(amount<=0||biomass<amount)return false;biomass-=amount;setDirty();return true;
    }
    @Override public CompoundTag save(CompoundTag tag){tag.putInt("Biomass",biomass);return tag;}
}

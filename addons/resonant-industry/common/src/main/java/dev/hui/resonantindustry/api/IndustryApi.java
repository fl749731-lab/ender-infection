package dev.hui.resonantindustry.api;

import java.util.*;

/** Register machines, recipes and solid fuels during mod construction; immutable after common setup. */
public final class IndustryApi {
    public static final int API_VERSION=2;
    public static final String MOD_ID="resonant_industry", GENERATOR=MOD_ID+":generator", PROCESSOR=MOD_ID+":processor", CENTRIFUGE=MOD_ID+":centrifuge", SLURRY=MOD_ID+":nutrient_slurry";
    private static final Map<String,MachineDefinition> MACHINES=new LinkedHashMap<>();
    private static final Map<String,IndustryRecipe> RECIPES=new LinkedHashMap<>();
    private static final Map<String,ProcessRecipe> PROCESSES=new LinkedHashMap<>();
    private static final Map<String,String> FINGERPRINTS=new LinkedHashMap<>();
    private static final Map<String,Integer> FUELS=new LinkedHashMap<>();
    private static boolean frozen,bootstrapped;
    private IndustryApi(){}
    public static void checkId(String id){if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid resource ID "+id);}
    private static void mutable(){if(frozen)throw new IllegalStateException("Industry registration is closed");}
    public static synchronized void registerMachine(MachineDefinition machine){mutable();if(MACHINES.putIfAbsent(machine.id(),machine)!=null)throw new IllegalArgumentException("Duplicate machine "+machine.id());}
    public static synchronized void registerRecipe(IndustryRecipe recipe){mutable();registerRecipe(ProcessRecipe.fromLegacy(recipe));RECIPES.put(recipe.id(),recipe);}
    public static synchronized void registerRecipe(ProcessRecipe recipe){mutable();if(PROCESSES.putIfAbsent(recipe.id(),recipe)!=null)throw new IllegalArgumentException("Duplicate recipe "+recipe.id());FINGERPRINTS.put(recipe.id(),recipe.fingerprint());}
    public static synchronized void registerFuel(String item,int ticks){mutable();checkId(item);if(ticks<1||ticks>72000||FUELS.putIfAbsent(item,ticks)!=null)throw new IllegalArgumentException("Invalid/duplicate fuel");}
    public static Map<String,MachineDefinition> machines(){return Collections.unmodifiableMap(MACHINES);}
    public static Map<String,IndustryRecipe> recipes(){return Collections.unmodifiableMap(RECIPES);}
    public static Map<String,ProcessRecipe> processes(){return Collections.unmodifiableMap(PROCESSES);}
    public static String processFingerprint(String id){return FINGERPRINTS.getOrDefault(id,"");}
    public static int fuelTicks(String id){return FUELS.getOrDefault(id,0);}
    public static synchronized void freeze(){for(var r:PROCESSES.values())if(!MACHINES.containsKey(r.machine()))throw new IllegalStateException("Unknown recipe machine "+r.machine());frozen=true;}
    public static synchronized void bootstrap(){
        if(bootstrapped)return;bootstrapped=true;
        registerMachine(MachineDefinition.box(GENERATOR,3,3,true));registerMachine(MachineDefinition.box(PROCESSOR,4,3,false));registerMachine(MachineDefinition.box(CENTRIFUGE,3,5,false));
        registerFuel("minecraft:coal",1600);registerFuel("minecraft:charcoal",1600);registerFuel("ender_infection:condensed_biomass",800);registerFuel(MOD_ID+":fuel_residue",400);
        for(String material:List.of("mycelium_bundle","spore_pod","condensed_biomass"))registerRecipe(new IndustryRecipe(MOD_ID+":slurry_"+material,PROCESSOR,"ender_infection:"+material,1,"minecraft:water",1000,"",0,"",0,SLURRY,1000,160,16));
        registerRecipe(new IndustryRecipe(MOD_ID+":reagent",CENTRIFUGE,"minecraft:glass_bottle",1,SLURRY,1000,"ender_infection:separated_reagent",1,MOD_ID+":fuel_residue",1,"",0,200,24));
        registerRecipe(new IndustryRecipe(MOD_ID+":tissue",CENTRIFUGE,"ender_infection:raw_tissue_sample",1,SLURRY,1000,MOD_ID+":purified_tissue",2,MOD_ID+":fuel_residue",1,"",0,240,24));
        registerRecipe(new ProcessRecipe(MOD_ID+":compound_separation",CENTRIFUGE,
            List.of(new ProcessRecipe.ItemPart(0,"minecraft:glass_bottle",2),new ProcessRecipe.ItemPart(1,"minecraft:paper",1)),
            List.of(new ProcessRecipe.FluidPart(0,SLURRY,1000),new ProcessRecipe.FluidPart(1,"minecraft:water",1000)),
            List.of(new ProcessRecipe.ItemPart(0,"ender_infection:separated_reagent",2),new ProcessRecipe.ItemPart(1,MOD_ID+":fuel_residue",1)),
            List.of(new ProcessRecipe.FluidPart(0,"minecraft:water",500),new ProcessRecipe.FluidPart(1,SLURRY,250)),
            List.of(new ProcessRecipe.ItemPart(0,"#minecraft:coals",1)),240,32,10));
    }
}

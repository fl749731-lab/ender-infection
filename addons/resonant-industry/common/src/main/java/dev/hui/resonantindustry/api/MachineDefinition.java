package dev.hui.resonantindustry.api;

import java.util.*;

/** Java 17 contract. Coordinates are relative to the controller, looking into the machine. */
public record MachineDefinition(String id, String nameKey, int width, int height, int length,
                                boolean generator, Map<Cell,String> cells) {
    public record Cell(int x,int y,int z) {}
    public MachineDefinition {
        IndustryApi.checkId(id); Objects.requireNonNull(nameKey); cells=Map.copyOf(cells);
        if(width<1||width>15||height<2||height>15||length<2||length>15||cells.size()!=width*height*length)
            throw new IllegalArgumentException("Invalid bounded multiblock volume");
        if(!"controller".equals(cells.get(new Cell(0,0,0))))throw new IllegalArgumentException("Missing controller");
        if(cells.values().stream().filter("controller"::equals).count()!=1)throw new IllegalArgumentException("One controller required");
        var roles=Set.of("air","controller","casing","window","rotor","energy_port","item_input","item_output","fluid_input","fluid_output");
        for(var e:cells.entrySet()){
            var c=e.getKey();
            if(c.x()<-width/2||c.x()>=(width+1)/2||c.y()<-1||c.y()>=height-1||c.z()<0||c.z()>=length||!roles.contains(e.getValue()))
                throw new IllegalArgumentException("Invalid layout cell "+c);
        }
    }
    public static MachineDefinition box(String id,int height,int length,boolean generator){
        var cells=new LinkedHashMap<Cell,String>();
        for(int y=-1;y<height-1;y++)for(int z=0;z<length;z++)for(int x=-1;x<=1;x++)
            cells.put(new Cell(x,y,z),x==0&&y>=0&&y<height-2&&z>0&&z<length-1?"air":"casing");
        cells.put(new Cell(0,0,0),"controller");cells.put(new Cell(-1,0,1),"energy_port");
        cells.put(new Cell(1,0,1),"item_input");
        if(!generator){cells.put(new Cell(0,-1,length-1),"item_output");cells.put(new Cell(1,0,length-1),"fluid_input");cells.put(new Cell(-1,0,length-1),"fluid_output");}
        if(height==4){cells.put(new Cell(0,height-2,1),"rotor");for(int y=0;y<2;y++)for(int x:new int[]{-1,1}){var c=new Cell(x,y,1);if(cells.get(c).equals("casing"))cells.put(c,"window");}}
        else {for(int z=1;z<length-1;z++)cells.put(new Cell(0,0,z),"rotor");for(int z=1;z<length-1;z++)cells.put(new Cell(0,1,z),"window");}
        return new MachineDefinition(id,"machine."+id.replace(':','.'),3,height,length,generator,cells);
    }
}

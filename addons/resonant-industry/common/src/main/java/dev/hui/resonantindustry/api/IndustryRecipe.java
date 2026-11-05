package dev.hui.resonantindustry.api;

/** All amounts are per batch; input and output fluid reservoirs are independent. */
public record IndustryRecipe(String id,String machine,String input,int inputCount,String fluid,int fluidAmount,
                             String output,int outputCount,String byproduct,int byproductCount,
                             String outputFluid,int outputFluidAmount,int ticks,int energyPerTick) {
    public IndustryRecipe {
        IndustryApi.checkId(id);IndustryApi.checkId(machine);
        check(input,inputCount);check(output,outputCount);check(byproduct,byproductCount);
        check(fluid,fluidAmount);check(outputFluid,outputFluidAmount);
        if(inputCount>64||outputCount>64||byproductCount>64||fluidAmount>8000||outputFluidAmount>8000||ticks<20||ticks>72000||energyPerTick<0||energyPerTick>100000)
            throw new IllegalArgumentException("Recipe outside supported bounds");
        if(inputCount==0&&fluidAmount==0)throw new IllegalArgumentException("Recipe needs a material input");
    }
    private static void check(String id,int count){if(count<0||(id.isEmpty()?count!=0:count==0))throw new IllegalArgumentException("Invalid material count");if(!id.isEmpty())IndustryApi.checkId(id);}
}

package example;

import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.IndustryRecipe;
import dev.hui.resonantindustry.api.ProcessRecipe;
import java.util.List;

/** Invoke from your addon constructor after its required resonant_industry dependency. */
public final class ExtraIndustryRecipes {
    public static void register() {
        IndustryApi.registerRecipe(new IndustryRecipe(
                "my_addon:slurry_books", IndustryApi.CENTRIFUGE,
                "minecraft:paper", 2, IndustryApi.SLURRY, 1000,
                "minecraft:book", 1, "", 0, "", 0, 200, 24));
        IndustryApi.registerRecipe(new ProcessRecipe(
                "my_addon:bound_notebooks", IndustryApi.CENTRIFUGE,
                List.of(new ProcessRecipe.ItemPart(0, "minecraft:paper", 2),
                        new ProcessRecipe.ItemPart(1, "minecraft:leather", 1)),
                List.of(new ProcessRecipe.FluidPart(0, IndustryApi.SLURRY, 1000),
                        new ProcessRecipe.FluidPart(1, "minecraft:water", 1000)),
                List.of(new ProcessRecipe.ItemPart(0, "minecraft:book", 2)),
                List.of(new ProcessRecipe.FluidPart(0, "minecraft:water", 500)),
                List.of(new ProcessRecipe.ItemPart(0, "#minecraft:coals", 1)),
                240, 32, 10));
    }
    private ExtraIndustryRecipes() {}
}

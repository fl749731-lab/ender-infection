package example;

import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.IndustryRecipe;

/** Invoke from your addon constructor after its required resonant_industry dependency. */
public final class ExtraIndustryRecipes {
    public static void register() {
        IndustryApi.registerRecipe(new IndustryRecipe(
                "my_addon:slurry_books", IndustryApi.CENTRIFUGE,
                "minecraft:paper", 2, IndustryApi.SLURRY, 1000,
                "minecraft:book", 1, "", 0, "", 0, 200, 24));
    }
    private ExtraIndustryRecipes() {}
}

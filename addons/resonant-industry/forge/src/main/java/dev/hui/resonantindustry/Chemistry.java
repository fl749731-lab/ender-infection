package dev.hui.resonantindustry;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.fluids.FluidStack;

/** Starter experiments use game-scale quantities and temperatures. */
public final class Chemistry {
    public enum Station {
        BENCH, HEATER, STIRRER, FILTER, CONDENSER;

        public boolean processesSamples() { return this != BENCH && this != CONDENSER; }
    }

    public enum Liquid {
        ACID_EXTRACT(0xffe9a848), ALKALINE_EXTRACT(0xff61bf9c), NEUTRAL_SOLUTION(0xff9e91df),
        CALCIUM_EXTRACT(0xffeabf79), CALCIUM_SUSPENSION(0xffded7b8), CLEAR_FILTRATE(0xff8ac9ba),
        DISTILLED_WATER(0xff70c9ec), FILTRATE_CONCENTRATE(0xff5b9c72), NEUTRAL_CONCENTRATE(0xff7962ab);

        public final int tint;

        Liquid(int tint) { this.tint = tint; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Portion(String fluid, int amount) {
        boolean matches(FluidStack sample, boolean exact) {
            if (amount == 0) return sample.isEmpty();
            if (sample.isEmpty() || !LabPlatform.plain(sample)) return false;
            var id = BuiltInRegistries.FLUID.getKey(sample.getFluid());
            boolean compatible = id.equals(IndustryContent.id(fluid))
                    || fluid.equals("minecraft:water") && id.equals(IndustryContent.id("distilled_water"));
            return compatible && (exact ? sample.getAmount() == amount : sample.getAmount() >= amount);
        }

        FluidStack stack() {
            if (amount == 0) return FluidStack.EMPTY;
            return new FluidStack(BuiltInRegistries.FLUID.get(IndustryContent.id(fluid)), amount);
        }
    }

    private static final Portion EMPTY = new Portion("", 0);
    private static final Portion WATER = new Portion("minecraft:water", 250);
    private static final Portion ACID_DOSE = new Portion("acid_extract", 25);
    private static final Portion BASE_DOSE = new Portion("alkaline_extract", 25);
    private static final Portion NEUTRAL_BATCH = new Portion("neutral_solution", 50);
    private static final Portion CALCIUM_DOSE = new Portion("calcium_extract", 25);
    private static final Portion SUSPENSION = new Portion("calcium_suspension", 50);
    private static final Portion FILTRATE = new Portion("clear_filtrate", 50);
    private static final Portion DISTILLATE = new Portion("distilled_water", 40);

    public enum Experiment {
        ALKALI_ASH(Station.HEATER, EMPTY, EMPTY, "minecraft:dried_kelp", EMPTY, "alkali_ash", 400, 100),
        FRUIT_EXTRACTION(Station.STIRRER, WATER, EMPTY, "minecraft:apple", new Portion("acid_extract", 250), "", 0, 80),
        ALKALI_EXTRACTION(Station.STIRRER, WATER, EMPTY, "alkali_ash", new Portion("alkaline_extract", 250), "", 0, 80),
        NEUTRALIZATION(Station.STIRRER, ACID_DOSE, BASE_DOSE, "", NEUTRAL_BATCH, "", 0, 100),
        CRYSTALLIZATION(Station.HEATER, NEUTRAL_BATCH, EMPTY, "", EMPTY, "neutral_salt", 100, 120),
        TISSUE_WASH(Station.STIRRER, NEUTRAL_BATCH, EMPTY, "ender_infection:raw_tissue_sample",
                new Portion("nutrient_slurry", 50), "purified_tissue", 0, 120),
        CALCIUM_EXTRACTION(Station.STIRRER, ACID_DOSE, EMPTY, "minecraft:calcite", CALCIUM_DOSE, "", 0, 80),
        PRECIPITATION(Station.STIRRER, CALCIUM_DOSE, BASE_DOSE, "", SUSPENSION, "", 0, 100),
        FILTRATION(Station.FILTER, SUSPENSION, EMPTY, "filter_paper", EMPTY, FILTRATE, "minecraft:calcite", 0, 60),
        FILTRATE_DISTILLATION(Station.HEATER, FILTRATE, EMPTY, "", new Portion("filtrate_concentrate", 10),
                DISTILLATE, "", 100, 140),
        NEUTRAL_DISTILLATION(Station.HEATER, NEUTRAL_BATCH, EMPTY, "", new Portion("neutral_concentrate", 10),
                DISTILLATE, "", 100, 140);

        public final Station station;
        public final Portion sample, reagent, product, receiverProduct;
        public final String ingredient, solidProduct;
        public final int temperature, duration;

        Experiment(Station station, Portion sample, Portion reagent, String ingredient, Portion product,
                   String solidProduct, int temperature, int duration) {
            this(station, sample, reagent, ingredient, product, EMPTY, solidProduct, temperature, duration);
        }

        Experiment(Station station, Portion sample, Portion reagent, String ingredient, Portion product,
                   Portion receiverProduct, String solidProduct, int temperature, int duration) {
            this.station = station;
            this.sample = sample;
            this.reagent = reagent;
            this.ingredient = ingredient;
            this.product = product;
            this.receiverProduct = receiverProduct;
            this.solidProduct = solidProduct;
            this.temperature = temperature;
            this.duration = duration;
        }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public boolean distillation() { return station == Station.HEATER && receiverProduct.amount > 0; }

        public int evaporated() { return sample.amount + reagent.amount - product.amount - receiverProduct.amount; }

        static Experiment read(String id) {
            for (var experiment : values()) if (experiment.id().equals(id)) return experiment;
            return null;
        }
    }

    private Chemistry() {}
}

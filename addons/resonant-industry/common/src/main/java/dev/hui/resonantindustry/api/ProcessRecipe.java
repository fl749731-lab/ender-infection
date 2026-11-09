package dev.hui.resonantindustry.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Java 17 contract. Input/output positions are numbered separately, starting at zero. */
public record ProcessRecipe(String id, String machine, List<ItemPart> inputs,
                            List<FluidPart> fluids, List<ItemPart> outputs,
                            List<FluidPart> outputFluids, List<ItemPart> catalysts,
                            int ticks, int energyPerTick, int priority) {
    public record ItemPart(int slot, String ingredient, int amount) {
        public ItemPart {
            selector(ingredient);
            if (slot < 0 || slot > 5 || amount < 1 || amount > 64)
                throw new IllegalArgumentException("Invalid item part");
        }
    }
    public record FluidPart(int tank, String ingredient, int amount) {
        public FluidPart {
            selector(ingredient);
            if (tank < 0 || tank > 3 || amount < 1 || amount > 8000)
                throw new IllegalArgumentException("Invalid fluid part");
        }
    }
    private static void selector(String id) {
        Objects.requireNonNull(id);
        IndustryApi.checkId(id.startsWith("#") ? id.substring(1) : id);
    }
    public ProcessRecipe {
        IndustryApi.checkId(id); IndustryApi.checkId(machine);
        inputs = List.copyOf(inputs); fluids = List.copyOf(fluids);
        outputs = List.copyOf(outputs); outputFluids = List.copyOf(outputFluids);
        catalysts = List.copyOf(catalysts);
        if (ticks < 20 || ticks > 72000 || energyPerTick < 0 || energyPerTick > 100000
                || priority < -1000 || priority > 1000 || inputs.isEmpty() && fluids.isEmpty())
            throw new IllegalArgumentException("Invalid process bounds");
        distinct(inputs.stream().map(ItemPart::slot).toList(), 6);
        distinct(fluids.stream().map(FluidPart::tank).toList(), 4);
        distinct(outputs.stream().map(ItemPart::slot).toList(), 4);
        distinct(outputFluids.stream().map(FluidPart::tank).toList(), 4);
        distinct(catalysts.stream().map(ItemPart::slot).toList(), 2);
        if (outputs.stream().anyMatch(p -> p.ingredient().startsWith("#"))
                || outputFluids.stream().anyMatch(p -> p.ingredient().startsWith("#")))
            throw new IllegalArgumentException("Outputs require exact registry IDs");
    }
    private static void distinct(List<Integer> positions, int bound) {
        if (positions.size() > bound || new HashSet<>(positions).size() != positions.size()
                || positions.stream().anyMatch(p -> p >= bound))
            throw new IllegalArgumentException("Repeated or unsupported process position");
    }
    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    public static ProcessRecipe fromLegacy(IndustryRecipe r) {
        var inputs = r.inputCount() == 0 ? List.<ItemPart>of() : List.of(new ItemPart(0, r.input(), r.inputCount()));
        var fluids = r.fluidAmount() == 0 ? List.<FluidPart>of() : List.of(new FluidPart(0, r.fluid(), r.fluidAmount()));
        var outputs = new ArrayList<ItemPart>();
        if (r.outputCount() > 0) outputs.add(new ItemPart(0, r.output(), r.outputCount()));
        if (r.byproductCount() > 0) outputs.add(new ItemPart(1, r.byproduct(), r.byproductCount()));
        var outFluid = r.outputFluidAmount() == 0 ? List.<FluidPart>of()
                : List.of(new FluidPart(0, r.outputFluid(), r.outputFluidAmount()));
        return new ProcessRecipe(r.id(), r.machine(), inputs, fluids, outputs, outFluid, List.of(), r.ticks(), r.energyPerTick(), 0);
    }
}

package dev.hui.enderinfection.api.institute;

import java.util.Objects;
import java.util.Set;

/** Item IDs keep the registration contract portable across both loaders. Empty reagentId means no reagent. */
public record ProcessingRecipe(String id, Set<String> devices, String inputId, int inputCount,
        String reagentId, int reagentCount, String outputId, int outputCount, int ticks) {
    public ProcessingRecipe {
        InstituteRegistry.checkId(id); InstituteRegistry.checkId(inputId); InstituteRegistry.checkId(outputId);
        devices = Set.copyOf(devices); Objects.requireNonNull(reagentId);
        if (devices.isEmpty() || devices.size() > 16) throw new IllegalArgumentException("A recipe needs 1..16 devices");
        devices.forEach(InstituteRegistry::checkId);
        if (!reagentId.isEmpty()) InstituteRegistry.checkId(reagentId);
        if (inputCount < 1 || inputCount > 64 || outputCount < 1 || outputCount > 64
                || (reagentId.isEmpty() ? reagentCount != 0 : reagentCount < 1 || reagentCount > 64))
            throw new IllegalArgumentException("Invalid recipe item counts");
        if (ticks < 20 || ticks > 72000) throw new IllegalArgumentException("Recipe duration must be 20..72000 ticks");
    }
}

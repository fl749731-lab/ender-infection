package dev.hui.enderinfection.api.institute;

public interface InstituteRegistrar {
    void room(RoomDefinition definition);
    void device(DeviceDefinition definition);
    /** Additive v1 extension; older registrar implementations remain binary compatible. */
    default void processing(ProcessingRecipe definition) { throw new UnsupportedOperationException("Processing recipes unavailable"); }
}

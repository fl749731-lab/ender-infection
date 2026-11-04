package dev.hui.enderinfection.api.institute;

/** Template dimensions, measured in blocks. */
public record RoomSize(int x, int y, int z) {
    public RoomSize {
        if (x < 1 || y < 1 || z < 1 || x > 128 || y > 128 || z > 128)
            throw new IllegalArgumentException("Room dimensions must be between 1 and 128 blocks");
    }
}

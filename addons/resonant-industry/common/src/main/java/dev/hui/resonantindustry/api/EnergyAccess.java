package dev.hui.resonantindustry.api;
public interface EnergyAccess {
    int stored(); int capacity(); int receive(int amount,boolean simulate); int extract(int amount,boolean simulate);
}

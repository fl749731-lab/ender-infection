package dev.hui.resonantindustry.api;
public interface FluidAccess {
    String fluid(); int amount(); int capacity();
    int fill(String fluid,int amount,boolean simulate);
    int drain(int amount,boolean simulate);
}

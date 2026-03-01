package me.aleksilassila.litematica.printer.implementation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

public class LocalPlayerRotationWrapper extends LocalPlayer {
    private float overrideYaw;
    private float overridePitch;
    public static boolean isWrapperInit = false;
    private static LocalPlayerRotationWrapper instance;
    public static LocalPlayerRotationWrapper getInstance() {
        if(!isWrapperInit || instance == null) {
            instance = new LocalPlayerRotationWrapper();
            isWrapperInit = true;
        }
        return instance;
    }

    private LocalPlayerRotationWrapper() {
        super(Minecraft.getInstance(), (ClientLevel) Minecraft.getInstance().player.level(), Minecraft.getInstance().player.connection,
                Minecraft.getInstance().player.getStats(), Minecraft.getInstance().player.getRecipeBook(), Input.EMPTY, false);
    }

    public LocalPlayerRotationWrapper rot(float yaw, float pitch) {
        this.overrideYaw = yaw;
        this.overridePitch = pitch;
        return instance;
    }

    @Override
    public float getYRot() {
        return overrideYaw;
    }

    @Override
    public float getXRot() {
        return overridePitch;
    }
}
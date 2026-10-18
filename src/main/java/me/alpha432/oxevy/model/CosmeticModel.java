package me.alpha432.oxevy.model;

import me.alpha432.oxevy.cosmetics.BedrockGeometry;
import net.minecraft.resources.Identifier;

public class CosmeticModel {
    private final BedrockGeometry geometry;
    private final Identifier textureLocation;

    public CosmeticModel(BedrockGeometry geometry, Identifier textureLocation) {
        this.geometry = geometry;
        this.textureLocation = textureLocation;
    }

    public BedrockGeometry getGeometry() {
        return geometry;
    }

    public Identifier getTextureLocation() {
        return textureLocation;
    }
}
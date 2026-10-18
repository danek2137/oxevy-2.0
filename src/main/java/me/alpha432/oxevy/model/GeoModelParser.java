package me.alpha432.oxevy.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

public class GeoModelParser {

    public static class ParsedGeometry {
        public final String identifier;
        public final int textureWidth;
        public final int textureHeight;
        public final JsonArray bones;

        public ParsedGeometry(String identifier, int textureWidth, int textureHeight, JsonArray bones) {
            this.identifier = identifier;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.bones = bones;
        }
    }

    public static class BoneInfo {
        public final String name;
        public final String parentName;
        public final float[] pivot;
        public final float[] rotation;
        public final boolean mirror;
        public final JsonArray cubes;

        public BoneInfo(String name, String parentName, float[] pivot, float[] rotation, boolean mirror, JsonArray cubes) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.rotation = rotation;
            this.mirror = mirror;
            this.cubes = cubes;
        }
    }

    public static ParsedGeometry parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.size() == 0) {
            throw new IllegalArgumentException("No geometry found in JSON");
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        String identifier = description.get("identifier").getAsString();
        int textureWidth = description.get("texture_width").getAsInt();
        int textureHeight = description.get("texture_height").getAsInt();
        JsonArray bones = geometry.getAsJsonArray("bones");

        return new ParsedGeometry(identifier, textureWidth, textureHeight, bones);
    }

    public static ModelPart buildModel(ParsedGeometry geometry) {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition rootDefinition = meshDefinition.getRoot();

        Map<String, PartDefinition> partMap = new HashMap<>();
        Map<String, Boolean> mirrorMap = new HashMap<>();
        partMap.put("root", rootDefinition);
        mirrorMap.put("root", false);

        // First pass: collect all bone info
        Map<String, BoneInfo> boneInfos = new HashMap<>();
        for (JsonElement boneElement : geometry.bones) {
            JsonObject bone = boneElement.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parentName = bone.has("parent") ? bone.get("parent").getAsString() : "root";

            float[] pivot = parseFloatArray(bone, "pivot", new float[]{0, 0, 0});
            float[] rotation = parseFloatArray(bone, "rotation", new float[]{0, 0, 0});
            boolean mirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();
            JsonArray cubes = bone.has("cubes") ? bone.getAsJsonArray("cubes") : null;

            boneInfos.put(name, new BoneInfo(name, parentName, pivot, rotation, mirror, cubes));
        }

        // Create parts in hierarchy order (parents before children)
        createPartsRecursive(boneInfos, partMap, mirrorMap, "root", meshDefinition, geometry);

        LayerDefinition layerDefinition = LayerDefinition.create(meshDefinition, geometry.textureWidth, geometry.textureHeight);
        return layerDefinition.bakeRoot();
    }

    private static void createPartsRecursive(
            Map<String, BoneInfo> boneInfos,
            Map<String, PartDefinition> partMap,
            Map<String, Boolean> mirrorMap,
            String parentName,
            MeshDefinition meshDefinition,
            ParsedGeometry geometry) {

        PartDefinition parent = partMap.get(parentName);
        boolean parentMirror = mirrorMap.getOrDefault(parentName, false);

        for (Map.Entry<String, BoneInfo> entry : boneInfos.entrySet()) {
            BoneInfo info = entry.getValue();
            if (!info.parentName.equals(parentName)) continue;
            if (partMap.containsKey(info.name)) continue; // Already created

            // Compute effective mirror (inherit from parent)
            boolean effectiveMirror = parentMirror || info.mirror;

            // Bedrock pivot → Java pivot
            PartPose pose = PartPose.offsetAndRotation(
                    -info.pivot[0],
                    24.0F - info.pivot[1],
                    info.pivot[2],
                    (float) Math.toRadians(-info.rotation[0]),
                    (float) Math.toRadians(-info.rotation[1]),
                    (float) Math.toRadians(info.rotation[2])
            );

            // Create a builder with all cubes for this bone
            CubeListBuilder builder = CubeListBuilder.create();
            if (info.cubes != null) {
                for (JsonElement cubeElement : info.cubes) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    float[] origin = parseFloatArray(cube, "origin", new float[]{0, 0, 0});
                    float[] size = parseFloatArray(cube, "size", new float[]{0, 0, 0});
                    float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0f;
                    CubeDeformation deformation = new CubeDeformation(inflate);

                    int u = 0, v = 0;
                    if (cube.has("uv")) {
                        JsonElement uvElement = cube.get("uv");
                        if (uvElement.isJsonArray()) {
                            JsonArray uv = uvElement.getAsJsonArray();
                            u = uv.get(0).getAsInt();
                            v = uv.get(1).getAsInt();
                        } else if (uvElement.isJsonObject()) {
                            JsonObject uvObj = uvElement.getAsJsonObject();
                            // Bedrock geometry uses per-face UVs, pick the first available face
                            // Order: north, south, east, west, up, down (common Bedrock order)
                            String[] faces = {"north", "south", "east", "west", "up", "down"};
                            for (String face : faces) {
                                if (uvObj.has(face)) {
                                    JsonObject faceUv = uvObj.getAsJsonObject(face);
                                    if (faceUv.has("uv")) {
                                        JsonArray uv = faceUv.getAsJsonArray("uv");
                                        u = uv.get(0).getAsInt();
                                        v = uv.get(1).getAsInt();
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    if (effectiveMirror) {
                        u = geometry.textureWidth - u - (int) size[0];
                    }

                    // Bedrock origin is absolute; convert to Java model part coords (relative to pivot in Java space)
                    float ox = -(origin[0] - info.pivot[0]);
                    float oy = (24.0F - (origin[1] + size[1] - info.pivot[1]));
                    float oz = origin[2] - info.pivot[2];
                    builder.texOffs(u, v).addBox(
                            ox,
                            oy,
                            oz,
                            size[0], size[1], size[2],
                            deformation
                    );
                }
            }

            PartDefinition part = parent.addOrReplaceChild(info.name, builder, pose);
            partMap.put(info.name, part);
            mirrorMap.put(info.name, effectiveMirror);

            // Recursively create children
            createPartsRecursive(boneInfos, partMap, mirrorMap, info.name, meshDefinition, geometry);
        }
    }

    private static float[] parseFloatArray(JsonObject obj, String key, float[] defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonArray arr = obj.getAsJsonArray(key);
        float[] result = new float[Math.min(arr.size(), defaultValue.length)];
        for (int i = 0; i < result.length; i++) {
            result[i] = arr.get(i).getAsFloat();
        }
        return result;
    }
}
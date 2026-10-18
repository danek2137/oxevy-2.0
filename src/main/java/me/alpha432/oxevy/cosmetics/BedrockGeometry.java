package me.alpha432.oxevy.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bedrock geometry representation parsed from geometry.steve.json / geometry.alex.json
 * Stays in Bedrock coordinate space: Y-up, origin at feet, units = pixels (1/16 block)
 */
public class BedrockGeometry {

    public final String identifier;
    public final int textureWidth;
    public final int textureHeight;
    public final List<Bone> bones = new ArrayList<>();
    public final Map<String, Bone> boneMap = new HashMap<>();

    public BedrockGeometry(String identifier, int textureWidth, int textureHeight) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static BedrockGeometry parse(String json) {
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

        BedrockGeometry result = new BedrockGeometry(identifier, textureWidth, textureHeight);

        // First pass: create all bones
        Map<String, Bone> tempMap = new HashMap<>();
        for (JsonElement boneElement : bones) {
            JsonObject bone = boneElement.getAsJsonObject();
            Bone b = parseBone(bone, textureWidth, textureHeight);
            tempMap.put(b.name, b);
            result.bones.add(b);
            result.boneMap.put(b.name, b);
        }

        // Second pass: link parent/children
        for (Bone b : result.bones) {
            if (b.parentName != null) {
                Bone parent = tempMap.get(b.parentName);
                if (parent != null) {
                    b.parent = parent;
                    parent.children.add(b);
                }
            }
        }

        // Find root bones (no parent)
        result.bones.removeIf(b -> b.parent != null);

        return result;
    }

    private static Bone parseBone(JsonObject bone, int texW, int texH) {
        String name = bone.get("name").getAsString();
        String parentName = bone.has("parent") ? bone.get("parent").getAsString() : null;

        float[] pivot = parseFloatArray(bone, "pivot", new float[]{0, 0, 0});
        float[] rotation = parseFloatArray(bone, "rotation", new float[]{0, 0, 0});
        boolean mirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();
        JsonArray cubes = bone.has("cubes") ? bone.getAsJsonArray("cubes") : null;

        List<Cube> cubeList = new ArrayList<>();
        if (cubes != null) {
            for (JsonElement cubeElement : cubes) {
                Cube c = parseCube(cubeElement.getAsJsonObject(), texW, texH, mirror);
                if (c != null) cubeList.add(c);
            }
        }

        return new Bone(name, parentName, pivot, rotation, mirror, cubeList);
    }

    private static Cube parseCube(JsonObject cube, int texW, int texH, boolean boneMirror) {
        float[] origin = parseFloatArray(cube, "origin", new float[]{0, 0, 0});
        float[] size = parseFloatArray(cube, "size", new float[]{0, 0, 0});
        if (size[0] <= 0 || size[1] <= 0 || size[2] <= 0) return null;

        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0f;
        boolean mirror = cube.has("mirror") ? cube.get("mirror").getAsBoolean() : boneMirror;

        UVData uvData = null;
        if (cube.has("uv")) {
            JsonElement uvElement = cube.get("uv");
            if (uvElement.isJsonArray()) {
                // Box UV (array format) [u, v]
                JsonArray uv = uvElement.getAsJsonArray();
                uvData = new UVData(uv.get(0).getAsInt(), uv.get(1).getAsInt(), texW, texH);
            } else if (uvElement.isJsonObject()) {
                // Per-face UV (object format)
                uvData = parsePerFaceUV(uvElement.getAsJsonObject(), texW, texH);
            }
        }
        if (uvData == null) {
            uvData = new UVData(0, 0, texW, texH);
        }

        return new Cube(origin, size, inflate, mirror, uvData);
    }

    private static UVData parsePerFaceUV(JsonObject uvObj, int texW, int texH) {
        UVData data = new UVData(texW, texH);
        String[] faces = {"north", "east", "south", "west", "up", "down"};
        for (String face : faces) {
            if (uvObj.has(face)) {
                JsonObject faceUv = uvObj.getAsJsonObject(face);
                if (faceUv.has("uv")) {
                    JsonArray uv = faceUv.getAsJsonArray("uv");
                    int u = uv.get(0).getAsInt();
                    int v = uv.get(1).getAsInt();
                    float su = 0, sv = 0;
                    if (faceUv.has("uv_size")) {
                        JsonArray uvSize = faceUv.getAsJsonArray("uv_size");
                        su = uvSize.get(0).getAsFloat();
                        sv = uvSize.get(1).getAsFloat();
                    } else {
                        // Default face size from cube size? We'll handle in renderer
                        su = 0; sv = 0;
                    }
                    data.setFaceUV(face, u, v, su, sv);
                }
            }
        }
        return data;
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

    // ===== Data classes =====

    public static class Bone {
        public final String name;
        public final String parentName;
        public final float[] pivot;      // Bedrock pixels, Y-up
        public final float[] rotation;   // Degrees, ZYX order
        public final boolean mirror;
        public final List<Cube> cubes;
        public Bone parent;
        public final List<Bone> children = new ArrayList<>();

        public Bone(String name, String parentName, float[] pivot, float[] rotation,
                    boolean mirror, List<Cube> cubes) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.rotation = rotation;
            this.mirror = mirror;
            this.cubes = cubes;
        }
    }

    public static class Cube {
        public final float[] origin;   // Bedrock pixels, absolute (min corner), Y-up
        public final float[] size;     // dx, dy, dz in pixels
        public final float inflate;
        public final boolean mirror;
        public final UVData uvData;

        public Cube(float[] origin, float[] size, float inflate, boolean mirror, UVData uvData) {
            this.origin = origin;
            this.size = size;
            this.inflate = inflate;
            this.mirror = mirror;
            this.uvData = uvData;
        }
    }

    public static class UVData {
        public final int textureWidth;
        public final int textureHeight;

        // Box UV (array format)
        public boolean isBoxUV = true;
        public int boxU = 0, boxV = 0;

        // Per-face UV (object format) - [u1, v1, u2, v2] in UV pixels
        public float[] north = null;  // -Z
        public float[] east = null;   // +X
        public float[] south = null;  // +Z
        public float[] west = null;   // -X
        public float[] up = null;     // +Y
        public float[] down = null;   // -Y

        // Box UV constructor
        public UVData(int u, int v, int texW, int texH) {
            this.textureWidth = texW;
            this.textureHeight = texH;
            this.boxU = u;
            this.boxV = v;
            this.isBoxUV = true;
        }

        // Per-face UV constructor
        public UVData(int texW, int texH) {
            this.textureWidth = texW;
            this.textureHeight = texH;
            this.isBoxUV = false;
        }

        public void setFaceUV(String face, int u, int v, float su, float sv) {
            this.isBoxUV = false;
            float u2 = su > 0 ? u + su : u + 16; // default 16 if not specified
            float v2 = sv > 0 ? v + sv : v + 16;
            float[] rect = {u, v, u2, v2};
            switch (face) {
                case "north": north = rect; break;  // -Z
                case "east": east = rect; break;    // +X
                case "south": south = rect; break;  // +Z
                case "west": west = rect; break;    // -X
                case "up": up = rect; break;        // +Y
                case "down": down = rect; break;    // -Y
            }
        }

        public float[] getFaceUV(String face) {
            return switch (face) {
                case "north" -> north;
                case "east" -> east;
                case "south" -> south;
                case "west" -> west;
                case "up" -> up;
                case "down" -> down;
                default -> null;
            };
        }
    }

    // ===== Render-time computation =====

    /** Computes bone matrices in Bedrock pixel space (Y-up, feet origin) */
    public void computeBoneMatrices(float ageInTicks, Map<String, BoneTransform> outTransforms) {
        Matrix4f identity = new Matrix4f().identity();
        for (Bone root : bones) {
            computeBoneMatrixRecursive(root, identity, ageInTicks, outTransforms);
        }
    }

    private void computeBoneMatrixRecursive(Bone bone, Matrix4f parentMatrix, float ageInTicks,
                                            Map<String, BoneTransform> outTransforms) {
        // Build bone's local matrix in Bedrock pixel space
        Matrix4f local = new Matrix4f().identity();

        // Translate to pivot
        local.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);

        // Apply rotation (ZYX order, degrees) - Bedrock uses ZYX
        if (bone.rotation[2] != 0) local.rotateZ((float) Math.toRadians(bone.rotation[2]));
        if (bone.rotation[1] != 0) local.rotateY((float) Math.toRadians(bone.rotation[1]));
        if (bone.rotation[0] != 0) local.rotateX((float) Math.toRadians(bone.rotation[0]));

        // Translate back by pivot so geometry rotates around the pivot
        local.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);

        // Apply animation offsets (TODO: implement animation system)
        // For now, no animation

        // Combine with parent
        Matrix4f world = new Matrix4f(parentMatrix).mul(local);

        // Store transform for this bone
        Matrix3f normalMatrix = new Matrix3f(world).invert().transpose();
        outTransforms.put(bone.name, new BoneTransform(world, normalMatrix, bone.mirror));

        // Recurse children
        for (Bone child : bone.children) {
            computeBoneMatrixRecursive(child, world, ageInTicks, outTransforms);
        }
    }

    public static class BoneTransform {
        public final Matrix4f matrix;      // Bone-to-world in Bedrock pixels
        public final Matrix3f normalMatrix; // For normal transformation
        public final boolean mirror;

        public BoneTransform(Matrix4f matrix, Matrix3f normalMatrix, boolean mirror) {
            this.matrix = matrix;
            this.normalMatrix = normalMatrix;
            this.mirror = mirror;
        }
    }
}
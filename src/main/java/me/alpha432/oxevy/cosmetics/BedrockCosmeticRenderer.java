package me.alpha432.oxevy.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders Bedrock geometry using submitCustomGeometry.
 * Handles: box UV, per-face UV, mirror, inflate, double-sided (12 quads/cube).
 */
public class BedrockCosmeticRenderer {

    // Conversion from Bedrock pixel space (Y-up, feet origin, pixels) to vanilla
    // model space (Y-down, head origin, blocks). The mixin renders inside the
    // vanilla model pose, which LivingEntityRenderer.submit builds as:
    //   scale(-1,-1,1) -> scale(0.9375) -> translate(0,-1.501,0)
    // so a model-space point q lands at entity height 0.9375 * (1.501 - q.y).
    // The mapping must therefore be:
    //   model_x = bedrock_x / 16
    //   model_y = (24 - bedrock_y) / 16   (head base at bedrock y=24 -> model y=0)
    //   model_z = bedrock_z / 16
    // NOTE: the translate constant must be -24, not -48: -48 shifts every
    // cosmetic down by 1.5 blocks (a headband at bedrock y=24..32 would land at
    // entity height 0..0.47 = on the legs).
    private static final Matrix4f BEDROCK_TO_MODEL = new Matrix4f()
            .scale(1f/16f, -1f/16f, 1f/16f)
            .translate(0, -24, 0);

    // Normal matrix for the conversion (inverse transpose of upper 3x3)
    // BEDROCK_TO_MODEL 3x3 = diag(1/16, -1/16, 1/16)
    // Inverse transpose = diag(16, -16, 16) normalized = diag(1, -1, 1)
    private static final Matrix3f BEDROCK_TO_MODEL_NORMAL = new Matrix3f()
            .scale(1, -1, 1);

    /**
     * Renders a cosmetic geometry.
     * @param poseStack Current pose stack (has vanilla entity transform)
     * @param collector SubmitNodeCollector for custom geometry
     * @param geometry Parsed Bedrock geometry
     * @param textureLocation Texture resource location
     * @param packedLight Packed light coordinates
     * @param ageInTicks Entity age for animation
     */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector,
                              BedrockGeometry geometry, Identifier textureLocation,
                              int packedLight, float ageInTicks) {

        // Compute bone transforms in Bedrock pixel space
        Map<String, BedrockGeometry.BoneTransform> boneTransforms = new HashMap<>();
        geometry.computeBoneMatrices(ageInTicks, boneTransforms);

        // Submit custom geometry
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(textureLocation),
                (pose, consumer) -> {
                    // Render each bone's cubes
                    for (BedrockGeometry.Bone bone : geometry.bones) {
                        renderBoneRecursive(bone, boneTransforms, pose, consumer, packedLight,
                                geometry.textureWidth, geometry.textureHeight);
                    }
                });
    }

    private static void renderBoneRecursive(BedrockGeometry.Bone bone,
                                            Map<String, BedrockGeometry.BoneTransform> boneTransforms,
                                            PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                                            int texW, int texH) {

        BedrockGeometry.BoneTransform bt = boneTransforms.get(bone.name);
        if (bt == null) return;

        // Combined matrix: conversion * bone (model space)
        Matrix4f boneModelMatrix = new Matrix4f(BEDROCK_TO_MODEL).mul(bt.matrix);
        Matrix3f boneNormalMatrix = new Matrix3f(BEDROCK_TO_MODEL_NORMAL).mul(bt.normalMatrix);

        // Render cubes for this bone
        for (BedrockGeometry.Cube cube : bone.cubes) {
            renderCube(cube, boneModelMatrix, boneNormalMatrix, bt.mirror,
                    pose, consumer, packedLight, texW, texH);
        }

        // Recurse children
        for (BedrockGeometry.Bone child : bone.children) {
            renderBoneRecursive(child, boneTransforms, pose, consumer, packedLight, texW, texH);
        }
    }

    private static void renderCube(BedrockGeometry.Cube cube,
                                   Matrix4f boneModelMatrix, Matrix3f boneNormalMatrix,
                                   boolean boneMirror,
                                   PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                                   int texW, int texH) {

        float ox = cube.origin[0];
        float oy = cube.origin[1];
        float oz = cube.origin[2];
        float dx = cube.size[0];
        float dy = cube.size[1];
        float dz = cube.size[2];
        float inflate = cube.inflate;
        boolean mirror = cube.mirror || boneMirror;

        // Apply inflate
        float ix = ox - inflate;
        float iy = oy - inflate;
        float iz = oz - inflate;
        float idx = dx + 2 * inflate;
        float idy = dy + 2 * inflate;
        float idz = dz + 2 * inflate;

        // Mirror: swap X min/max
        if (mirror) {
            float tmp = ix;
            ix = ix + idx;
            idx = -idx; // We'll handle by flipping face winding
        }

        // 8 corners of the cube in Bedrock pixel space (Y-up)
        Vector3f A = new Vector3f(ix, iy, iz);
        Vector3f B = new Vector3f(ix + idx, iy, iz);
        Vector3f C = new Vector3f(ix + idx, iy + idy, iz);
        Vector3f D = new Vector3f(ix, iy + idy, iz);
        Vector3f E = new Vector3f(ix, iy, iz + idz);
        Vector3f F = new Vector3f(ix + idx, iy, iz + idz);
        Vector3f G = new Vector3f(ix + idx, iy + idy, iz + idz);
        Vector3f H = new Vector3f(ix, iy + idy, iz + idz);

        // Transform corners to model space
        Vector3f[] corners = {A, B, C, D, E, F, G, H};
        Vector3f[] modelCorners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            modelCorners[i] = new Vector3f(corners[i]);
            boneModelMatrix.transformPosition(modelCorners[i]);
        }

        // Get face UV rectangles (in texture pixels, normalized to 0-1 in renderCubeFace)
        UVRect[] faceUVs = getFaceUVRects(cube.uvData, cube.size, texW, texH, mirror);

        // Define 6 faces with outward normals (in Bedrock space, Y-up)
        FaceData[] faces = {
            // East (+X)
            new FaceData(new int[]{5, 1, 2, 6}, new Vector3f(1, 0, 0), faceUVs[0]),
            // West (-X)
            new FaceData(new int[]{0, 4, 7, 3}, new Vector3f(-1, 0, 0), faceUVs[1]),
            // Down (-Y)
            new FaceData(new int[]{5, 4, 0, 1}, new Vector3f(0, -1, 0), faceUVs[2]),
            // Up (+Y)
            new FaceData(new int[]{2, 3, 7, 6}, new Vector3f(0, 1, 0), faceUVs[3]),
            // North (-Z)
            new FaceData(new int[]{1, 0, 3, 2}, new Vector3f(0, 0, -1), faceUVs[4]),
            // South (+Z)
            new FaceData(new int[]{4, 5, 6, 7}, new Vector3f(0, 0, 1), faceUVs[5])
        };

        // For mirror, flip all face windings
        if (mirror) {
            for (FaceData fd : faces) {
                fd.flipWinding();
            }
        }

        // Render each face twice (both windings for double-sided)
        for (FaceData fd : faces) {
            renderFace(fd, modelCorners, boneNormalMatrix, pose, consumer, packedLight, texW, texH, false);
            // Second pass with flipped winding (back face)
            FaceData flipped = fd.copyFlipped();
            renderFace(flipped, modelCorners, boneNormalMatrix, pose, consumer, packedLight, texW, texH, true);
        }
    }

    private static void renderFace(FaceData fd, Vector3f[] modelCorners,
                                   Matrix3f boneNormalMatrix, PoseStack.Pose pose,
                                   VertexConsumer consumer, int packedLight,
                                   int texW, int texH, boolean isBackFace) {

        Vector3f normal = new Vector3f(fd.normal);
        boneNormalMatrix.transform(normal);
        normal.normalize();

        // UV rect: [u1, v1, u2, v2] in texture pixels
        float[] uv = fd.uvRect;
        if (uv == null) return;

        // Convert UV to 0-1 range
        float u1 = uv[0] / (float) texW;
        float v1 = uv[1] / (float) texH;
        float u2 = uv[2] / (float) texW;
        float v2 = uv[3] / (float) texH;

        // Reference UV assignment: v0->(u2,v1), v1->(u1,v1), v2->(u1,v2), v3->(u2,v2)
        float[] uvs = {u2, v1, u1, v1, u1, v2, u2, v2};

        for (int i = 0; i < 4; i++) {
            Vector3f pos = modelCorners[fd.indices[i]];
            consumer.addVertex(pose.pose(), pos.x(), pos.y(), pos.z())
                    .setUv(uvs[i * 2], uvs[i * 2 + 1])
                    .setColor(255, 255, 255, 255)
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, normal.x(), normal.y(), normal.z());
        }
    }

    // Face UV order: East, West, Down, Up, North, South (matching reference Cube.kt)
    private static UVRect[] getFaceUVRects(BedrockGeometry.UVData uvData, float[] cubeSize,
                                           int texW, int texH, boolean mirror) {
        UVRect[] result = new UVRect[6];

        float dx = cubeSize[0];
        float dy = cubeSize[1];
        float dz = cubeSize[2];

        if (uvData.isBoxUV) {
            int u = uvData.boxU;
            int v = uvData.boxV;

            result[0] = new UVRect(u, v + (int)dz, u + (int)dz, v + (int)dz + (int)dy);
            result[1] = new UVRect(u + (int)dz + (int)dx, v + (int)dz, u + (int)dz + (int)dx + (int)dz, v + (int)dz + (int)dy);
            result[2] = new UVRect(u + (int)dz + (int)dx, v + (int)dz, u + (int)dz + (int)dx + (int)dx, v);
            result[3] = new UVRect(u + (int)dz, v, u + (int)dz + (int)dx, v + (int)dz);
            result[4] = new UVRect(u + (int)dz, v + (int)dz, u + (int)dz + (int)dx, v + (int)dz + (int)dy);
            result[5] = new UVRect(u + (int)dz + (int)dx + (int)dz, v + (int)dz, u + (int)dz + (int)dx + (int)dz + (int)dx, v + (int)dz + (int)dy);

            if (mirror) {
                UVRect tmp = result[0];
                result[0] = result[1];
                result[1] = tmp;
                for (UVRect r : result) {
                    if (r != null) r.flipU(texW);
                }
            }
        } else {
            result[0] = uvRectOrDefault(uvData.east, 0, 0, (int)dz, (int)dy, texW, texH);
            result[1] = uvRectOrDefault(uvData.west, (int)dz + (int)dx, (int)dz, (int)dz, (int)dy, texW, texH);
            result[2] = uvRectOrDefault(uvData.down, (int)dz + (int)dx, (int)dz, (int)dx, (int)dz, texW, texH);
            result[3] = uvRectOrDefault(uvData.up, (int)dz, (int)dz, (int)dx, (int)dz, texW, texH);
            result[4] = uvRectOrDefault(uvData.north, (int)dz, (int)dz, (int)dx, (int)dy, texW, texH);
            result[5] = uvRectOrDefault(uvData.south, (int)dz + (int)dx + (int)dz, (int)dz, (int)dx, (int)dy, texW, texH);

            if (mirror) {
                UVRect tmp = result[0];
                result[0] = result[1];
                result[1] = tmp;
                for (UVRect r : result) {
                    if (r != null) r.flipU(texW);
                }
            }
        }

        return result;
    }

    private static UVRect uvRectOrDefault(float[] rect, int defU, int defV, int du, int dv, int texW, int texH) {
        if (rect != null) {
            return new UVRect(rect[0], rect[1], rect[2], rect[3]);
        }
        return new UVRect(defU, defV, defU + du, defV + dv);
    }

    private static class UVRect {
        float u1, v1, u2, v2;
        UVRect(float u1, float v1, float u2, float v2) { this.u1 = u1; this.v1 = v1; this.u2 = u2; this.v2 = v2; }
        void flipU(int texW) {
            float nu1 = texW - u2;
            float nu2 = texW - u1;
            u1 = nu1; u2 = nu2;
        }
    }

    private static class FaceData {
        final int[] indices;
        final Vector3f normal;
        float[] uvRect;

        FaceData(int[] indices, Vector3f normal, UVRect uv) {
            this.indices = indices;
            this.normal = new Vector3f(normal);
            this.uvRect = uv != null ? new float[]{uv.u1, uv.v1, uv.u2, uv.v2} : null;
        }

        void flipWinding() {
            int[] ni = {indices[1], indices[0], indices[3], indices[2]};
            System.arraycopy(ni, 0, indices, 0, 4);
            normal.negate();
        }

        FaceData copyFlipped() {
            FaceData f = new FaceData(indices.clone(), normal, null);
            f.uvRect = this.uvRect != null ? this.uvRect.clone() : null;
            f.flipWinding();
            return f;
        }
    }
}
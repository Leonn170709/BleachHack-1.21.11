/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util.render;

import net.minecraft.util.math.RotationAxis;
import org.bleachhack.util.Boxes;
import org.bleachhack.util.render.color.LineColor;
import org.bleachhack.util.render.color.QuadColor;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Renderer {

	// 1.19.4 achieved "see through walls" by manually calling RenderSystem.disableDepthTest() around a
	// draw call - that method no longer exists (depth-test is baked into the RenderPipeline). These are
	// NO_DEPTH_TEST variants of vanilla's own debug-fill/lines pipelines, reusing their exact GLSL/
	// uniform declarations (position_color / rendertype_lines) so only the depth test differs.
	private static final RenderLayer NO_DEPTH_FILL = RenderLayer.of("bleachhack_no_depth_fill",
			RenderSetup.builder(RenderPipeline.builder()
					.withLocation(Identifier.of("bleachhack", "pipeline/no_depth_fill"))
					.withVertexShader(Identifier.of("minecraft", "core/position_color"))
					.withFragmentShader(Identifier.of("minecraft", "core/position_color"))
					.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.build())
					.translucent()
					.build());

	private static final RenderLayer NO_DEPTH_LINES = RenderLayer.of("bleachhack_no_depth_lines",
			RenderSetup.builder(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
					.withLocation(Identifier.of("bleachhack", "pipeline/no_depth_lines"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build())
					.build());

	// -------------------- Fill + Outline Boxes --------------------

	public static void drawBoxBoth(BlockPos blockPos, QuadColor color, float lineWidth, Direction... excludeDirs) {
		drawBoxBoth(new Box(blockPos), color, lineWidth, excludeDirs);
	}

	public static void drawBoxBoth(Box box, QuadColor color, float lineWidth, Direction... excludeDirs) {
		QuadColor outlineColor = color.clone();
		outlineColor.overwriteAlpha(255);

		drawBoxBoth(box, color, outlineColor, lineWidth, excludeDirs);
	}

	public static void drawBoxBoth(BlockPos blockPos, QuadColor fillColor, QuadColor outlineColor, float lineWidth, Direction... excludeDirs) {
		drawBoxBoth(new Box(blockPos), fillColor, outlineColor, lineWidth, excludeDirs);
	}

	public static void drawBoxBoth(Box box, QuadColor fillColor, QuadColor outlineColor, float lineWidth, Direction... excludeDirs) {
		drawBoxFill(box, fillColor, excludeDirs);
		drawBoxOutline(box, outlineColor, lineWidth, excludeDirs);
	}

	// -------------------- Fill Boxes --------------------

	public static void drawBoxFill(BlockPos blockPos, QuadColor color, Direction... excludeDirs) {
		drawBoxFill(new Box(blockPos), color, excludeDirs);
	}

	public static void drawBoxFill(Box box, QuadColor color, Direction... excludeDirs) {
		if (!FrustumUtils.isBoxVisible(box)) {
			return;
		}

		MatrixStack matrices = matrixFrom(box.minX, box.minY, box.minZ);

		RenderLayer layer = RenderLayers.DEBUG_FILLED_BOX;
		BufferBuilder buffer = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
		Vertexer.vertexBoxQuads(matrices, buffer, Boxes.moveToZero(box), color, excludeDirs);
		layer.draw(buffer.end());
	}

	/** Same as {@link #drawBoxFill}, but ignores depth test - visible through walls (ESP "Shader" mode). */
	public static void drawBoxFillThroughWalls(Box box, QuadColor color, Direction... excludeDirs) {
		MatrixStack matrices = matrixFrom(box.minX, box.minY, box.minZ);

		BufferBuilder buffer = Tessellator.getInstance().begin(NO_DEPTH_FILL.getDrawMode(), NO_DEPTH_FILL.getVertexFormat());
		Vertexer.vertexBoxQuads(matrices, buffer, Boxes.moveToZero(box), color, excludeDirs);
		NO_DEPTH_FILL.draw(buffer.end());
	}

	// -------------------- Outline Boxes --------------------

	public static void drawBoxOutline(BlockPos blockPos, QuadColor color, float lineWidth, Direction... excludeDirs) {
		drawBoxOutline(new Box(blockPos), color, lineWidth, excludeDirs);
	}

	public static void drawBoxOutline(Box box, QuadColor color, float lineWidth, Direction... excludeDirs) {
		if (!FrustumUtils.isBoxVisible(box)) {
			return;
		}

		MatrixStack matrices = matrixFrom(box.minX, box.minY, box.minZ);

		RenderLayer layer = RenderLayers.LINES;
		BufferBuilder buffer = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
		Vertexer.vertexBoxLines(matrices, buffer, Boxes.moveToZero(box), color, lineWidth, excludeDirs);
		layer.draw(buffer.end());
	}

	/** Same as {@link #drawBoxOutline}, but ignores depth test - visible through walls (ESP "Shader" mode). */
	public static void drawBoxOutlineThroughWalls(Box box, QuadColor color, float lineWidth, Direction... excludeDirs) {
		MatrixStack matrices = matrixFrom(box.minX, box.minY, box.minZ);

		BufferBuilder buffer = Tessellator.getInstance().begin(NO_DEPTH_LINES.getDrawMode(), NO_DEPTH_LINES.getVertexFormat());
		Vertexer.vertexBoxLines(matrices, buffer, Boxes.moveToZero(box), color, lineWidth, excludeDirs);
		NO_DEPTH_LINES.draw(buffer.end());
	}

	// -------------------- Quads --------------------

	public static void drawQuadFill(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int cullMode, QuadColor color) {
		if (!FrustumUtils.isPointVisible(x1, y1, z1) && !FrustumUtils.isPointVisible(x2, y2, z2)
				&& !FrustumUtils.isPointVisible(x3, y3, z3) && !FrustumUtils.isPointVisible(x4, y4, z4)) {
			return;
		}

		MatrixStack matrices = matrixFrom(x1, y1, z1);

		RenderLayer layer = RenderLayers.DEBUG_QUADS;
		BufferBuilder buffer = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
		Vertexer.vertexQuad(matrices, buffer,
				0f, 0f, 0f,
				(float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1),
				(float) (x3 - x1), (float) (y3 - y1), (float) (z3 - z1),
				(float) (x4 - x1), (float) (y4 - y1), (float) (z4 - z1),
				cullMode, color);
		layer.draw(buffer.end());
	}

	public static void drawQuadOutline(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float lineWidth, QuadColor color) {
		if (!FrustumUtils.isPointVisible(x1, y1, z1) && !FrustumUtils.isPointVisible(x2, y2, z2)
				&& !FrustumUtils.isPointVisible(x3, y3, z3) && !FrustumUtils.isPointVisible(x4, y4, z4)) {
			return;
		}

		MatrixStack matrices = matrixFrom(x1, y1, z1);
		int[] colors = color.getAllColors();

		RenderLayer layer = RenderLayers.LINES;
		BufferBuilder buffer = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
		Vertexer.vertexLine(matrices, buffer, 0f, 0f, 0f, (float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1), LineColor.gradient(colors[0], colors[1]), lineWidth);
		Vertexer.vertexLine(matrices, buffer, (float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1), (float) (x3 - x1), (float) (y3 - y1), (float) (z3 - z1), LineColor.gradient(colors[1], colors[2]), lineWidth);
		Vertexer.vertexLine(matrices, buffer, (float) (x3 - x1), (float) (y3 - y1), (float) (z3 - z1), (float) (x4 - x1), (float) (y4 - y1), (float) (z4 - z1), LineColor.gradient(colors[2], colors[3]), lineWidth);
		Vertexer.vertexLine(matrices, buffer, (float) (x4 - x1), (float) (y4 - y1), (float) (z4 - z1), 0f, 0f, 0f, LineColor.gradient(colors[3], colors[0]), lineWidth);
		layer.draw(buffer.end());
	}

	// -------------------- Lines --------------------

	public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, LineColor color, float width) {
		if (!FrustumUtils.isPointVisible(x1, y1, z1) && !FrustumUtils.isPointVisible(x2, y2, z2)) {
			return;
		}

		MatrixStack matrices = matrixFrom(x1, y1, z1);

		BufferBuilder buffer = Tessellator.getInstance().begin(NO_DEPTH_LINES.getDrawMode(), NO_DEPTH_LINES.getVertexFormat());
		Vertexer.vertexLine(matrices, buffer, 0f, 0f, 0f, (float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1), color, width);
		NO_DEPTH_LINES.draw(buffer.end());
	}

	// -------------------- Utils --------------------

	public static MatrixStack matrixFrom(double x, double y, double z) {
		MatrixStack matrices = new MatrixStack();

		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

		matrices.translate(x - camera.getCameraPos().x, y - camera.getCameraPos().y, z - camera.getCameraPos().z);

		return matrices;
	}

	public static Vec3d getInterpolationOffset(Entity e) {
		if (MinecraftClient.getInstance().isPaused()) {
			return Vec3d.ZERO;
		}

		double tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
		return new Vec3d(
				e.getX() - MathHelper.lerp(tickDelta, e.lastRenderX, e.getX()),
				e.getY() - MathHelper.lerp(tickDelta, e.lastRenderY, e.getY()),
				e.getZ() - MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ()));
	}
}

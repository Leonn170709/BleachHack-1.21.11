/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.math.RotationAxis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class WorldRenderer {

	private static final MinecraftClient mc = MinecraftClient.getInstance();

	// Shared/reused like vanilla's Tessellator buffer (1.19.4 used Tessellator.getInstance().getBuffer()
	// directly, which no longer exists in 1.21.11 - Tessellator's allocator is private now). Allocating a
	// fresh BufferAllocator per drawText call instead would leak native memory every call since
	// BufferAllocator is AutoCloseable and was never closed, which under sustained nametag rendering could
	// eventually make allocations fail and drop text mid-session.
	private static final BufferAllocator TEXT_BUFFER = new BufferAllocator(256);

	/** Draws text in the world. **/
	public static void drawText(Text text, double x, double y, double z, double scale, boolean shadow) {
		drawText(text, x, y, z, 0, 0, scale, shadow);
	}

	/** Draws text in the world. **/
	public static void drawText(Text text, double x, double y, double z, double offX, double offY, double scale, boolean fill) {
		MatrixStack matrices = matrixFrom(x, y, z);

		Camera camera = mc.gameRenderer.getCamera();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

		matrices.translate(offX, offY, 0);
		matrices.scale(-0.025f * (float) scale, -0.025f * (float) scale, 1);

		int halfWidth = mc.textRenderer.getWidth(text) / 2;

		VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(TEXT_BUFFER);

		if (fill) {
			int opacity = (int) (MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F) * 255.0F) << 24;
			mc.textRenderer.draw(text, -halfWidth, 0f, 553648127, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.NORMAL, opacity, 0xf000f0);
			immediate.draw();
		} else {
			matrices.push();
			matrices.translate(1, 1, 0);
			mc.textRenderer.draw(text.copy(), -halfWidth, 0f, 0x202020, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.NORMAL, 0, 0xf000f0);
			immediate.draw();
			matrices.pop();
		}

		// Vanilla's own entity nametags (LabelCommandRenderer) always draw a translucent SEE_THROUGH
		// copy of the text - a RenderLayer variant that ignores the depth test - before the opaque
		// NORMAL copy, so the label stays dimly visible behind terrain/foliage/other entities instead
		// of disappearing outright. This only ever drew NORMAL, so any nametag whose world position
		// happened to be behind so much as a leaf block or a fence post vanished completely instead of
		// dimming, which is what looked like "some nametags just don't render".
		mc.textRenderer.draw(text, -halfWidth, 0f, -2130706433, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
		immediate.draw();

		mc.textRenderer.draw(text, -halfWidth, 0f, -1, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.NORMAL, 0, 0xf000f0);
		immediate.draw();
	}

	// 1.21.11: item rendering moved to the same RenderState/OrderedRenderCommandQueue architecture as
	// entities and block entities (see task #3/#4 notes) - ItemRenderState.render(...) and every
	// HeldItemRenderer/ItemRenderer entry point require an OrderedRenderCommandQueue, which is only
	// ever handed out by vanilla's own per-frame render dispatch, not obtainable standalone. There is
	// no accessible way left to render an arbitrary ItemStack immediately outside that callback, so
	// this floating 3D item icon can't be preserved - callers (Nametags/AutoSteal) keep their
	// surrounding text (name/count) via drawText, just without the item icon itself.
	/** Draws a 2D gui items somewhere in the world. **/
	public static void drawGuiItem(double x, double y, double z, double offX, double offY, double scale, ItemStack item) {
	}

	public static MatrixStack matrixFrom(double x, double y, double z) {
		MatrixStack matrices = new MatrixStack();

		Camera camera = mc.gameRenderer.getCamera();
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

		matrices.translate(x - camera.getCameraPos().x, y - camera.getCameraPos().y, z - camera.getCameraPos().z);

		return matrices;
	}
}

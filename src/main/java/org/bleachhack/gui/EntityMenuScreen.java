/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.MathHelper;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.EntityMenu;
import org.bleachhack.util.BleachLogger;
import org.bleachhack.util.Boxes;
import org.bleachhack.util.collections.MutablePairList;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="https://github.com/lasnikprogram">Lasnik</a>
 */
public class EntityMenuScreen extends Screen {

	private LivingEntity entity;
	private String focusedString;
	private int crosshairX, crosshairY, focusedDot = -1;
	private float yaw, pitch;

	public EntityMenuScreen(LivingEntity entity) {
		super(Text.literal("Interaction Screen"));
		this.entity = entity;
	}

	public void init() {
		super.init();
		this.cursorMode(GLFW.GLFW_CURSOR_HIDDEN);
		yaw = client.player.getYaw();
		pitch = client.player.getPitch();
	}

	private void cursorMode(int mode) {
		double x = this.client.getWindow().getWidth() / 2d;
		double y = this.client.getWindow().getHeight() / 2d;

		KeyBinding.unpressAll();
		InputUtil.setCursorParameters(this.client.getWindow(), GLFW.GLFW_CURSOR_HIDDEN, x, y);
	}

	public void tick() {
		if (GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(),
				GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_RELEASE) {
			close();
		}
	}

	public void close() {
		cursorMode(GLFW.GLFW_CURSOR_NORMAL);

		// This makes the magic
		if (focusedString != null) {
			DecimalFormat coordFormat = new DecimalFormat("#.##");

			String message = ModuleManager.getModule(EntityMenu.class)
					.interactions.getValue(focusedString)
					.replaceAll("%name%", entity.getDisplayName().getString())
					.replaceAll("%uuid%", entity.getUuidAsString())
					.replaceAll("%health%", String.valueOf((int) entity.getHealth()))
					.replaceAll("%x%", coordFormat.format(entity.getX()))
					.replaceAll("%y%", coordFormat.format(entity.getY()))
					.replaceAll("%z%", coordFormat.format(entity.getZ()));

			if (message.startsWith(">suggest ")) {
				client.setScreen(new ChatScreen(message.substring(9), false));
			} else if (message.startsWith(">url ")) {
				try {
					Util.getOperatingSystem().open(new URI(message.substring(5)));
				} catch (Exception e) {
					BleachLogger.error("Invalid url \"" + message.substring(5) + "\"");
				}

				client.setScreen(null);
			} else {
				client.player.networkHandler.sendChatMessage(message);
				client.setScreen(null);
			}
		} else {
			client.setScreen(null);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// The crosshair used vanilla's GUI icon atlas via an implicit bound-texture draw + a manual
	// inverting blend function; both moved into RenderPipelines.CROSSHAIR + a dedicated texture
	// Identifier (see InGameHud's own crosshair render code), so the manual GlStateManager blend
	// setup is no longer needed - the pipeline bakes it in.
	private static final Identifier CROSSHAIR_TEXTURE = Identifier.ofVanilla("hud/crosshair");

	public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
		// Draw entity
		int entitySize = (int) (120 / Boxes.getCornerLength(entity.getBoundingBox()));
		int entityHeight = entitySize / 2 - (int) (10 / Boxes.getAxisLength(entity.getBoundingBox(), Axis.Y));
		int cx = width / 2;
		int cy = height / 2 + entityHeight;
		InventoryScreen.drawEntity(matrices,
				cx - entitySize, cy - entitySize, cx + entitySize, cy + entitySize,
				entitySize, 0.0625f, mouseX, mouseY,
				entity);

		// Fake crosshair
		matrices.drawGuiTexture(RenderPipelines.CROSSHAIR, CROSSHAIR_TEXTURE, crosshairX - 8, crosshairY - 8, 15, 15);

		drawDots(matrices, (int) (Math.min(height, width) / 2 * 0.75), mouseX, mouseY);

		matrices.getMatrices().pushMatrix();
		matrices.getMatrices().scale(2.5f, 2.5f);
		matrices.drawCenteredTextWithShadow(textRenderer, entity.getDisplayName().getString() /*"Interaction Screen"*/, width / 5, 5, 0xFFFFFFFF);
		matrices.getMatrices().popMatrix();

		Vector2 center = new Vector2(width / 2, height / 2);
		Vector2 mouse = new Vector2(mouseX, mouseY).subtract(center).normalize();

		int scale = Math.max(1, client.options.getGuiScale().getValue());

		// Move crossHair based on distance between mouse and center. But with limit
		float hypot = (float) Math.hypot(width / 2 - mouseX, height / 2 - mouseY);
		mouse.multiply(Math.min(hypot, 1f) / scale * 200f);

		this.crosshairX = (int) mouse.x + width / 2;
		this.crosshairY = (int) mouse.y + height / 2;

		client.player.setYaw(yaw + mouse.x / 3);
		client.player.setPitch(MathHelper.clamp(pitch + mouse.y / 3, -90f, 90f));
		super.render(matrices, mouseX, mouseY, delta);
	}

	private void drawDots(DrawContext matrices, int radius, int mouseX, int mouseY) {
		MutablePairList<String, String> map = ModuleManager.getModule(EntityMenu.class).interactions;
		List<Vector2> pointList = new ArrayList<>();
		String[] cache = new String[map.size()];

		int i = 0;
		double lowestDistance = Double.MAX_VALUE;
		for (String string: map.getEntries()) {
			// Just some fancy calculations to get the positions of the dots
			double s = (double) i / map.size() * 2 * Math.PI;
			int x = (int) Math.round(radius * Math.cos(s) + width / 2);
			int y = (int) Math.round(radius * Math.sin(s) + height / 2);
			drawTextField(matrices, x, y, string);

			// Calculate lowest distance between mouse and dot
			if (Math.hypot(x - mouseX, y - mouseY) < lowestDistance) {
				lowestDistance = Math.hypot(x - mouseX, y - mouseY);
				focusedDot = i;
			}

			cache[i] = string;
			pointList.add(new Vector2(x, y));
			i++;
		}

		// Go through all point and if it is focused -> drawing different color, changing closest string value
		for (Vector2 point: pointList) {
			if (pointList.get(focusedDot).equals(point)) {
				drawDot(matrices, (int) point.x, (int) point.y, 0xFF4CFF00);
				this.focusedString = cache[focusedDot];
			} else {
				drawDot(matrices, (int) point.x, (int) point.y, 0xFF0094FF);
			}
		}
	}

	private void drawRect(DrawContext matrices, int startX, int startY, int width, int height, int colorInner,int colorOuter) {
		matrices.drawHorizontalLine(startX, startX + width, startY, colorOuter);
		matrices.drawHorizontalLine(startX, startX + width, startY + height, colorOuter);
		matrices.drawVerticalLine(startX, startY, startY + height, colorOuter);
		matrices.drawVerticalLine(startX + width, startY, startY + height, colorOuter);
		matrices.fill(startX + 1, startY + 1, startX + width, startY + height, colorInner);
	}

	private void drawTextField(DrawContext matrices, int x, int y, String text) {
		if (x >= width / 2) {
			drawRect(matrices, x + 10, y - 8, textRenderer.getWidth(text) + 3, 15, 0x80808080, 0xFF000000);
			matrices.drawTextWithShadow(textRenderer, text, x + 12, y - 4, 0xFFFFFFFF);
		} else {
			drawRect(matrices, x - 14 - textRenderer.getWidth(text), y - 8, textRenderer.getWidth(text) + 3, 15, 0x80808080, 0xFF000000);
			matrices.drawTextWithShadow(textRenderer, text, x - 12 - textRenderer.getWidth(text), y - 4, 0xFFFFFFFF);
		}
	}

	// Literally drawing it in code
	private void drawDot(DrawContext matrices, int centerX, int centerY, int colorInner) {
		// Black background
		matrices.fill(centerX - 1, centerY - 5, centerX + 2, centerY + 6, 0xff000000);
		matrices.fill(centerX - 3, centerY - 4, centerX + 4, centerY + 5, 0xff000000);
		matrices.fill(centerX - 4, centerY - 3, centerX + 5, centerY + 4, 0xff000000);
		matrices.fill(centerX - 5, centerY - 1, centerX + 6, centerY + 2, 0xff000000);

		// Fill
		matrices.fill(centerX - 1, centerY - 4, centerX + 2, centerY + 5, colorInner);
		matrices.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4, colorInner);
		matrices.fill(centerX - 4, centerY - 1, centerX + 5, centerY + 2, colorInner);

		// Light overlay
		matrices.fill(centerX - 1, centerY - 3, centerX + 1, centerY - 2, 0x80ffffff);
		matrices.fill(centerX - 2, centerY - 2, centerX - 1, centerY - 1, 0x80ffffff);
		//fill(matrix, centerX - 3, centerY - 1, centerX - 2, centerY, 0x80ffffff);
	}
}


// Creating my own Vector class beacause I couldn't find a good one in minecrafts code
class Vector2 {

	public final float x, y;

	public Vector2(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public Vector2 normalize() {
		float mag = getMag();

		if (mag == 0 || mag == 1)
			return this;

		return divide(mag);
	}

	public Vector2 subtract(Vector2 vec) {
		return new Vector2(this.x - vec.x, this.y - vec.y);
	}

	public Vector2 divide(float n) {
		return new Vector2(this.x / n, this.y / n);
	}

	public Vector2 multiply(float n) {
		return new Vector2(this.x * n, this.y * n);
	}

	private float getMag() {
		return (float) Math.sqrt(x * x + y * y);
	}

	@Override
	public int hashCode() {
		int result = 1;
		result = 31 * result + Float.floatToIntBits(x);
		result = 31 * result + Float.floatToIntBits(y);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;

		Vector2 other = (Vector2) obj;
		return Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
				&& Float.floatToIntBits(y) == Float.floatToIntBits(other.y);
	}
}

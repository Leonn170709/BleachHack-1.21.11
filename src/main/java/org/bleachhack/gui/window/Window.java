/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui.window;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.bleachhack.gui.window.widget.WindowWidget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.ColorHelper;

public class Window {

	public int x1;
	public int y1;
	public int x2;
	public int y2;

	public String title;
	public ItemStack icon;

	public boolean closed;
	public boolean selected = false;

	private List<WindowWidget> widgets = new ArrayList<>();

	protected boolean dragging;
	protected int dragOffX;
	protected int dragOffY;

	public Window(int x1, int y1, int x2, int y2, String title, ItemStack icon) {
		this(x1, y1, x2, y2, title, icon, false);
	}

	public Window(int x1, int y1, int x2, int y2, String title, ItemStack icon, boolean closed) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.title = title;
		this.icon = icon;
		this.closed = closed;
	}

	public List<WindowWidget> getWidgets() {
		return widgets;
	}

	public <T extends WindowWidget> T addWidget(T widget) {
		widgets.add(widget);
		return widget;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRend = MinecraftClient.getInstance().textRenderer;

		if (dragging) {
			x2 = (x2 - x1) + mouseX - dragOffX - Math.min(0, mouseX - dragOffX);
			y2 = (y2 - y1) + mouseY - dragOffY - Math.min(0, mouseY - dragOffY);
			x1 = Math.max(0, mouseX - dragOffX);
			y1 = Math.max(0, mouseY - dragOffY);
		}

		drawBackground(context, mouseX, mouseY, textRend);

		for (WindowWidget w : widgets) {
			if (w.shouldRender(x1, y1, x2, y2)) {
				w.render(context, x1, y1, mouseX, mouseY);
			}
		}

		boolean blockItem = icon != null && icon.getItem() instanceof BlockItem;

		/* window icon */
		if (icon != null) {
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(x1 + (blockItem ? 3 : 2), y1 + 2);
			context.getMatrices().scale(0.6f, 0.6f);

			context.drawItem(icon, 0, 0);

			context.getMatrices().popMatrix();
		}

		/* window title */
		context.drawTextWithShadow(textRend, title,
				x1 + (icon == null || icon.getItem() == Items.AIR ? 4 : (blockItem ? 15 : 14)), y1 + 3, -1);
	}

	protected void drawBackground(DrawContext context, int mouseX, int mouseY, TextRenderer textRend) {
		/* background */
		context.fill(x1, y1 + 1, x1 + 1, y2 - 1, 0xff6060b0);
		horizontalGradient(context, x1 + 1, y1, x2 - 1, y1 + 1, 0xff6060b0, 0xff8070b0);
		context.fill(x2 - 1, y1 + 1, x2, y2 - 1, 0xff8070b0);
		horizontalGradient(context, x1 + 1, y2 - 1, x2 - 1, y2, 0xff6060b0, 0xff8070b0);

		context.fill(x1 + 1, y1 + 12, x2 - 1, y2 - 1, 0x90606090);

		/* title bar */
		horizontalGradient(context, x1 + 1, y1 + 1, x2 - 1, y1 + 12, (selected ? 0xff6060b0 : 0xff606060), (selected ? 0xff8070b0 : 0xffa0a0a0));

		/* buttons */
		context.drawText(textRend, "x", x2 - 10, y1 + 3, 0, false);
		context.drawText(textRend, "x", x2 - 11, y1 + 2, -1, false);

		context.drawText(textRend, "_", x2 - 21, y1 + 2, 0, false);
		context.drawText(textRend, "_", x2 - 22, y1 + 1, -1, false);
	}

	public boolean shouldClose(int mouseX, int mouseY) {
		return selected && mouseX > x2 - 23 && mouseX < x2 && mouseY > y1 + 2 && mouseY < y1 + 12;
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX >= x1 && mouseX <= x2 - 2 && mouseY >= y1 && mouseY <= y1 + 11) {
			dragging = true;
			dragOffX = (int) mouseX - x1;
			dragOffY = (int) mouseY - y1;
		}

		if (selected) {
			try {
				for (WindowWidget w : widgets) {
					if (w.shouldRender(x1, y1, x2, y2)) {
						w.mouseClicked(x1, y1, (int) mouseX, (int) mouseY, button);
					}
				}
			} catch (ConcurrentModificationException ignored) {}
		}
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;

		if (selected) {
			for (WindowWidget w : widgets) {
				if (w.shouldRender(x1, y1, x2, y2)) {
					w.mouseReleased(x1, y1, (int) mouseX, (int) mouseY, button);
				}
			}
		}
	}

	public void tick() {
		for (WindowWidget w : widgets) {
			w.tick();
		}
	}

	public void charTyped(char chr, int modifiers) {
		if (selected) {
			for (WindowWidget w : widgets) {
				w.charTyped(chr, modifiers);
			}
		}
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if (selected) {
			for (WindowWidget w : widgets) {
				w.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}

	public static void fill(DrawContext context, int x1, int y1, int x2, int y2) {
		fill(context, x1, y1, x2, y2, 0xff6060b0, 0xff8070b0, 0x00000000);
	}

	public static void fill(DrawContext context, int x1, int y1, int x2, int y2, int fill) {
		fill(context, x1, y1, x2, y2, 0xff6060b0, 0xff8070b0, fill);
	}

	public static void fill(DrawContext context, int x1, int y1, int x2, int y2, int colTop, int colBot, int colFill) {
		context.fill(x1, y1 + 1, x1 + 1, y2 - 1, colTop);
		context.fill(x1 + 1, y1, x2 - 1, y1 + 1, colTop);
		context.fill(x2 - 1, y1 + 1, x2, y2 - 1, colBot);
		context.fill(x1 + 1, y2 - 1, x2 - 1, y2, colBot);
		context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, colFill);
	}

	// 1.21.11's DrawContext.fillGradient(...) only interpolates vertically (top row always gets
	// colorStart, bottom row colorEnd - see ColoredQuadGuiElementRenderState), with no horizontal
	// equivalent. This reimplements a left-to-right gradient as a column-by-column fill using only
	// the stable public DrawContext.fill/ColorHelper.lerp API, rather than authoring a custom
	// GuiElementRenderState against DrawContext's internal (and likely still-shifting) render queue.
	public static void horizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int color1, int color2) {
		int width = x2 - x1;
		if (width <= 0) {
			return;
		}

		for (int i = 0; i < width; i++) {
			float t = width <= 1 ? 0f : (float) i / (width - 1);
			context.fill(x1 + i, y1, x1 + i + 1, y2, ColorHelper.lerp(t, color1, color2));
		}
	}

	public static void verticalGradient(DrawContext context, int x1, int y1, int x2, int y2, int color1, int color2) {
		context.fillGradient(x1, y1, x2, y2, color1, color2);
	}
}

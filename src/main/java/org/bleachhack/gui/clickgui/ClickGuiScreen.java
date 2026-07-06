/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui.clickgui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.bleachhack.gui.clickgui.window.ClickGuiWindow;
import org.bleachhack.gui.clickgui.window.ClickGuiWindow.Tooltip;
import org.bleachhack.gui.window.Window;
import org.bleachhack.gui.window.WindowScreen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public abstract class ClickGuiScreen extends WindowScreen {

	protected int keyDown = -1;
	protected boolean lmDown = false;
	protected boolean rmDown = false;
	protected boolean lmHeld = false;
	protected int mwScroll = 0;
	
	private int warningOpacity;

	public ClickGuiScreen(Text title) {
		super(title);
	}
	
	@Override
	public void init() {
		// super.init(); Don't call super because it clears the windows

		warningOpacity = 0;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
		// Screen.renderWithTooltip() now calls renderBackground() once itself before render() runs
		// (1.21.11) - calling it again here throws "Can only blur once per frame".
		for (Window w : getWindows()) {
			if (w instanceof ClickGuiWindow) {
				((ClickGuiWindow) w).updateKeys(mouseX, mouseY, keyDown, lmDown, rmDown, lmHeld, mwScroll);
			}
		}

		super.render(matrices, mouseX, mouseY, delta);

		for (Window w : getWindows()) {
			if (w instanceof ClickGuiWindow) {
				Tooltip tooltip = ((ClickGuiWindow) w).getTooltip();

				if (tooltip != null) {
					String[] split = tooltip.text.split("\n", -1 /* Adding -1 makes it keep empty splits */);
					ArrayUtils.reverse(split);

					List<List<String>> segments = new ArrayList<>();
					int maxLineWidth = 0;

					for (String s: split) {
						/* Match lines to end of words after it reaches 22 characters long */
						Matcher mat = Pattern.compile(".{1,22}\\b\\W*").matcher(s);

						List<String> lines = new ArrayList<>();

						while (mat.find())
							lines.add(mat.group().trim());

						if (lines.isEmpty())
							lines.add(s);

						for (String line: lines)
							maxLineWidth = Math.max(maxLineWidth, textRenderer.getWidth(line));

						segments.add(lines);
					}

					// Flip the tooltip to the left of its anchor point instead of letting it run off the
					// right edge of the screen (tooltip.x is normally just right of the setting row).
					int boxX = tooltip.x;
					if (boxX + maxLineWidth + 3 > width) {
						boxX = Math.max(0, tooltip.x - maxLineWidth - 9);
					}

					int tooltipY = tooltip.y;
					for (List<String> lines: segments) {
						int start = tooltipY - lines.size() * 10;
						for (int l = 0; l < lines.size(); l++) {
							matrices.fill(boxX, start + (l * 10) - 1,
									boxX + textRenderer.getWidth(lines.get(l)) + 3,
									start + (l * 10) + 9, 0xff000000);

							matrices.drawTextWithShadow(textRenderer, lines.get(l), boxX + 2, start + (l * 10), -1);
						}

						tooltipY -= lines.size() * 10;
					}
				}
			}
		}

		Window.fill(matrices, width / 2 - 50, -1, width / 2 - 2, 12,
				mouseX >= width / 2 - 50 && mouseX <= width / 2 - 2 && mouseY >= 0 && mouseY <= 12 ? 0x60b070f0 : 0x60606090);
		Window.fill(matrices, width / 2 + 2, -1, width / 2 + 50, 12,
				mouseX >= width / 2 + 2 && mouseX <= width / 2 + 50 && mouseY >= 0 && mouseY <= 12 ? 0x60b070f0 : 0x60606090);

		matrices.drawCenteredTextWithShadow(textRenderer, "Modules", width / 2 - 26, 2, 0xf0f0f0);
		matrices.drawCenteredTextWithShadow(textRenderer, "UI", width / 2 + 26, 2, 0xf0f0f0);

		if (warningOpacity > 3) {
			matrices.drawCenteredTextWithShadow(textRenderer, "UI not available on the main menu!", width / 2, 17,
					warningOpacity > 255 ? 0xd14a3b : (warningOpacity << 24) | 0xd14a3b);
			warningOpacity -= 3;
		}

		lmDown = false;
		rmDown = false;
		keyDown = -1;
		mwScroll = 0;
	}

	public boolean mouseClicked(Click click, boolean doubleClick) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();

		if (button == 0) {
			if (mouseX >= width / 2 - 50 && mouseX <= width / 2 - 2 && mouseY >= 0 && mouseY <= 12) {
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1f));
				tryOpen(ModuleClickGuiScreen.INSTANCE);
			} else if (mouseX >= width / 2 + 2 && mouseX <= width / 2 + 50 && mouseY >= 0 && mouseY <= 12) {
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1f));
				tryOpen(UIClickGuiScreen.INSTANCE);
			} else {
				lmDown = true;
				lmHeld = true;
			}
		} else if (button == 1) {
			rmDown = true;
		}

		return super.mouseClicked(click, doubleClick);
	}

	public boolean mouseReleased(Click click) {
		if (click.button() == 0)
			lmHeld = false;
		return super.mouseReleased(click);
	}

	public boolean keyPressed(KeyInput input) {
		keyDown = input.key();
		return super.keyPressed(input);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		mwScroll = (int) verticalAmount;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
	
	private void tryOpen(Screen screen) {
		if (client.world != null) {
			client.setScreen(screen);
		} else {
			warningOpacity = 500;
		}
	}
}

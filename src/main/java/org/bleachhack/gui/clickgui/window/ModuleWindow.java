/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui.clickgui.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.ClickGui;
import org.bleachhack.setting.module.ModuleSetting;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;

public class ModuleWindow extends ClickGuiWindow {

	public List<Module> modList = new ArrayList<>();
	public LinkedHashMap<Module, Boolean> mods = new LinkedHashMap<>();

	private int len;

	private Set<Module> searchedModules;

	private Tooltip tooltip = null;

	// how far the module list is scrolled down, in pixels; clamped to [0, content height - visible height]
	private int scrollOffset = 0;

	public ModuleWindow(List<Module> mods, int x1, int y1, int len, String title, ItemStack icon) {
		super(x1, y1, x1 + len, 0, title, icon);

		this.len = len;
		modList = mods;

		for (Module m : mods)
			this.mods.put(m, false);

		y2 = getHeight();
	}

	public void render(DrawContext matrices, int mouseX, int mouseY) {
		tooltip = null;
		int x = x1 + 1;
		int y = y1 + 13;
		x2 = x + len + 1;

		int contentHeight = getHeight();
		// leave a small margin so the window never quite touches the bottom of the screen
		int visibleHeight = hiding ? 0 : Math.min(contentHeight, Math.max(12, mc.getWindow().getScaledHeight() - y - 4));
		y2 = hiding ? y1 + 13 : y1 + 13 + visibleHeight;

		boolean scrollable = !hiding && contentHeight > visibleHeight;
		if (scrollable && mwScroll != 0 && mouseOver(x1, y1, x2, y2)) {
			scrollOffset -= mwScroll * 12;
		}
		scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, contentHeight - visibleHeight)));

		super.render(matrices, mouseX, mouseY);

		if (hiding) return;

		TextRenderer textRend = mc.textRenderer;

		if (scrollable) {
			matrices.enableScissor(x, y, x + len, y + visibleHeight);
		}

		int curY = -scrollOffset;
		for (Entry<Module, Boolean> m : mods.entrySet()) {
			if (curY + 12 > 0 && curY < visibleHeight) {
				if (mouseOver(x, y + curY, x + len, y + 12 + curY)) {
					matrices.fill(x, y + curY, x + len, y + 12 + curY, 0x70303070);
				}

				// If they match: Module gets marked red
				if (searchedModules != null && searchedModules.contains(m.getKey()) && ModuleManager.getModule(ClickGui.class).getSetting(1).asToggle().getState()) {
					matrices.fill(x, y + curY, x + len, y + 12 + curY, 0x50ff0000);
				}

				matrices.drawTextWithShadow(textRend, textRend.trimToWidth(m.getKey().getName(), len),
						x + 2, y + 2 + curY, m.getKey().isEnabled() ? 0x70efe0 : 0xc0c0c0);

				// Set which module settings show on
				if (mouseOver(x, y + curY, x + len, y + 12 + curY)) {
					tooltip = new Tooltip(x + len + 2, y + curY, m.getKey().getDesc());

					if (lmDown)
						m.getKey().toggle();
					if (rmDown)
						mods.replace(m.getKey(), !m.getValue());
					if (lmDown || rmDown)
						mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				}
			}

			curY += 12;

			// draw settings
			if (m.getValue()) {
				for (ModuleSetting<?> s : m.getKey().getSettings()) {
					if (!s.isVisible()) {
						continue;
					}

					int settingHeight = s.getHeight(len);

					if (curY + settingHeight > 0 && curY < visibleHeight) {
						s.render(this, matrices, x + 1, y + curY, len - 1);

						if (!s.getTooltip().isEmpty() && mouseOver(x + 2, y + curY, x + len, y + settingHeight + curY)) {
							tooltip = s.getTooltip(this, x + 1, y + curY, len - 1);
						}

						matrices.fill(x + 1, y + curY, x + 2, y + curY + settingHeight, 0xff8070b0);
					}

					curY += settingHeight;
				}
			}
		}

		if (scrollable) {
			matrices.disableScissor();
		}
	}

	public Tooltip getTooltip() {
		return tooltip;
	}

	public void setSearchedModule(Set<Module> mods) {
		searchedModules = mods;
	}

	public void setLen(int len) {
		this.len = len;
	}

	public int getHeight() {
		int h = 1;
		for (Entry<Module, Boolean> e : mods.entrySet()) {
			h += 12;

			if (e.getValue()) {
				for (ModuleSetting<?> s : e.getKey().getSettings()) {
					if (s.isVisible()) {
						h += s.getHeight(len);
					}
				}
			}
		}

		return h;
	}
}

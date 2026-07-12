/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui.clickgui;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.bleachhack.BleachHack;
import org.bleachhack.command.Command;
import org.bleachhack.gui.clickgui.window.ClickGuiWindow;
import org.bleachhack.gui.clickgui.window.ModuleWindow;
import org.bleachhack.gui.window.Window;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.ClickGui;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.io.BleachFileHelper;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;


public class ModuleClickGuiScreen extends ClickGuiScreen {
	
	public static ModuleClickGuiScreen INSTANCE = new ModuleClickGuiScreen();

	private TextFieldWidget searchField;

	// True while the search bar is only open because Ctrl+F opened it, i.e. the "Search bar" setting
	// itself is off. It gets closed again as soon as the search is over (field loses focus / gui
	// closes) instead of staying open, which would silently flip the setting on.
	private boolean temporarySearch;

	public ModuleClickGuiScreen() {
		super(Text.literal("ClickGui"));
	}

	public void init() {
		super.init();

		searchField = new TextFieldWidget(textRenderer, 2, 14, 100, 12, Text.empty() /* @LasnikProgram is author lol */);
		searchField.visible = false;
		searchField.setMaxLength(20);
		searchField.setSuggestion("Search here");
		addDrawableChild(searchField);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_F && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			// Ctrl+F always means "let me search", never "hide the search bar" - so show it if it's
			// hidden and jump straight into typing either way. Focus has to be set on the screen
			// (not just the widget) or charTyped() is never routed to the field and typing is eaten.
			SettingToggle searchBar = ModuleManager.getModule(ClickGui.class).getSetting(1).asToggle();
			if (!searchBar.getState()) {
				searchBar.setValue(true);
				temporarySearch = true;
			}

			searchField.visible = true;
			setFocused(searchField);
			searchField.setFocused(true);
			return true;
		}

		return super.keyPressed(input);
	}

	private void endTemporarySearch() {
		temporarySearch = false;
		searchField.setText("");
		searchField.visible = false;
		ModuleManager.getModule(ClickGui.class).getSetting(1).asToggle().setValue(false);

		for (Window w : getWindows()) {
			if (w instanceof ModuleWindow) {
				((ModuleWindow) w).setSearchedModule(new HashSet<>());
			}
		}
	}

	@Override
	public void removed() {
		if (temporarySearch) {
			endTemporarySearch();
		}

		super.removed();
	}

	@Override
	public float getScale() {
		ClickGui clickGui = ModuleManager.getModule(ClickGui.class);

		float manual = clickGui.getSetting(3).asSlider().getValueInt() / 100f;
		return clickGui.getSetting(4).asToggle().getState() ? computeAutoFitScale() * manual : manual;
	}

	// Minecraft's GUI Scale changes how many virtual pixels fit on screen, which otherwise makes
	// the clickgui visibly bigger/smaller as you change it (same virtual size, different real
	// size). Countering that 1:1 keeps the clickgui's real on-screen size constant regardless of
	// GUI Scale - 2 is the reference factor (matches the manual Scale slider's 100% default on a
	// typical GUI Scale of 2).
	private float computeAutoFitScale() {
		int factor = Math.max(1, client.getWindow().getScaleFactor());
		return Math.max(0.5f, Math.min(2f, 2f / factor));
	}

	public void initWindows() {
		int len = ModuleManager.getModule(ClickGui.class).getSetting(0).asSlider().getValueInt();

		// Wrap into a new column instead of running the category tabs off the bottom of the
		// screen - windows are user-draggable and their positions get saved, so a scroll offset
		// that keeps repositioning them every frame would fight (and undo) that.
		int screenHeight = (int) (client.getWindow().getScaledHeight() / Math.max(getScale(), 0.01f));
		int maxRows = Math.max(1, (screenHeight - 50) / 16);

		int col = 0;
		int row = 0;
		for (ModuleCategory c: ModuleCategory.values()) {
			int x = 30 + col * (len + 10);
			int y = 50 + row * 16;
			addWindow(new ModuleWindow(ModuleManager.getModulesInCat(c), x, y, len, StringUtils.capitalize(c.name().toLowerCase()), c.getItem()));

			row++;
			if (row >= maxRows) {
				row = 0;
				col++;
			}
		}

		for (Window w: getWindows()) {
			if (w instanceof ClickGuiWindow) {
				((ClickGuiWindow) w).hiding = true;
			}
		}
	}

	public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
		BleachFileHelper.SCHEDULE_SAVE_CLICKGUI.set(true);
		ClickGui clickGui = ModuleManager.getModule(ClickGui.class);

		// Clicking anything else (a module, a window) unfocuses the field - that's the search being
		// over, so a Ctrl+F-opened bar goes away again.
		if (temporarySearch && !searchField.isFocused()) {
			endTemporarySearch();
		}

		searchField.visible = clickGui.getSetting(1).asToggle().getState();

		if (clickGui.getSetting(1).asToggle().getState()) {
			searchField.setSuggestion(searchField.getText().isEmpty() ? "Search here" : "");

			Set<Module> seachMods = new HashSet<>();
			if (!searchField.getText().isEmpty()) {
				for (Module m : ModuleManager.getModules()) {
					if (m.getName().toLowerCase(Locale.ENGLISH).contains(searchField.getText().toLowerCase(Locale.ENGLISH).replace(" ", ""))) {
						seachMods.add(m);
					}
				}
			}

			for (Window w : getWindows()) {
				if (w instanceof ModuleWindow) {
					((ModuleWindow) w).setSearchedModule(seachMods);
				}
			}
		}

		int len = clickGui.getSetting(0).asSlider().getValueInt();
		for (Window w : getWindows()) {
			if (w instanceof ModuleWindow) {
				((ModuleWindow) w).setLen(len);
			}
		}

		super.render(matrices, mouseX, mouseY, delta);

		matrices.drawText(textRenderer, "BleachHack-" + BleachHack.VERSION + "-" + SharedConstants.getGameVersion().name(), 3, 3, 0x305090, false);
		matrices.drawText(textRenderer, "BleachHack-" + BleachHack.VERSION + "-" + SharedConstants.getGameVersion().name(), 2, 2, 0x6090d0, false);

		if (clickGui.getSetting(2).asToggle().getState()) {
			matrices.drawTextWithShadow(textRenderer, "Current prefix is: \"" + Command.getPrefix() + "\" (" + Command.getPrefix() + "help)", 2, height - 20, 0x99ff99);
			matrices.drawTextWithShadow(textRenderer, "Use " + Command.getPrefix() + "clickgui to reset the clickgui", 2, height - 10, 0x9999ff);
		}
	}
}

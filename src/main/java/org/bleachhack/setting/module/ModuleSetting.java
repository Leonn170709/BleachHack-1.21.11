/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.setting.module;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bleachhack.gui.clickgui.window.ClickGuiWindow.Tooltip;
import org.bleachhack.setting.Setting;
import org.bleachhack.setting.SettingDataHandler;
import org.bleachhack.gui.clickgui.window.ModuleWindow;
import org.bleachhack.util.io.BleachFileHelper;

import net.minecraft.client.gui.DrawContext;

public abstract class ModuleSetting<T> extends Setting<T> {

	public ModuleSetting(String name, T value, SettingDataHandler<T> handler) {
		super(name, "", value, handler);
	}
	
	public ModuleSetting(String name, T value, UnaryOperator<T> defaultValue, SettingDataHandler<T> handler) {
		super(name, "", value, defaultValue, handler);
	}

	public SettingMode asMode() {
		try {
			return (SettingMode) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}

	public SettingToggle asToggle() {
		try {
			return (SettingToggle) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}

	public SettingSlider asSlider() {
		try {
			return (SettingSlider) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}

	public SettingColor asColor() {
		try {
			return (SettingColor) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}
	
	public SettingButton asButton() {
		try {
			return (SettingButton) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}
	
	@SuppressWarnings("unchecked")
	public <E> SettingList<E> asList(Class<E> itemClass) {
		try {
			return (SettingList<E>) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}

	public SettingRotate asRotate() {
		try {
			return (SettingRotate) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}
	
	public SettingKey asBind() {
		try {
			return (SettingKey) this;
		} catch (Exception e) {
			throw new ClassCastException("Execption parsing setting: " + this);
		}
	}

	public Tooltip getTooltip(ModuleWindow window, int x, int y, int len) {
		return new Tooltip(x + len + 2, y, getTooltip());
	}

	// Lets a setting hide itself depending on another setting in the same module (e.g. a slider
	// that's meaningless while a particular SettingMode option is selected), mirroring Meteor's
	// per-setting .visible(...) predicate. Defaults to always-visible so existing settings don't
	// need to opt in. ModuleWindow skips both layout height and rendering for hidden settings.
	private Supplier<Boolean> visiblePredicate = () -> true;

	public boolean isVisible() {
		return visiblePredicate.get();
	}

	public ModuleSetting<T> visibleWhen(Supplier<Boolean> visiblePredicate) {
		this.visiblePredicate = visiblePredicate;
		return this;
	}

	@Override
	public void setValue(T value) {
		super.setValue(value);
		BleachFileHelper.SCHEDULE_SAVE_MODULES.set(true);
	}

	public abstract void render(ModuleWindow window, DrawContext matrices, int x, int y, int len);

	public abstract int getHeight(int len);
}

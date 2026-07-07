
/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingSlider;

public class Zoom extends Module {

	public double prevSens;

	public Zoom() {
		super("Zoom", KEY_UNBOUND, ModuleCategory.RENDER, "Zooms in your view.",
				new SettingSlider("Scale", 1, 10, 3, 2).withDesc("How much to zoom."));
	}

	public float getScale() {
		return getSetting(0).asSlider().getValueFloat();
	}

	@Override
	public void onEnable(boolean inWorld) {
		super.onEnable(inWorld);

		// FOV itself is no longer touched here - writing straight into GameOptions.getFov()
		// persisted the zoomed-in value into the options screen/options.txt (and any bug in
		// restoring it on disable left the player stuck there), so this now matches Meteor's Zoom:
		// MixinGameRenderer divides the *computed* per-frame FOV on the fly (see bleachhack_zoomFov)
		// instead, leaving the real option untouched.
		prevSens = mc.options.getMouseSensitivity().getValue();
		mc.options.getMouseSensitivity().setValue(prevSens / Math.max(getScale() * 0.5, 1));
	}

	@Override
	public void onDisable(boolean inWorld) {
		mc.options.getMouseSensitivity().setValue(prevSens);

		super.onDisable(inWorld);
	}
}

/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.NoKeyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import net.minecraft.util.StringHelper;

// isValidChar moved from SharedConstants to StringHelper in 1.21.11 (and its param went from char to
// an int codepoint, since typed-character input is now represented as a codepoint via CharInput -
// see CharInput#isValidChar, which delegates straight into this method).
@Mixin(StringHelper.class)
public class MixinStringHelper {

	@Overwrite
	public static boolean isValidChar(int chr) {
		Module noKeyBlock = ModuleManager.getModule(NoKeyBlock.class);

		if (!noKeyBlock.isEnabled()) {
			return chr != 167 && chr >= ' ' && chr != 127;
		}

		return (noKeyBlock.getSetting(0).asToggle().getState() || chr != 167)
				&& (noKeyBlock.getSetting(1).asToggle().getState() || chr >= ' ')
				&& (noKeyBlock.getSetting(2).asToggle().getState() || chr != 127);
	}
}

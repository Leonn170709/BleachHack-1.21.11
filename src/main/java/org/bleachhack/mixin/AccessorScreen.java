/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;

// setDrawables() dropped: Screen.drawables is `final` in 1.21.11 (an @Accessor setter can't be
// generated for a final field) and nothing in this codebase used the setter anyway.
// callRenderTextHoverEffect() dropped: Screen.renderTextHoverEffect(...) was removed entirely with
// no replacement method - see WindowTextWidget notes.
@Mixin(Screen.class)
public interface AccessorScreen {

	@Accessor
	public abstract List<Drawable> getDrawables();
}

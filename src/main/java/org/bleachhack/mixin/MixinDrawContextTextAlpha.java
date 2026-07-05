/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.DrawContext;

// 1.19.4's TextRenderer ran every color through a "tweakTransparency" step before drawing: if the
// alpha byte's top 6 bits were all zero (i.e. the caller passed a bare RGB literal like 0x70efe0 with
// no alpha, which is how BleachHack's whole GUI codebase writes text colors), it forced full opacity
// (color | 0xFF000000) instead of treating it as see-through. 1.21.11's DrawContext.drawText dropped
// that normalization and instead just silently skips the draw entirely when alpha == 0
// ("if (ColorHelper.getAlpha(color) != 0) { ... }") - so every bare-RGB text color across the mod
// (module list entries, Options/Credits/Accounts screens, tooltips, ...) went invisible instead of
// throwing, since nothing else changed shape. Restoring the exact 1.19.4 normalization here fixes all
// of those call sites at once instead of auditing and rewriting ~100 individual color literals.
@Mixin(DrawContext.class)
public class MixinDrawContextTextAlpha {

	@ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private int tweakTransparency(int color) {
		return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
	}
}

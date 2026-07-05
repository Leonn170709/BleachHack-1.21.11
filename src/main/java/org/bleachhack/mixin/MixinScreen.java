/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import net.minecraft.client.gui.DrawContext;
import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventRenderScreenBackground;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.Screen;

@Mixin(Screen.class)
public class MixinScreen {

	// Tooltip rendering (renderTooltipFromComponents) moved to MixinDrawContext - 1.21.11 moved the
	// actual draw call from Screen into DrawContext.drawTooltipImmediately, deferred via
	// DrawContext.drawDeferredElements() instead of being called straight from Screen#render.

	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
	private void renderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
		EventRenderScreenBackground event = new EventRenderScreenBackground(context);
		BleachHack.eventBus.post(event);

		if (event.isCancelled()) {
			callback.cancel();
		}
	}
}

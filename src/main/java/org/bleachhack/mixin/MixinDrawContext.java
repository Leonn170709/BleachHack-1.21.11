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

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventRenderTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

// 1.19.4 hooked Screen#renderTooltipFromComponents(MatrixStack, ...) to intercept tooltip draws.
// 1.21.11 removed that method entirely - tooltip drawing moved into DrawContext itself and is now
// deferred (queued via DrawContext#drawTooltip, actually drawn later by drawDeferredElements()), with
// DrawContext#drawTooltipImmediately(...) as the single real draw routine every tooltip path funnels
// through. That's the equivalent hook point now.
@Mixin(DrawContext.class)
public class MixinDrawContext {

	@Unique private boolean skipTooltip;

	@Shadow
	public void drawTooltipImmediately(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
			TooltipPositioner positioner, Identifier texture) {}

	@Inject(method = "drawTooltipImmediately", at = @At("HEAD"), cancellable = true)
	private void drawTooltipImmediately(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
			TooltipPositioner positioner, Identifier texture, CallbackInfo callback) {
		if (skipTooltip) {
			skipTooltip = false;
			return;
		}

		EventRenderTooltip event = new EventRenderTooltip(MinecraftClient.getInstance().currentScreen, new MatrixStack(), components, x, y, x, y);
		BleachHack.eventBus.post(event);

		callback.cancel();
		if (event.isCancelled()) {
			return;
		}

		skipTooltip = true;
		drawTooltipImmediately(textRenderer, event.getComponents(), event.getX(), event.getY(), positioner, texture);
	}
}

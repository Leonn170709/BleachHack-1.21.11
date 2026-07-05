/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.render.RenderTickCounter;

// 1.21.11 turned RenderTickCounter into an interface; the fields this mixin needs (renamed too) now
// live on its actual implementation, RenderTickCounter$Dynamic, so the mixin target moved there.
@Mixin(RenderTickCounter.Dynamic.class)
public class MixinRenderTickCounter {

	@Shadow private float dynamicDeltaTicks;
	@Shadow private float tickProgress;
	@Shadow private long lastTimeMillis;
	@Shadow private float tickTime;

	@Inject(method = "beginRenderTick(J)I", at = @At("HEAD"), cancellable = true)
	private void beginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> ci) {
		if (ModuleManager.getModule(Timer.class).isEnabled()) {
			this.dynamicDeltaTicks = (float) (((timeMillis - this.lastTimeMillis) / this.tickTime)
					* ModuleManager.getModule(Timer.class).getSetting(0).asSlider().getValue());
			this.lastTimeMillis = timeMillis;
			this.tickProgress += this.dynamicDeltaTicks;
			int i = (int) this.tickProgress;
			this.tickProgress -= i;

			ci.setReturnValue(i);
		}
	}

}

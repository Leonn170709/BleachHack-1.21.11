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
import org.bleachhack.module.mods.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.chunk.light.ChunkSkyLightProvider;

// 1.19.4 hooked recalculateLevel(long, long, int) - a small helper that computed the new light level
// for one neighboring position - and forced it to return 0 to short-circuit skylight math for the
// "Skylight" lag-reduction toggle. That helper is gone: the light engine was rewritten and its logic
// is now fully inlined into propagateLightIncrease/propagateLightDecrease directly (no separable
// "compute the level" call left to override). Cancelling both of those outright instead - skipping
// skylight propagation entirely - achieves the same goal (and more thoroughly, since it skips the
// surrounding queueing work too, not just the level computation).
@Mixin(ChunkSkyLightProvider.class)
public class MixinChunkSkylightProvider {

	@Inject(method = "propagateLightIncrease", at = @At("HEAD"), cancellable = true)
	private void propagateLightIncrease(long blockPos, long packed, int lightLevel, CallbackInfo ci) {
		if (ModuleManager.getModule(NoRender.class).isWorldToggled(4)) {
			ci.cancel();
		}
	}

	@Inject(method = "propagateLightDecrease", at = @At("HEAD"), cancellable = true)
	private void propagateLightDecrease(long blockPos, long packed, CallbackInfo ci) {
		if (ModuleManager.getModule(NoRender.class).isWorldToggled(4)) {
			ci.cancel();
		}
	}
}

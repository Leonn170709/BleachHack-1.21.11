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
import org.bleachhack.module.mods.Ambience;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;

// 1.21.11 replaced the old DimensionEffects.getSkyColor() override point with a generic
// EnvironmentAttributes system read once per frame in SkyRendering.updateRenderState() - this is
// the one place that value ends up, so overwriting it here after the vanilla computation is
// simpler than modeling Ambience's override as another attribute source.
@Mixin(SkyRendering.class)
public class MixinSkyRendering {

	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void updateRenderState(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo info) {
		Ambience ambience = ModuleManager.getModule(Ambience.class);
		if (!ambience.isEnabled()) {
			return;
		}

		Integer color = ambience.getSkyColorOverride();
		if (color != null) {
			state.skyColor = color;
		}
	}
}

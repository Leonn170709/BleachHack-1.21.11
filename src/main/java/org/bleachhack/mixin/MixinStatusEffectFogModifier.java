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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;

// 1.21.11 removed BackgroundRenderer entirely and split fog application into per-effect FogModifier
// classes (net.minecraft.client.render.fog). The blindness "should this fog apply" check that used to
// live inline in BackgroundRenderer now lives in StatusEffectFogModifier.shouldApply, shared by every
// status-effect-driven fog modifier (blindness, darkness, ...) - only suppress it for blindness itself.
@Mixin(StatusEffectFogModifier.class)
public class MixinStatusEffectFogModifier {

	@Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
	private void shouldApply(CameraSubmersionType submersionType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		StatusEffectFogModifier self = (StatusEffectFogModifier) (Object) this;

		if (self.getStatusEffect() == StatusEffects.BLINDNESS && ModuleManager.getModule(NoRender.class).isOverlayToggled(0)) {
			cir.setReturnValue(false);
		}
	}
}

/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventEntityControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.mob.MobEntity;

// 1.19.4 hooked isSaddled() on AbstractHorseEntity/PigEntity/StriderEntity individually (each had its
// own copy). 1.21.11 consolidated it into a single hasSaddleEquipped() on the shared MobEntity base
// class (just checks the SADDLE equipment slot) - Mixin can't inject an inherited-but-not-overridden
// method into 3 separate leaf classes that don't each redeclare it, so this hooks the one real
// declaration on MobEntity instead. Harmless for non-rideable mobs since the default check
// (isWearing(EquipmentSlot.SADDLE)) is already always false for them.
@Mixin(MobEntity.class)
public abstract class MixinMobEntity {

	@Inject(method = "hasSaddleEquipped", at = @At("HEAD"), cancellable = true)
	private void hasSaddleEquipped(CallbackInfoReturnable<Boolean> info) {
		EventEntityControl event = new EventEntityControl();
		BleachHack.eventBus.post(event);

		if (event.canBeControlled() != null) {
			info.setReturnValue(event.canBeControlled());
		}
	}
}

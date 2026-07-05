/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import net.minecraft.registry.tag.FluidTags;
import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventReach;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.SpeedMine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.world.World;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	@Shadow private PlayerInventory inventory;

	private MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	// 1.19.4 read the Efficiency/AquaAffinity enchantments directly via EnchantmentHelper - both are
	// gone (enchantments are data-component-driven now) and their effects are pre-baked into two
	// generic attributes (MINING_EFFICIENCY, SUBMERGED_MINING_SPEED) instead. This is a straight port
	// of PlayerEntity's real 1.21.11 getBlockBreakingSpeed body with BleachHack's toggle overrides
	// re-inserted at the same points as 1.19.4.
	@Inject(method = "getBlockBreakingSpeed", at = @At("HEAD"), cancellable = true)
	private void getBlockBreakingSpeed(BlockState block, CallbackInfoReturnable<Float> ci) {
		Module speedMine = ModuleManager.getModule(SpeedMine.class);

		if (speedMine.isEnabled()) {
			float breakingSpeed = this.inventory.getSelectedStack().getMiningSpeedMultiplier(block);
			if (breakingSpeed > 1.0F) {
				breakingSpeed += (float) this.getAttributeValue(EntityAttributes.MINING_EFFICIENCY);
			}

			if (StatusEffectUtil.hasHaste(this)) {
				breakingSpeed *= 1.0F + (StatusEffectUtil.getHasteAmplifier(this) + 1) * 0.2F;
			}

			if (!speedMine.getSetting(4).asToggle().getState()) {
				if (this.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
					float fatigueMult;
					switch (this.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
						case 0:
							fatigueMult = 0.3F;
							break;
						case 1:
							fatigueMult = 0.09F;
							break;
						case 2:
							fatigueMult = 0.0027F;
							break;
						case 3:
						default:
							fatigueMult = 8.1E-4F;
					}

					breakingSpeed *= fatigueMult;
				}
			}

			breakingSpeed *= (float) this.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);

			if (!speedMine.getSetting(5).asToggle().getState()) {
				if (this.isSubmergedIn(FluidTags.WATER)) {
					breakingSpeed *= (float) this.getAttributeInstance(EntityAttributes.SUBMERGED_MINING_SPEED).getValue();
				}

				if (!this.isOnGround()) {
					breakingSpeed /= 5.0F;
				}
			}

			if (speedMine.getSetting(0).asMode().getMode() == 1)
				breakingSpeed *= speedMine.getSetting(3).asSlider().getValueFloat();

			ci.setReturnValue(breakingSpeed);
		}
	}

	// Reach: 1.19.4 hooked ClientPlayerInteractionManager.getReachDistance() (one unified reach value).
	// 1.21.11 removed that method entirely - reach is now two separate entity attributes, each read
	// directly off the player. Both get the same EventReach bonus so "Reach" still boosts block and
	// entity range together like before.
	@Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
	private void getBlockInteractionRange(CallbackInfoReturnable<Double> ci) {
		EventReach event = new EventReach((float) (double) ci.getReturnValueD());
		BleachHack.eventBus.post(event);
		ci.setReturnValue((double) event.getReach());
	}

	@Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
	private void getEntityInteractionRange(CallbackInfoReturnable<Double> ci) {
		EventReach event = new EventReach((float) (double) ci.getReturnValueD());
		BleachHack.eventBus.post(event);
		ci.setReturnValue((double) event.getReach());
	}
}

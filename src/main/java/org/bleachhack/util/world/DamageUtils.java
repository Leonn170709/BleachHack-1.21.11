/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.explosion.ExplosionImpl;

public class DamageUtils {

	private static final MinecraftClient mc = MinecraftClient.getInstance();

	/**
	 * Resolves an {@link Enchantments} {@link RegistryKey} (data-driven registry entries as of 1.20.5+) to
	 * the {@link RegistryEntry} that {@link EnchantmentHelper#getLevel} now expects, using the client's
	 * dynamic registry manager (enchantments are synced to the client just like any other registry).
	 */
	private static RegistryEntry<Enchantment> ench(RegistryKey<Enchantment> key) {
		return mc.world.getRegistryManager().getEntryOrThrow(key);
	}

	/**
	 * Finds the first attribute modifier on {@code stack} (in {@code slot}) for the given attribute.
	 * Replaces the old {@code ItemStack.getAttributeModifiers(EquipmentSlot).get(attribute)} map lookup,
	 * which was removed in favor of a callback-based {@code applyAttributeModifiers}.
	 */
	private static double getBaseAttributeModifier(ItemStack stack, RegistryEntry<EntityAttribute> attribute, EquipmentSlot slot) {
		double[] holder = {Double.NaN};
		stack.applyAttributeModifiers(slot, (attr, modifier) -> {
			if (Double.isNaN(holder[0]) && attr.matches(attribute)) {
				holder[0] = modifier.value();
			}
		});
		return Double.isNaN(holder[0]) ? 0 : holder[0];
	}

	/**
	 * ported: Sharpness/Smite/Bane of Arthropods attack-damage bonuses are data-driven "damage" effect
	 * components as of 1.20.5+ (see EnchantmentHelper#getDamage), evaluated against a LootContext that
	 * vanilla builds from a live ServerWorld (Enchantment#createEnchantedDamageLootContext requires
	 * ServerWorld). DamageUtils only has the client's ClientWorld available, so instead of driving that
	 * generic (and server-only) effect pipeline, this replicates the exact numbers vanilla ships in
	 * data/minecraft/enchantment/{sharpness,smite,bane_of_arthropods}.json: Sharpness applies
	 * unconditionally (base 1.0 + 0.5/level above first), Smite/Bane of Arthropods add 2.5/level but only
	 * if the target's EntityType is in the matching "sensitive_to_*" tag (the modern replacement for the
	 * old EntityGroup.UNDEAD/ARTHROPOD checks).
	 */
	private static float getEnchantAttackDamage(ItemStack stack, Entity target) {
		float bonus = 0f;

		int sharpness = EnchantmentHelper.getLevel(ench(Enchantments.SHARPNESS), stack);
		if (sharpness > 0) {
			bonus += 1.0f + 0.5f * (sharpness - 1);
		}

		int smite = EnchantmentHelper.getLevel(ench(Enchantments.SMITE), stack);
		if (smite > 0 && target != null && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
			bonus += 2.5f + 2.5f * (smite - 1);
		}

		int bane = EnchantmentHelper.getLevel(ench(Enchantments.BANE_OF_ARTHROPODS), stack);
		if (bane > 0 && target != null && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
			bonus += 2.5f + 2.5f * (bane - 1);
		}

		return bonus;
	}

	private static float sumArmorProtection(LivingEntity target, RegistryKey<Enchantment> enchantment, float base, float perLevel) {
		float total = 0f;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (!slot.isArmorSlot()) {
				continue;
			}

			int level = EnchantmentHelper.getLevel(ench(enchantment), target.getEquippedStack(slot));
			if (level > 0) {
				total += base + perLevel * (level - 1);
			}
		}

		return total;
	}

	/**
	 * ported: same reasoning as {@link #getEnchantAttackDamage}. EnchantmentHelper.getProtectionAmount is
	 * now (ServerWorld, LivingEntity, DamageSource) and needs a real ServerWorld internally, which isn't
	 * available client-side. Replicated directly from data/minecraft/enchantment/{protection,
	 * fire_protection,blast_protection,projectile_protection,feather_falling}.json: Protection applies
	 * to any damage (1.0 + 1.0/level above first) unless the source bypasses invulnerability; the other
	 * four are the same shape (2.0 or 3.0 base/per-level) gated on the matching DamageTypeTags entry
	 * (is_fire/is_explosion/is_projectile/is_fall) - the modern replacement for the old
	 * ProtectionEnchantment.Type checks.
	 */
	private static float getEnchantProtectionAmount(LivingEntity target, DamageSource source) {
		if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 0f;
		}

		float total = sumArmorProtection(target, Enchantments.PROTECTION, 1.0f, 1.0f);

		if (source.isIn(DamageTypeTags.IS_FIRE)) {
			total += sumArmorProtection(target, Enchantments.FIRE_PROTECTION, 2.0f, 2.0f);
		}

		if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			total += sumArmorProtection(target, Enchantments.BLAST_PROTECTION, 2.0f, 2.0f);
		}

		if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			total += sumArmorProtection(target, Enchantments.PROJECTILE_PROTECTION, 2.0f, 2.0f);
		}

		if (source.isIn(DamageTypeTags.IS_FALL)) {
			total += sumArmorProtection(target, Enchantments.FEATHER_FALLING, 3.0f, 3.0f);
		}

		return total;
	}

	public static float getItemAttackDamage(ItemStack stack) {
		float damage = 1f + (float) getBaseAttributeModifier(stack, EntityAttributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND);

		return damage + getEnchantAttackDamage(stack, null);
	}

	public static float getAttackDamage(PlayerEntity attacker, Entity target) {
		float cooldown = attacker.getAttackCooldownProgress(0.5F);

		float damage = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE)
				+ (float) getBaseAttributeModifier(attacker.getMainHandStack(), EntityAttributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND);

		damage *= 0.2f + cooldown * cooldown * 0.8f;
		float enchDamage = getEnchantAttackDamage(attacker.getMainHandStack(), target) * cooldown;

		if (damage <= 0f && enchDamage <= 0f) {
			return 0f;
		}

		// Crits
		if (cooldown > 0.9
				&& attacker.fallDistance > 0.0F
				&& !attacker.isOnGround()
				&& !attacker.isClimbing()
				&& !attacker.isTouchingWater()
				&& !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
				&& !attacker.hasVehicle()
				&& !attacker.isSprinting()
				&& target instanceof LivingEntity) {
			damage *= 1.5f;
		}

		damage += enchDamage;

		if (target instanceof LivingEntity) {
			LivingEntity livingTarget = (LivingEntity) target;
			DamageSource damageSource = mc.world.getDamageSources().playerAttack(attacker);

			// Armor
			damage = DamageUtil.getDamageLeft(livingTarget, damage, damageSource, livingTarget.getArmor(),
					(float) livingTarget.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));

			// Enchantments
			if (livingTarget.hasStatusEffect(StatusEffects.RESISTANCE)) {
				int resistance = 25 - (livingTarget.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
				float resistance_1 = damage * resistance;
				damage = Math.max(resistance_1 / 25f, 0f);
			}

			if (damage <= 0f) {
				damage = 0f;
			} else {
				float protAmount = getEnchantProtectionAmount(livingTarget, damageSource);
				if (protAmount > 0) {
					damage = DamageUtil.getInflictedDamage(damage, protAmount);
				}
			}
		} else if (damage <= 0f) {
			damage = 0f;
		}

		return damage;
	}

	public static float getExplosionDamage(Vec3d explosionPos, float power, LivingEntity target) {
		if (mc.world.getDifficulty() == Difficulty.PEACEFUL)
			return 0f;

		double maxDist = power * 2;
		if (!mc.world.getOtherEntities(null, new Box(
				MathHelper.floor(explosionPos.x - maxDist - 1.0),
				MathHelper.floor(explosionPos.y - maxDist - 1.0),
				MathHelper.floor(explosionPos.z - maxDist - 1.0),
				MathHelper.floor(explosionPos.x + maxDist + 1.0),
				MathHelper.floor(explosionPos.y + maxDist + 1.0),
				MathHelper.floor(explosionPos.z + maxDist + 1.0))).contains(target)) {
			return 0f;
		}

		// ported: Entity.isImmuneToExplosion() now takes an Explosion instance (Explosion#getWorld()
		// requires a live ServerWorld, unavailable client-side, so a real Explosion can't be constructed
		// here). Only WardenEntity (digging/emerging) and ArmorStandEntity (invisible, and only if the
		// explosion "preserves decorative entities") override the base "false" - and WardenEntity's own
		// isDiggingOrEmerging() is itself package-private, so it can't be read from here either. DamageUtils
		// is only ever called against players/mobs in combat modules (CrystalAura/AutoLog), never wardens
		// or armor stands, so this falls back to the base Entity behavior (never immune) unconditionally.
		boolean immuneToExplosion = false;

		if (!immuneToExplosion && !target.isInvulnerable()) {
			double distExposure = Math.sqrt(target.squaredDistanceTo(explosionPos)) / maxDist;
			if (distExposure <= 1.0) {
				double xDiff = target.getX() - explosionPos.x;
				double yDiff = target.getEyeY() - explosionPos.y;
				double zDiff = target.getZ() - explosionPos.z;
				double diff = Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
				if (diff != 0.0) {
					double exposure = ExplosionImpl.calculateReceivedDamage(explosionPos, target);
					double finalExposure = (1.0 - distExposure) * exposure;

					float toDamage = (float) Math.floor((finalExposure * finalExposure + finalExposure) / 2.0 * 7.0 * maxDist + 1.0);

					if (target instanceof PlayerEntity) {
						if (mc.world.getDifficulty() == Difficulty.EASY) {
							toDamage = Math.min(toDamage / 2f + 1f, toDamage);
						} else if (mc.world.getDifficulty() == Difficulty.HARD) {
							toDamage = toDamage * 3f / 2f;
						}
					}

					DamageSource explosionSource = mc.world.getDamageSources().explosion(null, null);

					// Armor
					toDamage = DamageUtil.getDamageLeft(target, toDamage, explosionSource, target.getArmor(),
							(float) target.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS).getValue());

					// Enchantments
					if (target.hasStatusEffect(StatusEffects.RESISTANCE)) {
						int resistance = 25 - (target.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
						float resistance_1 = toDamage * resistance;
						toDamage = Math.max(resistance_1 / 25f, 0f);
					}

					if (toDamage <= 0f) {
						toDamage = 0f;
					} else {
						float protAmount = getEnchantProtectionAmount(target, explosionSource);
						if (protAmount > 0) {
							toDamage = DamageUtil.getInflictedDamage(toDamage, protAmount);
						}
					}

					return toDamage;
				}
			}
		}

		return 0f;
	}

	public static boolean willKill(LivingEntity target, float damage) {
		if (target.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING || target.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
			return false;
		}

		return damage >= target.getHealth() + target.getAbsorptionAmount();
	}

	public static boolean willPop(LivingEntity target, float damage) {
		if (target.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING && target.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
			return false;
		}

		return damage >= target.getHealth() + target.getAbsorptionAmount();
	}

	public static boolean willPopOrKill(LivingEntity target, float damage) {
		return damage >= target.getHealth() + target.getAbsorptionAmount();
	}

	public static boolean willGoBelowHealth(LivingEntity target, float damage, float minHealth) {
		return target.getHealth() + target.getAbsorptionAmount() - damage < minHealth;
	}
}

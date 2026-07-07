/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.event.events.EventTick;
import org.bleachhack.event.events.EventWorldRender;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingColor;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.render.Renderer;
import org.bleachhack.util.render.color.QuadColor;
import org.bleachhack.util.world.WorldUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ported from Trouser-Streak's InfiniteReach (https://github.com/etianl/Trouser-Streak).
 */
public class InfiniteReach extends Module {

	private int cooldown;
	private Entity hoveredTarget;
	private BlockHitResult hoveredBlock;

	public InfiniteReach() {
		this(new SettingMode("Mode", "Vanilla", "Paper").withDesc("Vanilla: reach capped at 22 blocks. Paper: a much longer reach is often possible."),
				new SettingToggle("Auto Calculate Packets", true).withDesc("Scales the spam packet count based on the clip distance instead of using a fixed amount."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the settings other
	// settings react to as constructor parameters first lets the predicates below capture those
	// local variables instead.
	private InfiniteReach(SettingMode compatMode, SettingToggle autoPackets) {
		super("InfiniteReach", KEY_UNBOUND, ModuleCategory.EXPLOITS, "Attacks/mines far beyond normal reach by teleporting there and back for a single packet.",
				compatMode,
				new SettingSlider("Distance", 1, 22, 22, 0).withDesc("How far away you can attack/mine.")
						.visibleWhen(() -> compatMode.getMode() == 0),
				new SettingSlider("Distance", 1, 99, 59, 0).withDesc("How far away you can attack/mine.")
						.visibleWhen(() -> compatMode.getMode() == 1),
				new SettingToggle("Clip Up", true).withDesc("Paper only: hops above yourself and the target before landing on it, instead of a straight teleport - also works as a Mace Smash and routes around obstacles.")
						.visibleWhen(() -> compatMode.getMode() == 1),
				autoPackets,
				new SettingSlider("Packets", 1, 17, 4, 0).withDesc("Fixed spam packet count, used when Auto Calculate Packets is off.")
						.visibleWhen(() -> !autoPackets.getState()),
				new SettingSlider("Delay", 1, 20, 5, 0).withDesc("Ticks to wait between attacks/mines."),
				new SettingToggle("Only With Mace", false).withDesc("Only reach-attacks while holding a Mace and the target isn't blocking."),
				new SettingToggle("Swing Arm", true).withDesc("Swings your hand on your screen for visual feedback."),
				new SettingToggle("Raycast", false).withDesc("Only attacks/mines if you can see the target - defeats the point of reaching through walls, so off by default."),
				new SettingToggle("Highlight", true).withDesc("Draws an outline around whatever you're currently targeting.").withChildren(
						new SettingColor("Color", 255, 60, 60)));
	}

	private double getMaxDistance() {
		return getSetting(0).asMode().getMode() == 0 ? getSetting(1).asSlider().getValue() : getSetting(2).asSlider().getValue();
	}

	@Override
	public void onDisable(boolean inWorld) {
		hoveredTarget = null;
		hoveredBlock = null;
		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		double maxDistance = getMaxDistance();

		Vec3d eyePos = mc.player.getEyePos();
		Vec3d look = mc.player.getRotationVec(1f);
		Vec3d endVec = eyePos.add(look.multiply(maxDistance));

		EntityHitResult entityHit = ProjectileUtil.raycast(mc.player, eyePos, endVec,
				mc.player.getBoundingBox().stretch(look.multiply(maxDistance)).expand(1),
				e -> e.isAlive() && e.isAttackable() && !e.isInvulnerable() && e != mc.player,
				maxDistance * maxDistance);

		hoveredTarget = entityHit != null ? entityHit.getEntity() : null;
		hoveredBlock = null;

		if (hoveredTarget == null) {
			BlockHitResult blockHit = (BlockHitResult) mc.world.raycast(new RaycastContext(eyePos, endVec,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

			if (!mc.world.getBlockState(blockHit.getBlockPos()).isAir()) {
				hoveredBlock = blockHit;
			}
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		boolean attacking = mc.options.attackKey.isPressed();
		boolean using = mc.options.useKey.isPressed();
		if (!attacking && !using) {
			return;
		}

		if (hoveredTarget instanceof LivingEntity target) {
			if (getSetting(7).asToggle().getState()
					&& (mc.player.getMainHandStack().getItem() != Items.MACE || target.isBlocking())) {
				return;
			}

			if (getSetting(9).asToggle().getState() && !mc.player.canSee(target)) {
				return;
			}

			attackEntity(target, attacking);
			cooldown = getSetting(6).asSlider().getValueInt();
		} else if (hoveredBlock != null && attacking) {
			attackBlock(hoveredBlock);
			cooldown = getSetting(6).asSlider().getValueInt();
		}
	}

	private void attackEntity(LivingEntity target, boolean attacking) {
		Vec3d home = mc.player.getEntityPos();
		double distance = home.distanceTo(target.getEntityPos());

		Vec3d landing = target.getEntityPos();
		if (WorldUtils.isTeleportUnsafe(mc.player, landing)) {
			landing = WorldUtils.findSafeOffset(mc.player, landing, 0.4, 0);
		}

		spamStatusPackets(distance);

		// Clip Up: hop above yourself and above the target before landing, instead of teleporting
		// straight there - this both routes around anything blocking a direct line to the target
		// and, since you land as if falling, doubles as a Mace Smash.
		boolean clipUp = getSetting(0).asMode().getMode() == 1 && getSetting(3).asToggle().getState();
		if (clipUp) {
			Vec3d aboveSelf = home.add(0, distance, 0);
			Vec3d aboveTarget = landing.add(0, distance, 0);
			if (!WorldUtils.isTeleportUnsafe(mc.player, aboveSelf) && !WorldUtils.isTeleportUnsafe(mc.player, aboveTarget)) {
				WorldUtils.sendTeleport(aboveSelf);
				WorldUtils.sendTeleport(aboveTarget);
			}
		}

		WorldUtils.sendTeleport(landing);

		if (attacking) {
			mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
		} else {
			mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(target, mc.player.isSneaking(), Hand.MAIN_HAND));
		}

		if (getSetting(8).asToggle().getState()) {
			mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
			mc.player.swingHand(Hand.MAIN_HAND);
		}

		if (clipUp) {
			WorldUtils.sendTeleport(landing.add(0, distance, 0).add(0, 0.01, 0));
			WorldUtils.sendTeleport(home.add(0, distance, 0).add(0, 0.01, 0));
		}

		WorldUtils.sendTeleport(home);
		WorldUtils.sendTeleport(WorldUtils.findSafeOffset(mc.player, home, 0.05, 0.01));
	}

	private void attackBlock(BlockHitResult blockHit) {
		Vec3d home = mc.player.getEntityPos();
		BlockPos pos = blockHit.getBlockPos();
		double distance = home.distanceTo(Vec3d.ofCenter(pos));

		Vec3d landing = WorldUtils.findSafeOffset(mc.player, Vec3d.ofCenter(pos), 0.4, 0);

		spamStatusPackets(distance);
		WorldUtils.sendTeleport(landing);

		mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, blockHit.getSide()));
		mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, blockHit.getSide()));

		if (getSetting(8).asToggle().getState()) {
			mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
			mc.player.swingHand(Hand.MAIN_HAND);
		}

		WorldUtils.sendTeleport(home);
		WorldUtils.sendTeleport(WorldUtils.findSafeOffset(mc.player, home, 0.05, 0.01));
	}

	// Auto Calculate Packets scales the spam count with the actual clip distance - a longer clip
	// is a bigger, more suspicious jump and needs more "you're grounded" spam beforehand.
	private void spamStatusPackets(double distance) {
		int count = getSetting(4).asToggle().getState()
				? Math.min(17, Math.max(1, (int) Math.ceil(distance / 5.0)))
				: getSetting(5).asSlider().getValueInt();

		for (int i = 0; i < count; i++) {
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
		}
	}

	@BleachSubscribe
	public void onWorldRender(EventWorldRender.Post event) {
		if (!getSetting(10).asToggle().getState()) {
			return;
		}

		int[] rgb = getSetting(10).asToggle().getChild(0).asColor().getRGBArray();

		if (hoveredTarget != null) {
			Renderer.drawBoxOutline(hoveredTarget.getBoundingBox(), QuadColor.single(rgb[0], rgb[1], rgb[2], 255), 2f);
		} else if (hoveredBlock != null) {
			Renderer.drawBoxOutline(hoveredBlock.getBlockPos(), QuadColor.single(rgb[0], rgb[1], rgb[2], 255), 2f);
		}
	}

}

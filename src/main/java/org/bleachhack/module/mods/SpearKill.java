/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventPacket;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.world.WorldUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from Trouser-Streak's SpearKill (https://github.com/etianl/Trouser-Streak). Vanilla's
 * Spear deals bonus "kinetic" damage the longer you hold high speed toward your target while
 * charging a thrust - Lunge boosts your real velocity to build that speed reliably, Blink instead
 * withholds your movement packets while charging and releases them as one large jump when close
 * enough, so the server only ever sees you already there.
 */
public class SpearKill extends Module {

	private Entity target;
	private boolean charging;
	private boolean holdingPackets;
	private Vec3d blinkStart;
	private boolean aboveFirstPhase;
	private Vec3d abovePos;
	private int blinkLungeTicks;

	public SpearKill() {
		this(new SettingMode("Mode", "Lunge", "Blink").withDesc("How to reach charging speed. Lunge: push you toward the target with real velocity. Blink: hold your movement back, then release it all as one jump."),
				new SettingMode("Direction", "Direct", "Above", "Auto").withDesc("Direct: lunge straight at the target. Above: lunge to a point over the target first, then stab down. Auto: try Above, but use Direct if the spot above (or the way down) is blocked."),
				new SettingToggle("Blink+Lunge", false).withDesc("Also push you toward the target with velocity while charging in Blink mode."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the settings other
	// settings react to as constructor parameters first lets the predicates below capture those
	// local variables instead.
	private SpearKill(SettingMode mode, SettingMode lungeDirection, SettingToggle blinkLunge) {
		super("SpearKill", KEY_UNBOUND, ModuleCategory.EXPLOITS,
				"Helps you reach the speed vanilla's Spear needs for a charged hit.",
				mode,
				new SettingSlider("Max Range", 1, 512, 256, 0).withDesc("How far away a target can be and still get locked onto."),
				new SettingToggle("Ignore Friends", true).withDesc("Never targets players on your friend list."),

				new SettingSlider("Strength", 0.1, 10, 5, 1).withDesc("How fast to push you toward the target.")
						.visibleWhen(() -> mode.getMode() == 0 || blinkLunge.getState()),
				new SettingToggle("Stop On Target", true).withDesc("Stops pushing you once you've reached the target.").withChildren(
						new SettingSlider("Stop Dist", 0, 10, 2, 1).withDesc("How close to the target counts as \"reached\"."))
						.visibleWhen(() -> mode.getMode() == 0),
				lungeDirection.visibleWhen(() -> mode.getMode() == 0),
				new SettingSlider("Height", 1, 30, 6, 1).withDesc("How many blocks above the target to aim for first (Above/Auto only).")
						.visibleWhen(() -> mode.getMode() == 0 && lungeDirection.getMode() != 0),
				new SettingSlider("Trigger", 0.5, 10, 2, 1).withDesc("How close to the point above the target before switching to a direct lunge.")
						.visibleWhen(() -> mode.getMode() == 0 && lungeDirection.getMode() != 0),
				new SettingToggle("Validate Path", true).withDesc("Auto only: makes sure the spot above the target and the way down are actually clear before committing to it.")
						.visibleWhen(() -> mode.getMode() == 0 && lungeDirection.getMode() == 2),

				new SettingSlider("Flush", 1, 10, 3, 1).withDesc("How close to the target before releasing your held movement.")
						.visibleWhen(() -> mode.getMode() == 1),
				blinkLunge.visibleWhen(() -> mode.getMode() == 1),
				new SettingSlider("Lunge Delay", 1, 30, 15, 0).withDesc("How many ticks to charge before the Blink+Lunge push kicks in.")
						.visibleWhen(() -> mode.getMode() == 1 && blinkLunge.getState()),
				new SettingToggle("Raycast", true).withDesc("Only locks onto targets you can see."));
	}

	@Override
	public void onDisable(boolean inWorld) {
		if (charging && getSetting(0).asMode().getMode() == 1) {
			flush();
		}

		reset();
		super.onDisable(inWorld);
	}

	private void reset() {
		target = null;
		charging = false;
		holdingPackets = false;
		blinkStart = null;
		aboveFirstPhase = false;
		abovePos = null;
		blinkLungeTicks = 0;
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		boolean nowCharging = mc.player.isUsingItem() && mc.player.getActiveItem().contains(DataComponentTypes.KINETIC_WEAPON);

		if (nowCharging && target == null) {
			target = findTarget();
		}

		if (!nowCharging && charging) {
			if (getSetting(0).asMode().getMode() == 1) {
				flush();
			}
			reset();
		}
		charging = nowCharging;

		if (!charging || target == null || !target.isAlive()) {
			return;
		}

		rotateToTarget();

		if (getSetting(0).asMode().getMode() == 0) {
			lunge();
		} else {
			blink();
		}
	}

	private void lunge() {
		double stopDistance = getSetting(4).asToggle().getState() ? getSetting(4).asToggle().getChild(0).asSlider().getValue() : -1;
		if (stopDistance >= 0 && mc.player.getBoundingBox().expand(stopDistance).intersects(target.getBoundingBox())) {
			mc.player.setVelocity(Vec3d.ZERO);
			target = null;
			return;
		}

		mc.player.setSprinting(true);
		mc.player.setVelocity(lungeDirectionVec().multiply(getSetting(3).asSlider().getValue()));
	}

	// Straight-at-target by default; FromAbove/Auto first aim for a point above the target and
	// only switch to a direct approach once close to that point (or, for Auto, if the above
	// position/path turns out to be blocked).
	private Vec3d lungeDirectionVec() {
		Vec3d playerPos = mc.player.getEntityPos();
		Vec3d targetCenter = target.getBoundingBox().getCenter();
		int mode = getSetting(5).asMode().getMode();

		if (mode == 0) {
			return targetCenter.subtract(playerPos).normalize();
		}

		if (!aboveFirstPhase || abovePos == null) {
			abovePos = new Vec3d(targetCenter.x, targetCenter.y + getSetting(6).asSlider().getValue(), targetCenter.z);
			aboveFirstPhase = true;
		}

		if (mode == 2 && getSetting(8).asToggle().getState() && !isAbovePathClear(abovePos, targetCenter)) {
			aboveFirstPhase = false;
			return targetCenter.subtract(playerPos).normalize();
		}

		if (playerPos.distanceTo(abovePos) < getSetting(7).asSlider().getValue()) {
			aboveFirstPhase = false;
			return targetCenter.subtract(playerPos).normalize();
		}

		return abovePos.subtract(playerPos).normalize();
	}

	private boolean isAbovePathClear(Vec3d above, Vec3d targetCenter) {
		if (WorldUtils.isTeleportUnsafe(mc.player, above)) {
			return false;
		}

		int steps = Math.max(10, (int) (above.distanceTo(targetCenter) * 2.5));
		for (int i = 1; i < steps; i++) {
			if (WorldUtils.isTeleportUnsafe(mc.player, above.lerp(targetCenter, i / (double) steps))) {
				return false;
			}
		}

		return true;
	}

	private void blink() {
		holdingPackets = true;

		if (blinkStart == null) {
			blinkStart = mc.player.getEntityPos();
		}

		if (getSetting(10).asToggle().getState() && ++blinkLungeTicks >= getSetting(11).asSlider().getValueInt()) {
			mc.player.setSprinting(true);
			mc.player.setVelocity(target.getBoundingBox().getCenter().subtract(mc.player.getEntityPos()).normalize().multiply(getSetting(3).asSlider().getValue()));
		}

		if (mc.player.getEntityPos().distanceTo(target.getEntityPos()) <= getSetting(9).asSlider().getValue()) {
			flush();
			blinkStart = mc.player.getEntityPos();
			blinkLungeTicks = 0;
		}
	}

	// Unlike the other three ported modules, Blink's flush doesn't need a spam-packet burst first -
	// the original just sends the withheld start/end positions directly as one jump.
	private void flush() {
		if (!holdingPackets || blinkStart == null) {
			return;
		}

		WorldUtils.sendTeleport(blinkStart);
		WorldUtils.sendTeleport(mc.player.getEntityPos());
		holdingPackets = false;
	}

	@BleachSubscribe
	public void onSendPacket(EventPacket.Send event) {
		if (charging && holdingPackets && getSetting(0).asMode().getMode() == 1 && event.getPacket() instanceof PlayerMoveC2SPacket) {
			event.setCancelled(true);
		}
	}

	private void rotateToTarget() {
		Vec3d toTarget = target.getBoundingBox().getCenter().subtract(mc.player.getEyePos()).normalize();
		float yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.asin(toTarget.y));
		mc.player.setYaw(yaw);
		mc.player.setPitch(pitch);
	}

	private Entity findTarget() {
		double maxRange = getSetting(1).asSlider().getValue();
		Vec3d eyePos = mc.player.getEyePos();
		Vec3d look = mc.player.getRotationVec(1f);

		EntityHitResult hit = ProjectileUtil.raycast(mc.player, eyePos, eyePos.add(look.multiply(maxRange)),
				mc.player.getBoundingBox().stretch(look.multiply(maxRange)).expand(1),
				this::isValidTarget, maxRange * maxRange);

		return hit != null ? hit.getEntity() : null;
	}

	private boolean isValidTarget(Entity e) {
		if (!(e instanceof LivingEntity) || !e.isAlive() || e == mc.player) {
			return false;
		}

		if (getSetting(2).asToggle().getState() && e instanceof PlayerEntity && BleachHack.friendMang.has(e)) {
			return false;
		}

		return !getSetting(12).asToggle().getState() || mc.player.canSee(e);
	}

}

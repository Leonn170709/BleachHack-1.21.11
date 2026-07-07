/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.event.events.EventPacket;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.PlayerInteractEntityC2SUtils;
import org.bleachhack.util.PlayerInteractEntityC2SUtils.InteractType;
import org.bleachhack.util.world.WorldUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from Trouser-Streak's MaceKill (https://github.com/etianl/Trouser-Streak).
 */
public class MaceKill extends Module {

	private boolean sendingAttack;

	// Queued follow-up attacks for Totem Bypass - hitting all of them back to back in the same
	// tick doesn't out-damage a totem's invulnerability frames, so the rest are spaced out with a
	// real tick delay between each.
	private LivingEntity queuedTarget;
	private int queuedAttacksLeft;
	private double queuedHeight;
	private int queuedDelay;

	public MaceKill() {
		this(new SettingToggle("Auto Calculate Packets", true).withDesc("Scales the spam packet count based on the fall height instead of using a fixed amount."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the Auto Calculate
	// Packets toggle as a constructor parameter first lets the manual Packets slider below capture
	// that local variable instead.
	private MaceKill(SettingToggle autoPackets) {
		super("MaceKill", KEY_UNBOUND, ModuleCategory.EXPLOITS,
				"Fakes a fall right before hitting so your Mace lands a full smash attack, risk-free - can also chain hits to bypass totems.",
				new SettingSlider("Fall Height", 1, 169, 22, 0).withDesc("How many blocks to fake-fall from before attacking."),
				autoPackets,
				new SettingSlider("Packets", 1, 17, 4, 0).withDesc("Fixed spam packet count, used when Auto Calculate Packets is off.")
						.visibleWhen(() -> !autoPackets.getState()),
				new SettingToggle("Totem Bypass", false).withDesc("Chains several smash attacks, each from a bit higher, to out-damage a totem's regeneration.").withChildren(
						new SettingSlider("Attacks", 1, 3, 3, 0).withDesc("How many extra attacks to chain after the first."),
						new SettingSlider("Height +", 1, 100, 9, 0).withDesc("How much extra fall height to add for each chained attack."),
						new SettingSlider("Bypass Delay", 0, 20, 4, 0).withDesc("Ticks to wait between chained attacks - all at once doesn't beat a totem's invulnerability window.")),
				new SettingToggle("Skip If Blocked", true).withDesc("Don't attack if the target is shielding or can't take damage."),
				new SettingToggle("Swing Arm", true).withDesc("Swings your hand on your screen for visual feedback."),
				new SettingToggle("Raycast", true).withDesc("Only attacks if you can see the target."));
	}

	@BleachSubscribe
	public void onSendPacket(EventPacket.Send event) {
		if (sendingAttack || !(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet)) {
			return;
		}

		if (PlayerInteractEntityC2SUtils.getInteractType(packet) != InteractType.ATTACK
				|| mc.player.getMainHandStack().getItem() != Items.MACE
				|| mc.player.hasVehicle()) {
			return;
		}

		if (!(PlayerInteractEntityC2SUtils.getEntity(packet) instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}

		if (getSetting(4).asToggle().getState() && (target.isBlocking() || target.isInvulnerable())) {
			return;
		}

		if (getSetting(6).asToggle().getState() && !mc.player.canSee(target)) {
			return;
		}

		event.setCancelled(true);

		boolean totemBypass = getSetting(3).asToggle().getState();
		double height = getSetting(0).asSlider().getValue();

		smash(target, height);

		if (totemBypass) {
			queuedTarget = target;
			queuedAttacksLeft = getSetting(3).asToggle().getChild(0).asSlider().getValueInt() - 1;
			queuedHeight = height + getSetting(3).asToggle().getChild(1).asSlider().getValue();
			queuedDelay = getSetting(3).asToggle().getChild(2).asSlider().getValueInt();
		}
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (queuedAttacksLeft <= 0 || queuedTarget == null) {
			return;
		}

		if (!queuedTarget.isAlive()) {
			queuedAttacksLeft = 0;
			queuedTarget = null;
			return;
		}

		if (queuedDelay-- > 0) {
			return;
		}

		smash(queuedTarget, queuedHeight);

		queuedAttacksLeft--;
		queuedHeight += getSetting(3).asToggle().getChild(1).asSlider().getValue();
		queuedDelay = getSetting(3).asToggle().getChild(2).asSlider().getValueInt();

		if (queuedAttacksLeft <= 0) {
			queuedTarget = null;
		}
	}

	@Override
	public void onDisable(boolean inWorld) {
		queuedTarget = null;
		queuedAttacksLeft = 0;
		super.onDisable(inWorld);
	}

	private void smash(LivingEntity target, double height) {
		Vec3d home = mc.player.getEntityPos();

		sendingAttack = true;
		try {
			spamStatusPackets(height);

			WorldUtils.sendTeleport(home.add(0, height, 0));
			WorldUtils.sendTeleport(home);

			if (getSetting(5).asToggle().getState()) {
				mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
				mc.player.swingHand(Hand.MAIN_HAND);
			}

			mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));

			WorldUtils.sendTeleport(WorldUtils.findSafeOffset(mc.player, home, 0.05, 0.01));
		} finally {
			sendingAttack = false;
		}
	}

	// Auto Calculate Packets scales the spam count with the fake fall's height: taller (more
	// suspicious/further) fakes need more "you're grounded" spam beforehand to keep the server's
	// movement-distance tracking from flagging the sudden teleport.
	private void spamStatusPackets(double height) {
		int count = getSetting(1).asToggle().getState()
				? Math.min(17, Math.max(1, (int) Math.ceil(height / 6.0)))
				: getSetting(2).asSlider().getValueInt();

		for (int i = 0; i < count; i++) {
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
		}
	}

}

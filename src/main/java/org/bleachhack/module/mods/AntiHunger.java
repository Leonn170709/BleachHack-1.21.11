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
import org.bleachhack.event.events.EventSendMovementPackets;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingToggle;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class AntiHunger extends Module {

	// legacy (pre-1.21.11 BleachHack) implementation state
	private boolean bool = false;

	// meteor-style implementation state
	private boolean lastOnGround = false;
	private boolean ignorePacket = false;

	public AntiHunger() {
		this(new SettingMode("Mode", "Old", "New").withDesc("Old is the original 1.19.4 behavior, New is a Meteor-style implementation."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the Mode setting as
	// a constructor parameter first lets the predicates below capture that local variable instead.
	private AntiHunger(SettingMode mode) {
		super("AntiHunger", KEY_UNBOUND, ModuleCategory.PLAYER, "Minimizes the amount of hunger you use (Also makes you slide).",
				mode,
				new SettingToggle("Relaxed", false).withDesc("Only activates every other tick, might fix getting fly kicked.")
						.visibleWhen(() -> mode.getMode() == 0),
				new SettingToggle("Sprint Spoof", true).withDesc("Cancels sprint start packets.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("OnGround Spoof", true).withDesc("Spoofs the onGround flag while standing still on the ground.")
						.visibleWhen(() -> mode.getMode() == 1));
	}

	@Override
	public void onEnable(boolean inWorld) {
		super.onEnable(inWorld);
		bool = false;
		ignorePacket = false;

		if (inWorld) {
			lastOnGround = mc.player.isOnGround();
		}
	}

	@BleachSubscribe
	public void onSendMovementPackets(EventSendMovementPackets event) {
		if (getSetting(0).asMode().getMode() != 1) {
			return;
		}

		if (mc.player.isOnGround() && !lastOnGround && getSetting(3).asToggle().getState()) {
			// let one real packet through so landing still registers (keeps fall damage working)
			ignorePacket = true;
		}

		lastOnGround = mc.player.isOnGround();
	}

	@BleachSubscribe
	public void onSendPacket(EventPacket.Send event) {
		if (getSetting(0).asMode().getMode() == 1) {
			onMeteorPacket(event);
		} else {
			onLegacyPacket(event);
		}
	}

	private void onLegacyPacket(EventPacket.Send event) {
		if (event.getPacket() instanceof PlayerMoveC2SPacket) {
			if (mc.player.getVelocity().y != 0 && !mc.options.jumpKey.isPressed() && (!bool || !getSetting(1).asToggle().getState())) {
				boolean onGround = mc.player.fallDistance >= 0.1f;
				mc.player.setOnGround(onGround);
				((PlayerMoveC2SPacket) event.getPacket()).onGround = onGround;
				bool = true;
			} else {
				bool = false;
			}
		}
	}

	private void onMeteorPacket(EventPacket.Send event) {
		if (ignorePacket && event.getPacket() instanceof PlayerMoveC2SPacket) {
			ignorePacket = false;
			return;
		}

		if (mc.player.hasVehicle() || mc.player.isTouchingWater() || mc.player.isSubmergedInWater()) {
			return;
		}

		if (event.getPacket() instanceof ClientCommandC2SPacket packet && getSetting(2).asToggle().getState()) {
			if (packet.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
				event.setCancelled(true);
			}
		}

		if (event.getPacket() instanceof PlayerMoveC2SPacket packet && getSetting(3).asToggle().getState()
				&& mc.player.isOnGround() && mc.player.fallDistance <= 0.0 && !mc.interactionManager.isBreakingBlock()) {
			packet.onGround = false;
		}
	}

}

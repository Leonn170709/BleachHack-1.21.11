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
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.util.PlayerInteractEntityC2SUtils;
import org.bleachhack.util.PlayerInteractEntityC2SUtils.InteractType;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * @author sl, Bleach
 */
public class Criticals extends Module {

	public Criticals() {
		super("Criticals", KEY_UNBOUND, ModuleCategory.COMBAT, "Attempts to force Critical hits on entities you hit.",
				new SettingMode("Mode", "MiniJump", "FullJump", "Packet", "UpdatedNCP", "OldNCP").withDesc(
						"Criticals mode. MiniJump/FullJump fake a (mini/full) jump, Packet/UpdatedNCP/OldNCP (from Meteor) "
						+ "spoof a tiny, near-invisible height change tuned to different anticheat detection thresholds."));
	}

	@BleachSubscribe
	public void sendPacket(EventPacket.Send event) {
		if (event.getPacket() instanceof PlayerInteractEntityC2SPacket) {
			PlayerInteractEntityC2SPacket packet = (PlayerInteractEntityC2SPacket) event.getPacket();
			if (PlayerInteractEntityC2SUtils.getInteractType(packet) == InteractType.ATTACK
					&& PlayerInteractEntityC2SUtils.getEntity(packet) instanceof LivingEntity) {
				sendCritPackets();
			}
		}
	}

	private void sendCritPackets() {
		if (mc.player.isClimbing() || mc.player.isTouchingWater()
				|| mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || mc.player.hasVehicle()) {
			return;
		}

		boolean sprinting = mc.player.isSprinting();
		if (sprinting) {
			mc.player.setSprinting(false);
			mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));
		}

		if (mc.player.isOnGround()) {
			double x = mc.player.getX();
			double y = mc.player.getY();
			double z = mc.player.getZ();
			switch (getSetting(0).asMode().getMode()) {
				case 0 -> {
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0633, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, mc.player.horizontalCollision));
				}
				case 1 -> {
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.42, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.65, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.72, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.53, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.32, z, false, mc.player.horizontalCollision));
				}
				case 2 -> {
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, mc.player.horizontalCollision));
				}
				case 3 -> {
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0000008, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, mc.player.horizontalCollision));
				}
				case 4 -> {
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.11, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.1100013579, z, false, mc.player.horizontalCollision));
					mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0000013579, z, false, mc.player.horizontalCollision));
				}
			}
		}

		if (sprinting) {
			mc.player.setSprinting(true);
			mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_SPRINTING));
		}
	}
}

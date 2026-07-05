/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.event.events.EventPlayerPushed;
import org.bleachhack.event.events.EventPacket;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * @author sl First Module utilizing EventBus!
 */
public class NoVelocity extends Module {

	public NoVelocity() {
		super("NoVelocity", KEY_UNBOUND, ModuleCategory.PLAYER, "If you take some damage, you don't move.",
				new SettingToggle("Knockback", true).withDesc("Reduces knockback from other entities.").withChildren(
						new SettingSlider("VelXZ", 0, 100, 0, 1).withDesc("How much horizontal velocity to keep."),
						new SettingSlider("VelY", 0, 100, 0, 1).withDesc("How much vertical velocity  to keep.")),
				new SettingToggle("Explosions", true).withDesc("Reduces explosion velocity.").withChildren(
						new SettingSlider("VelXZ", 0, 100, 0, 1).withDesc("How much horizontal velocity to keep."),
						new SettingSlider("VelY", 0, 100, 0, 1).withDesc("How much vertical velocity to keep.")),
				new SettingToggle("Pushing", true).withDesc("Reduces how much you get pushed by entitie.s").withChildren(
						new SettingSlider("Amount", 0, 100, 0, 1).withDesc("How much pushing to keep.")),
				new SettingToggle("Fluids", true).withDesc("Reduces how much you get pushed from fluids."));
	}

	@BleachSubscribe
	public void onPlayerPushed(EventPlayerPushed event) {
		if (getSetting(2).asToggle().getState()) {
			double amount = getSetting(2).asToggle().getChild(0).asSlider().getValue() / 100d;
			event.setPushX(event.getPushX() * amount);
			event.setPushY(event.getPushY() * amount);
			event.setPushZ(event.getPushZ() * amount);
		}
	}

	@BleachSubscribe
	public void readPacket(EventPacket.Read event) {
		if (mc.player == null)
			return;

		if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket && getSetting(0).asToggle().getState()) {
			// 1.19.4 stored velocity as 3 separate ints scaled by 8000 (the raw network encoding);
			// 1.21.11 merged them into one plain Vec3d already in real blocks/tick, matching
			// mc.player.getVelocity() directly - no more manual *8000/8000d conversion needed.
			EntityVelocityUpdateS2CPacket packet = (EntityVelocityUpdateS2CPacket) event.getPacket();
			if (packet.getEntityId() == mc.player.getId()) {
				double velXZ = getSetting(0).asToggle().getChild(0).asSlider().getValue() / 100;
				double velY = getSetting(0).asToggle().getChild(1).asSlider().getValue() / 100;

				Vec3d current = mc.player.getVelocity();
				Vec3d packetVel = packet.getVelocity();

				double pvelX = (packetVel.x - current.x) * velXZ;
				double pvelY = (packetVel.y - current.y) * velY;
				double pvelZ = (packetVel.z - current.z) * velXZ;

				packet.velocity = new Vec3d(pvelX + current.x, pvelY + current.y, pvelZ + current.z);
			}
		} else if (event.getPacket() instanceof ExplosionS2CPacket && getSetting(1).asToggle().getState()) {
			// 1.19.4 had separate playerVelocityX/Y/Z float fields; 1.21.11 merged them into one
			// Optional<Vec3d> - ExplosionS2CPacket is now a record, widened mutable via accesswidener.
			ExplosionS2CPacket packet = (ExplosionS2CPacket) event.getPacket();

			double velXZ = getSetting(1).asToggle().getChild(0).asSlider().getValue() / 100;
			double velY = getSetting(1).asToggle().getChild(1).asSlider().getValue() / 100;

			packet.playerKnockback = packet.playerKnockback().map(v -> new Vec3d(v.x * velXZ, v.y * velY, v.z * velXZ));
		}
	}

	// Fluid handling in MixinFlowableFluid.getVelocity_hasNext()
}

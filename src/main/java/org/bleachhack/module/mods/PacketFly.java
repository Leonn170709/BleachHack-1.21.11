/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.event.events.EventClientMove;
import org.bleachhack.event.events.EventPacket;
import org.bleachhack.event.events.EventSendMovementPackets;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class PacketFly extends Module {

	private Vec3d cachedPos;
	private int timer = 0;

	public PacketFly() {
		super("PacketFly", KEY_UNBOUND, ModuleCategory.MOVEMENT, "Allows you to fly with packets.",
				new SettingMode("Mode", "Phase", "Packet").withDesc("Packetfly mode."),
				new SettingSlider("HSpeed", 0.05, 2, 0.5, 2).withDesc("The horizontal speed."),
				new SettingSlider("VSpeed", 0.05, 2, 0.5, 2).withDesc("The vertical speed."),
				new SettingSlider("Fall", 0, 40, 20, 0).withDesc("How often to fall (antikick)."),
				new SettingToggle("Packet Cancel", false).withDesc("Cancel rubberband packets clientside."));
	}

	@Override
	public void onEnable(boolean inWorld) {
		if (!inWorld)
			return;

		super.onEnable(inWorld);

		cachedPos = mc.player.getRootVehicle().getEntityPos();
	}

	@BleachSubscribe
	public void onMovementPackets(EventSendMovementPackets event) {
		// Packet mode moves via real velocity/collision (see onTick) and relies on Minecraft's own
		// sendMovementPackets to report the resulting position, so it must not be cancelled here.
		if (getSetting(0).asMode().getMode() == 1) {
			return;
		}

		mc.player.setVelocity(Vec3d.ZERO);
		event.setCancelled(true);
	}

	@BleachSubscribe
	public void onClientMove(EventClientMove event) {
		// Packet mode needs real, collision-resolved movement to actually go anywhere - only Phase
		// mode (which repositions the entity directly every tick) needs vanilla movement suppressed.
		if (getSetting(0).asMode().getMode() == 1) {
			return;
		}

		event.setCancelled(true);
	}

	@BleachSubscribe
	public void onReadPacket(EventPacket.Read event) {
		if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
			// 1.21.11 merged yaw/pitch into a nested EntityPosition record ("change" field) - widened
			// mutable via accesswidener, replaced via withRotation(...).
			PlayerPositionLookS2CPacket p = (PlayerPositionLookS2CPacket) event.getPacket();
			p.change = p.change().withRotation(mc.player.getYaw(), mc.player.getPitch());

			if (getSetting(4).asToggle().getState()) {
				event.setCancelled(true);
			}
		}

	}

	@BleachSubscribe
	public void onSendPacket(EventPacket.Send event) {
		// Only Phase mode hand-crafts its own position packets every tick and wants the automatic
		// ones suppressed/simplified; Packet mode needs the normal Full packets (with real look data)
		// to go out untouched.
		if (getSetting(0).asMode().getMode() != 0) {
			return;
		}

		if (event.getPacket() instanceof PlayerMoveC2SPacket.LookAndOnGround) {
			event.setCancelled(true);
			return;
		}

		if (event.getPacket() instanceof PlayerMoveC2SPacket.Full) {
			event.setCancelled(true);
			PlayerMoveC2SPacket p = (PlayerMoveC2SPacket) event.getPacket();
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(0), p.getY(0), p.getZ(0), p.isOnGround(), false));
		}
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (!mc.player.isAlive())
			return;

		double hspeed = getSetting(1).asSlider().getValue();
		double vspeed = getSetting(2).asSlider().getValue();
		timer++;

		Vec3d forward = new Vec3d(0, 0, hspeed).rotateY(-(float) Math.toRadians(mc.player.getYaw()));
		Vec3d moveVec = Vec3d.ZERO;

		// 1.21.11 replaced Input's individual pressingForward/jumping/... booleans with a single
		// PlayerInput record (input.playerInput).
		if (mc.player.input.playerInput.forward()) {
			moveVec = moveVec.add(forward);
		}
		if (mc.player.input.playerInput.backward()) {
			moveVec = moveVec.add(forward.negate());
		}
		if (mc.player.input.playerInput.jump()) {
			moveVec = moveVec.add(0, vspeed, 0);
		}
		if (mc.player.input.playerInput.sneak()) {
			moveVec = moveVec.add(0, -vspeed, 0);
		}
		if (mc.player.input.playerInput.left()) {
			moveVec = moveVec.add(forward.rotateY((float) Math.toRadians(90)));
		}
		if (mc.player.input.playerInput.right()) {
			moveVec = moveVec.add(forward.rotateY((float) -Math.toRadians(90)));
		}

		Entity target = mc.player.getRootVehicle();
		if (getSetting(0).asMode().getMode() == 0) {
			if (timer > getSetting(3).asSlider().getValue()) {
				moveVec = moveVec.add(0, -vspeed, 0);
				timer = 0;
			}

			cachedPos = cachedPos.add(moveVec);

			//target.noClip = true;
			target.updatePositionAndAngles(cachedPos.x, cachedPos.y, cachedPos.z, mc.player.getYaw(), mc.player.getPitch());
			if (target != mc.player) {
				mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(target));
			} else {
				mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cachedPos.x, cachedPos.y, cachedPos.z, false, false));
				mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cachedPos.x, cachedPos.y - 0.01, cachedPos.z, true, false));
			}
		} else if (getSetting(0).asMode().getMode() == 1) {
			// Packet mode: rather than hand-crafting position packets from an origin that never
			// advances (the old code recomputed moveVec from the player's real, unmoved position
			// every tick, so it never actually went anywhere), drive real velocity/collision like
			// Flight's Static mode does and let Minecraft's normal movement-packet code report the
			// resulting position - this also means it respects collision (no wall clipping) and
			// looks like ordinary movement to the server, unlike Phase's raw teleport packets.
			if (timer > getSetting(3).asSlider().getValue()) {
				moveVec = moveVec.add(0, -vspeed, 0);
				timer = 0;
			}

			mc.player.setVelocity(moveVec);
		}
	}

}

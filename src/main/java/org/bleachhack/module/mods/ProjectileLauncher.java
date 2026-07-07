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
import org.bleachhack.module.ModuleManager;
import org.bleachhack.setting.module.SettingItemList;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.world.WorldUtils;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from Trouser-Streak's ProjectileLauncher (https://github.com/etianl/Trouser-Streak).
 */
public class ProjectileLauncher extends Module {

	private boolean sendingItemUse;
	private boolean chargingBow;
	private int bowChargeTicks;

	public ProjectileLauncher() {
		this(new SettingToggle("Legacy Mode", false).withDesc("An older jitter-based technique that costs hunger instead of teleporting. Doesn't work on most servers - only worth trying on old Paper versions where the teleport trick fails."),
				new SettingMode("Mode", "Vanilla", "Paper").withDesc("Vanilla: reach capped at 22 blocks. Paper: a much longer reach is often possible."),
				new SettingToggle("Auto Calculate Packets", true).withDesc("Scales the spam packet count based on the clip distance instead of using a fixed amount."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the toggles other
	// settings react to as constructor parameters first lets the predicates below capture those
	// local variables instead.
	private ProjectileLauncher(SettingToggle legacyMode, SettingMode compatMode, SettingToggle autoPackets) {
		super("ProjectileLauncher", KEY_UNBOUND, ModuleCategory.EXPLOITS,
				"Teleports away and back when you use a throwable/bow, so it launches from further away.",
				legacyMode,
				new SettingSlider("Multiplier", 1, 300, 90, 0).withDesc("Legacy Mode only: higher makes it more likely to work, but costs more hunger.")
						.visibleWhen(legacyMode::getState),
				new SettingMode("Direction", "Back", "Forward").withDesc("Teleport behind you (opposite your look direction) or ahead of you before using the item.")
						.visibleWhen(() -> !legacyMode.getState()),
				compatMode.visibleWhen(() -> !legacyMode.getState()),
				new SettingSlider("Distance", 1, 22, 21, 0).withDesc("How far to teleport before using the item.")
						.visibleWhen(() -> !legacyMode.getState() && compatMode.getMode() == 0),
				new SettingSlider("Distance", 1, 199, 149, 0).withDesc("How far to teleport before using the item.")
						.visibleWhen(() -> !legacyMode.getState() && compatMode.getMode() == 1),
				new SettingSlider("Min Distance", 0.5, 21, 2, 1).withDesc("Only teleport if at least this much clear space is available.")
						.visibleWhen(() -> !legacyMode.getState()),
				autoPackets.visibleWhen(() -> !legacyMode.getState()),
				new SettingSlider("Packets", 1, 17, 4, 0).withDesc("Fixed spam packet count, used when Auto Calculate Packets is off.")
						.visibleWhen(() -> !legacyMode.getState() && !autoPackets.getState()),
				new SettingItemList("Items", "Edit Projectile Items",
						Items.ENDER_PEARL, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.EXPERIENCE_BOTTLE,
						Items.SNOWBALL, Items.EGG, Items.WIND_CHARGE).withDesc("Which throwable items this applies to."),
				new SettingToggle("Bow Machinegun", true).withDesc("Automatically releases your bow/trident as soon as it's charged enough.").withChildren(
						new SettingSlider("Charge Ticks", 4, 60, 20, 0).withDesc("How many ticks to charge before auto-releasing.")));
	}

	private double getMaxDistance() {
		return getSetting(3).asMode().getMode() == 0 ? getSetting(4).asSlider().getValue() : getSetting(5).asSlider().getValue();
	}

	@Override
	public void onDisable(boolean inWorld) {
		chargingBow = false;
		bowChargeTicks = 0;
		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (!getSetting(10).asToggle().getState()) {
			return;
		}

		boolean holdingBow = mc.player.getMainHandStack().getItem() == Items.BOW
				|| mc.player.getMainHandStack().getItem() == Items.TRIDENT
				|| mc.player.getOffHandStack().getItem() == Items.BOW
				|| mc.player.getOffHandStack().getItem() == Items.TRIDENT;

		if (!holdingBow || !chargingBow) {
			bowChargeTicks = 0;
			return;
		}

		if (++bowChargeTicks >= getSetting(10).asToggle().getChild(0).asSlider().getValueInt()) {
			mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
			bowChargeTicks = 0;
			chargingBow = false;
		}
	}

	@BleachSubscribe
	public void onSendPacket(EventPacket.Send event) {
		if (sendingItemUse) {
			return;
		}

		if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
			Item item = (packet.getHand() == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack()).getItem();

			if (getSetting(9).asList(Item.class).contains(item)) {
				if (getSetting(0).asToggle().getState()) {
					sendLegacyPackets();
				} else {
					event.setCancelled(true);
					launch(packet);
				}
			} else if (item == Items.BOW || item == Items.TRIDENT) {
				chargingBow = true;
			}
			return;
		}

		if (chargingBow && event.getPacket() instanceof PlayerActionC2SPacket packet
				&& packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
			chargingBow = false;

			if (getSetting(0).asToggle().getState()) {
				sendLegacyPackets();
			} else {
				event.setCancelled(true);
				launch(packet);
			}
		}
	}

	// Legacy Mode: instead of teleporting, spam tiny sub-pixel Y jitters while sprinting. On some
	// old Paper versions this desyncs the server's own movement/hunger tracking enough that the
	// projectile still launches with extra distance - it doesn't intercept or cancel the real
	// item-use packet, it just fires this burst right before it goes out normally.
	private void sendLegacyPackets() {
		AntiHunger antiHunger = ModuleManager.getModule(AntiHunger.class);
		boolean antiHungerWasEnabled = antiHunger.isEnabled();
		if (antiHungerWasEnabled) {
			antiHunger.toggle();
		}

		mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));

		int count = getSetting(1).asSlider().getValueInt();
		for (int i = 0; i < count; i++) {
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					mc.player.getX(), mc.player.getY() - 0.000000001, mc.player.getZ(), true, mc.player.horizontalCollision));
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					mc.player.getX(), mc.player.getY() + 0.000000001, mc.player.getZ(), false, mc.player.horizontalCollision));
		}

		if (!mc.player.isSprinting()) {
			mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
		}

		if (antiHungerWasEnabled) {
			antiHunger.toggle();
		}
	}

	private void launch(Packet<?> useItemPacket) {
		Vec3d home = mc.player.getEntityPos();
		Vec3d look = mc.player.getRotationVec(1f);
		Vec3d direction = getSetting(2).asMode().getMode() == 0 ? look.negate() : look;

		double maxDistance = getMaxDistance();
		double minDistance = getSetting(6).asSlider().getValue();

		Vec3d target = findFurthestSafePos(home, direction, maxDistance, minDistance);
		if (target == null) {
			// try the opposite direction before giving up
			target = findFurthestSafePos(home, direction.negate(), maxDistance, minDistance);
		}
		if (target == null) {
			mc.player.networkHandler.sendPacket(useItemPacket);
			return;
		}

		spamStatusPackets(home.distanceTo(target));

		WorldUtils.sendTeleport(target);
		sendingItemUse = true;
		mc.player.networkHandler.sendPacket(useItemPacket);
		sendingItemUse = false;

		WorldUtils.sendTeleport(home);
		WorldUtils.sendTeleport(WorldUtils.findSafeOffset(mc.player, home, 0.05, 0.001));
	}

	private Vec3d findFurthestSafePos(Vec3d start, Vec3d direction, double maxDistance, double minDistance) {
		for (double dist = maxDistance; dist >= minDistance; dist -= 0.5) {
			Vec3d candidate = start.add(direction.multiply(dist));
			if (!WorldUtils.isTeleportUnsafe(mc.player, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	// Auto Calculate Packets scales the spam count with the actual clip distance - a longer clip
	// is a bigger, more suspicious jump and needs more "you're grounded" spam beforehand.
	private void spamStatusPackets(double distance) {
		int count = getSetting(7).asToggle().getState()
				? Math.min(17, Math.max(1, (int) Math.ceil(distance / 5.0)))
				: getSetting(8).asSlider().getValueInt();

		for (int i = 0; i < count; i++) {
			mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
		}
	}

}

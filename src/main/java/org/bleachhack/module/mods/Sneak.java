package org.bleachhack.module.mods;

import org.bleachhack.event.events.EventPacket;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;

import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;

public class Sneak extends Module {

	public Sneak() {
		super("Sneak", KEY_UNBOUND, ModuleCategory.MOVEMENT, "Makes you automatically sneak.",
				new SettingMode("Mode", "Legit", "Packet").withDesc("Mode for sneaking (Only other players will see u sneaking with packet mode)."));
	}

	@Override
	public void onDisable(boolean inWorld) {
		mc.options.sneakKey.setPressed(false);

		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (getSetting(0).asMode().getMode() == 0) {
			mc.options.sneakKey.setPressed(true);
		}
	}

	// ported: 1.19.4 toggled sneaking server-side via a discrete ClientCommandC2SPacket press/release
	// command. That packet mode was removed; sneak is now one field of the continuous per-tick
	// PlayerInputC2SPacket, so "packet mode" now works by rewriting that field on every outgoing
	// packet instead of sending/cancelling a one-off command.
	@BleachSubscribe
	public void onSendPacket(EventPacket.Send event) {
		if (getSetting(0).asMode().getMode() == 1 && event.getPacket() instanceof PlayerInputC2SPacket p && !p.input().sneak()) {
			PlayerInput i = p.input();
			event.setPacket(new PlayerInputC2SPacket(new PlayerInput(i.forward(), i.backward(), i.left(), i.right(), i.jump(), true, i.sprint())));
		}
	}
}

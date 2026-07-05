/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.bleachhack.BleachHack;
import org.bleachhack.command.Command;
import org.bleachhack.event.events.EventKeyPress;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.setting.option.Option;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;

@Mixin(Keyboard.class)
public class MixinKeyboard {

	// onKey's signature changed from (long, int key, int scanCode, int action, int modifiers) to
	// (long, int action, KeyInput input) - key/scanCode/modifiers moved into the KeyInput record.
	// The old second injection point (an InputUtil.isKeyPressed call, ordinal 5, inside the F3-debug-key
	// handling block near the top of the method) no longer exists - 1.21.11's onKey only calls
	// isKeyPressed once (ordinal 0, for the debug crash key). Both hooks just need to run early/
	// unconditionally before Minecraft's own key handling can consume the key, so both are HEAD now.
	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
	private void onKeyEvent(long windowPointer, int action, KeyInput input, CallbackInfo callbackInfo) {
		if (input.key() >= 0) {
			EventKeyPress.Global event = new EventKeyPress.Global(input.key(), input.scancode(), action, input.modifiers());
			BleachHack.eventBus.post(event);

			if (event.isCancelled()) {
				callbackInfo.cancel();
			}
		}
	}

	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
	private void onKeyEvent_1(long windowPointer, int action, KeyInput input, CallbackInfo callbackInfo) {
		// Module keybinds (handleKey) and the quick-prefix chat trigger must only fire once per actual
		// press - @At("HEAD") means this now runs for press/release/repeat alike, unlike the old
		// isKeyPressed-ordinal anchor point which happened to only ever run within a press-gated block.
		if (action != InputUtil.GLFW_PRESS) {
			return;
		}

		if (Option.CHAT_QUICK_PREFIX.getValue() && Command.getPrefix().length() == 1 && input.key() == Command.getPrefix().charAt(0)) {
			MinecraftClient.getInstance().setScreen(new ChatScreen(Command.getPrefix(), false));
		}

		ModuleManager.handleKey(input.key());

		if (input.key() >= 0) {
			EventKeyPress.InWorld event = new EventKeyPress.InWorld(input.key(), input.scancode());
			BleachHack.eventBus.post(event);

			if (event.isCancelled()) {
				callbackInfo.cancel();
			}
		}
	}
}

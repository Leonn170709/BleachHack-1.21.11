/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import java.util.Locale;
import java.util.Set;

import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import org.bleachhack.event.events.EventRenderShader;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;

import net.minecraft.util.Identifier;

public class ShaderRender extends Module {

	// 1.21.11 removed most of the old "Super Secret Settings" post-effect shaders from the vanilla
	// jar - Invert/Blur/Creeper/Spider are the only 4 of the original 24 modes that still ship as
	// real assets/minecraft/post_effect/json files. The rest have been ported from Minecraft
	// 1.19.4's own (Mojang) GLSL/JSON assets against the new post-effect pipeline - see
	// src/main/resources/assets/bleachhack/{post_effect,shaders/post}. Art/Flip/NTSC/Outline/
	// Phosphor are still missing (multi-pass chains with frame-feedback targets or a custom vertex
	// shader - not ported yet); picking one of those just leaves the world unshaded instead of
	// crashing. Settings stay identical to 1.19.4 so saved configs don't break.
	private static final Set<String> VANILLA_STILL_AVAILABLE = Set.of("invert", "blur", "creeper", "spider");
	private static final Set<String> PORTED = Set.of("notch", "fxaa", "bumpy", "blobs", "blobs2", "pencil", "vibrant",
			"deconverge", "scanline", "sobel", "bits", "desaturate", "green", "wobble", "antialias");

	private String lastMode = null;
	private PostEffectProcessor lastShader = null;

	public ShaderRender() {
		super("ShaderRender", KEY_UNBOUND, ModuleCategory.RENDER, "Applies a full-screen shader effect to the game (Minecraft's old vanilla shader packs).",
				new SettingMode("Shader", "Notch", "FXAA", "Art", "Bumpy", "Blobs", "Blobs2", "Pencil", "Vibrant",
						"Deconverge", "Flip", "Invert", "NTSC", "Outline", "Phosphor", "Scanline", "Sobel",
						"Bits", "Desaturate", "Green", "Blur", "Wobble", "Antialias", "Creeper", "Spider").withDesc("Shader to use."));
	}

	@BleachSubscribe
	public void onWorldRender(EventRenderShader event) {
		String[] modes = getSetting(0).asMode().modes;
		String mode = modes[getSetting(0).asMode().getMode()].toLowerCase(Locale.ENGLISH);

		if (!mode.equals(lastMode)) {
			lastMode = mode;

			if (lastShader != null) {
				lastShader.close();
				lastShader = null;
			}

			if (VANILLA_STILL_AVAILABLE.contains(mode)) {
				lastShader = mc.getShaderLoader().loadPostEffect(Identifier.of("minecraft", mode), DefaultFramebufferSet.MAIN_ONLY);
			} else if (PORTED.contains(mode)) {
				lastShader = mc.getShaderLoader().loadPostEffect(Identifier.of("bleachhack", mode), DefaultFramebufferSet.MAIN_ONLY);
			}
		}

		event.setEffect(lastShader);
	}

}

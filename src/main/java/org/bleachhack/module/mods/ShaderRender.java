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
import org.bleachhack.event.events.EventRenderShader;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;

import net.minecraft.util.Identifier;

public class ShaderRender extends Module {

	// 1.21.11 removed most of the old "Super Secret Settings" post-effect shaders from the vanilla
	// jar - only these 4 of the original 24 modes still ship as real assets/minecraft/post_effect/
	// json files. The rest (Notch/FXAA/Art/Bumpy/Blobs/Blobs2/Pencil/Vibrant/Deconverge/Flip/NTSC/
	// Outline/Phosphor/Scanline/Sobel/Bits/Desaturate/Green/Wobble/Antialias) have no vanilla GLSL/
	// JSON asset left to reference at all any more; recreating them means re-authoring ~20 post-effect
	// chains from scratch against the new pipeline. Settings stay identical to 1.19.4 so saved configs
	// don't break; picking a removed mode just leaves the world unshaded instead of crashing.
	private static final Set<String> STILL_AVAILABLE = Set.of("invert", "blur", "creeper", "spider");

	private String lastMode = null;
	private PostEffectProcessor lastShader = null;

	public ShaderRender() {
		super("ShaderRender", KEY_UNBOUND, ModuleCategory.RENDER, "1.7 Super secret settings.",
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

			if (STILL_AVAILABLE.contains(mode)) {
				lastShader = mc.getShaderLoader().loadPostEffect(Identifier.of("minecraft", mode), Set.of());
			}
		}

		event.setEffect(lastShader);
	}

}

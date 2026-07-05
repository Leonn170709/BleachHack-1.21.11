/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.memory.ObjectPool;
import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventRenderShader;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

	@Shadow private MinecraftClient client;
	@Shadow private Identifier postProcessorId;
	@Shadow private boolean postProcessorEnabled;
	@Shadow private ObjectPool pool;

	// 1.21.11 replaced GameRenderer's `postProcessor` field (a live PostEffectProcessor instance) with
	// a lazily-loaded `postProcessorId`/`postProcessorEnabled` pair, so there's no single field read left
	// to redirect. This flag lets us suppress vanilla's own render call for exactly one frame after we've
	// already rendered our own (possibly substituted) effect, instead of double-rendering it.
	private boolean bleachhack_renderedCustomEffect;

	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void onTiltViewWhenHurt(MatrixStack matrixStack, float f, CallbackInfo ci) {
		if (ModuleManager.getModule(NoRender.class).isOverlayToggled(2)) {
			ci.cancel();
		}
	}

	@Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
	private void showFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
		if (ModuleManager.getModule(NoRender.class).isWorldToggled(1) && floatingItem.getItem() == Items.TOTEM_OF_UNDYING) {
			ci.cancel();
		}
	}

	@Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F", ordinal = 0),
			require = 0 /* TODO: meteor compatibility */)
	private float nauseaWobble(float delta, float first, float second) {
		if (ModuleManager.getModule(NoRender.class).isOverlayToggled(5)) {
			return 0;
		}

		return MathHelper.lerp(delta, first, second);
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V", shift = At.Shift.AFTER))
	private void render_Shader(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		PostEffectProcessor current = postProcessorId != null
				? client.getShaderLoader().loadPostEffect(postProcessorId, DefaultFramebufferSet.MAIN_ONLY)
				: null;

		EventRenderShader event = new EventRenderShader(current);
		BleachHack.eventBus.post(event);

		bleachhack_renderedCustomEffect = event.getEffect() != null;

		if (event.getEffect() != null) {
			event.getEffect().render(client.getFramebuffer(), pool);
		}
	}

	@Redirect(method = "render", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/render/GameRenderer;postProcessorEnabled:Z"))
	private boolean render_suppressVanillaShaderDoubleRender(GameRenderer renderer) {
		if (bleachhack_renderedCustomEffect) {
			bleachhack_renderedCustomEffect = false;
			return false;
		}

		return postProcessorEnabled;
	}
}

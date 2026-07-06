/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventWorldRender;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingColor;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.render.Renderer;
import org.bleachhack.util.render.color.QuadColor;
import org.bleachhack.util.world.EntityUtils;

import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;

public class ESP extends Module {

	// This module's own handle to vanilla's "entity_outline" post effect - rebuilt (see
	// applyShaderSettings) with fresh uniform values whenever the Shader-mode settings below change,
	// since post-effect uniforms are otherwise baked once at resource-load time.
	private static final Identifier ENTITY_OUTLINE = DefaultFramebufferSet.ENTITY_OUTLINE;
	private final ProjectionMatrix2 shaderProjection = new ProjectionMatrix2("bleachhack_entity_outline", 0.1F, 1000.0F, false);
	private float lastWidth = -1, lastGlow = -1, lastFill = -1, lastShape = -1;

	public ESP() {
		super("ESP", KEY_UNBOUND, ModuleCategory.RENDER, "Highlights Entities in the world.",
				new SettingMode("Render", "Shader", "Box").withDesc("The Render mode."),
				new SettingSlider("ShaderWidth", 1, 10, 4, 0).withDesc("How many pixels the shader outline glow should reach (Meteor's outlineWidth)."),
				new SettingSlider("ShaderGlow", 0, 10, 3, 1).withDesc("The glow multiplier of the shader outline (Meteor's glowMultiplier)."),
				new SettingSlider("ShaderFill", 1, 255, 100, 0).withDesc("How opaque the fill on shader mode should be (Meteor's fillOpacity)."),
				// SettingMode always defaults to its first listed option, so "Lines+Sides" (the fullest
				// effect, matching Meteor's own default) has to be listed first to actually be the default.
				new SettingMode("ShaderShape", "Lines+Sides", "Lines", "Sides").withDesc("Whether the shader mode draws the glow, the fill, or both (Meteor's shapeMode)."),
				new SettingSlider("Box", 0, 5, 2, 1).withDesc("How thick the box outline should be."),
				new SettingSlider("BoxFill", 0, 255, 50, 0).withDesc("How opaque the fill on box mode should be."),

				new SettingToggle("Players", true).withDesc("Highlights Players.").withChildren(
						new SettingColor("Player Color", 255, 75, 75).withDesc("Outline color for players."),
						new SettingColor("Friend Color", 0, 255, 255).withDesc("Outline color for friends.")),

				new SettingToggle("Mobs", false).withDesc("Highlights Mobs.").withChildren(
						new SettingColor("Color", 128, 25, 128).withDesc("Outline color for mobs.")),

				new SettingToggle("Animals", false).withDesc("Highlights Animals").withChildren(
						new SettingColor("Color", 75, 255, 75).withDesc("Outline color for animals.")),

				new SettingToggle("Items", true).withDesc("Highlights Items.").withChildren(
						new SettingColor("Color", 255, 200, 50).withDesc("Outline color for items.")),

				new SettingToggle("Crystals", true).withDesc("Highlights End Crystals.").withChildren(
						new SettingColor("Color", 255, 50, 255).withDesc("Outline color for crystals.")),

				new SettingToggle("Vehicles", false).withDesc("Highlights Vehicles.").withChildren(
						new SettingColor("Color", 150, 150, 150).withDesc("Outline color for vehicles (minecarts/boats).")),

				new SettingToggle("Armorstands", false).withDesc("Highlights armor stands.").withChildren(
						new SettingColor("Color", 160, 150, 50).withDesc("Outline color for armor stands.")));
	}

	@BleachSubscribe
	public void onWorldRender(EventWorldRender.Post event) {
		// "Shader" mode needs no drawing here at all - MixinEntityRenderer sets each highlighted
		// entity's EntityRenderState.outlineColor directly, which makes vanilla's own Glowing-effect
		// outline framebuffer/shader (entity_outline post-effect) draw a real through-walls silhouette
		// outline for it automatically, matching Meteor's "Shader" ESP mode without needing a custom
		// framebuffer/shader/vertex-consumer of our own.
		if (getSetting(0).asMode().getMode() == 0) {
			applyShaderSettings();
			return;
		}

		float width = getSetting(5).asSlider().getValueFloat();
		int fill = getSetting(6).asSlider().getValueInt();

		for (Entity e: mc.world.getEntities()) {
			int[] color = getColor(e);

			if (color != null) {
				if (width != 0)
					Renderer.drawBoxOutline(e.getBoundingBox(), QuadColor.single(color[0], color[1], color[2], 255), width);

				if (fill != 0)
					Renderer.drawBoxFill(e.getBoundingBox(), QuadColor.single(color[0], color[1], color[2], fill));
			}
		}
	}

	// Post-effect uniforms are otherwise only read once (at resource-load time), so ESP's Shader-mode
	// sliders would silently do nothing after the initial load. This rebuilds vanilla's "entity_outline"
	// post effect from Meteor's outline algorithm with the module's current settings baked in, and swaps
	// it into ShaderLoader's cache in place of whatever's currently loaded there - but only when a value
	// actually changed, since rebuilding allocates a new GPU pipeline/uniform buffer.
	private void applyShaderSettings() {
		float width = getSetting(1).asSlider().getValueFloat();
		float glow = getSetting(2).asSlider().getValueFloat();
		float fill = getSetting(3).asSlider().getValueInt() / 255f;
		float shape = getSetting(4).asMode().getMode();

		if (width == lastWidth && glow == lastGlow && fill == lastFill && shape == lastShape) {
			return;
		}

		// Resource loading (and with it, our custom fragment shader's source) may not be ready yet this
		// early - e.g. right after enabling the module at the title screen, before the client's first
		// resource reload has finished. Skip silently without marking these values as applied, so this
		// retries again next frame instead of getting stuck with a broken pipeline.
		if (mc.getShaderLoader().getSource(Identifier.of("bleachhack", "post/entity_outline_meteor"), com.mojang.blaze3d.shaders.ShaderType.FRAGMENT) == null) {
			return;
		}

		lastWidth = width;
		lastGlow = glow;
		lastFill = fill;
		lastShape = shape;

		Identifier swap = Identifier.ofVanilla("swap");
		Map<String, List<UniformValue>> outlineUniforms = Map.of("OutlineConfig", List.of(
				new UniformValue.FloatValue(width),
				new UniformValue.FloatValue(fill),
				new UniformValue.FloatValue(glow),
				new UniformValue.FloatValue(shape)));
		Map<String, List<UniformValue>> blitUniforms = Map.of("BlitConfig", List.of(
				new UniformValue.Vec4fValue(new org.joml.Vector4f(1, 1, 1, 1))));

		PostEffectPipeline.Pass outlinePass = new PostEffectPipeline.Pass(
				Identifier.ofVanilla("core/screenquad"), Identifier.of("bleachhack", "post/entity_outline_meteor"),
				List.of(new PostEffectPipeline.TargetSampler("In", ENTITY_OUTLINE, false, false)), swap, outlineUniforms);
		PostEffectPipeline.Pass blitPass = new PostEffectPipeline.Pass(
				Identifier.ofVanilla("core/screenquad"), Identifier.ofVanilla("post/blit"),
				List.of(new PostEffectPipeline.TargetSampler("In", swap, false, false)), ENTITY_OUTLINE, blitUniforms);

		PostEffectPipeline pipeline = new PostEffectPipeline(
				Map.of(swap, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
				List.of(outlinePass, blitPass));

		try {
			PostEffectProcessor processor = PostEffectProcessor.parseEffect(
					pipeline, mc.getTextureManager(), DefaultFramebufferSet.MAIN_AND_ENTITY_OUTLINE, ENTITY_OUTLINE, shaderProjection);

			ShaderLoader.Cache cache = mc.getShaderLoader().cache;
			Optional<PostEffectProcessor> old = cache.postEffectProcessors.put(ENTITY_OUTLINE, Optional.of(processor));
			if (old != null) {
				old.ifPresent(PostEffectProcessor::close);
			}
		} catch (ShaderLoader.LoadException e) {
			e.printStackTrace();
		}
	}

	public int[] getColor(Entity e) {
		if (e == mc.player)
			return null;

		if (e instanceof PlayerEntity && getSetting(7).asToggle().getState()) {
			return getSetting(7).asToggle().getChild(BleachHack.friendMang.has(e) ? 1 : 0).asColor().getRGBArray();
		} else if (e instanceof Monster && getSetting(8).asToggle().getState()) {
			return getSetting(8).asToggle().getChild(0).asColor().getRGBArray();
		} else if (EntityUtils.isAnimal(e) && getSetting(9).asToggle().getState()) {
			return getSetting(9).asToggle().getChild(0).asColor().getRGBArray();
		} else if (e instanceof ItemEntity && getSetting(10).asToggle().getState()) {
			return getSetting(10).asToggle().getChild(0).asColor().getRGBArray();
		} else if (e instanceof EndCrystalEntity && getSetting(11).asToggle().getState()) {
			return getSetting(11).asToggle().getChild(0).asColor().getRGBArray();
		} else if ((e instanceof BoatEntity || e instanceof AbstractMinecartEntity) && getSetting(12).asToggle().getState()) {
			return getSetting(12).asToggle().getChild(0).asColor().getRGBArray();
		} else if (e instanceof ArmorStandEntity && getSetting(13).asToggle().getState()) {
			return getSetting(13).asToggle().getChild(0).asColor().getRGBArray();
		}

		return null;
	}
}
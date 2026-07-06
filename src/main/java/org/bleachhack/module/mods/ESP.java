/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

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
import org.bleachhack.util.render.ShaderEspRenderer;
import org.bleachhack.util.render.WireframeEntityRenderer;
import org.bleachhack.util.render.color.QuadColor;
import org.bleachhack.util.world.EntityUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class ESP extends Module {

	public ESP() {
		this(new SettingMode("Render", "Shader", "Box", "Wireframe").withDesc("The Render mode."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the Render mode
	// setting as a constructor parameter first lets the predicates below capture that local
	// variable instead.
	private ESP(SettingMode renderMode) {
		super("ESP", KEY_UNBOUND, ModuleCategory.RENDER, "Highlights Entities in the world.",
				renderMode,
				new SettingSlider("Box", 0, 5, 2, 1).withDesc("How thick the box/wireframe outline should be.")
						.visibleWhen(() -> renderMode.getMode() != 0),
				new SettingSlider("BoxFill", 0, 255, 50, 0).withDesc("How opaque the fill on box/wireframe mode should be.")
						.visibleWhen(() -> renderMode.getMode() != 0),

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
		int mode = getSetting(0).asMode().getMode();
		float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

		if (mode == 0) {
			// Shader - a real silhouette-framebuffer + Meteor glow shader, independent of vanilla's
			// own Glowing-effect outline (see ShaderEspRenderer).
			Map<Entity, int[]> matched = new LinkedHashMap<>();
			for (Entity e: mc.world.getEntities()) {
				int[] color = getColor(e);
				if (color != null) {
					matched.put(e, color);
				}
			}

			ShaderEspRenderer.render(matched, tickDelta);
			return;
		}

		float width = getSetting(1).asSlider().getValueFloat();
		int fill = getSetting(2).asSlider().getValueInt();

		for (Entity e: mc.world.getEntities()) {
			int[] color = getColor(e);

			if (color == null) {
				continue;
			}

			if (mode == 2) {
				// Wireframe - Meteor draws the entity's actual posed model as a wireframe here instead
				// of a bounding box (see WireframeEntityRenderer), reusing the same width/fill sliders.
				WireframeEntityRenderer.render(e, tickDelta, color[0], color[1], color[2], fill, width);
				continue;
			}

			if (width != 0)
				Renderer.drawBoxOutline(e.getBoundingBox(), QuadColor.single(color[0], color[1], color[2], 255), width);

			if (fill != 0)
				Renderer.drawBoxFill(e.getBoundingBox(), QuadColor.single(color[0], color[1], color[2], fill));
		}
	}

	public int[] getColor(Entity e) {
		if (e == mc.player)
			return null;

		if (e instanceof PlayerEntity && getSetting(3).asToggle().getState()) {
			return getSetting(3).asToggle().getChild(BleachHack.friendMang.has(e) ? 1 : 0).asColor().getRGBArray();
		} else if (e instanceof Monster && getSetting(4).asToggle().getState()) {
			return getSetting(4).asToggle().getChild(0).asColor().getRGBArray();
		} else if (EntityUtils.isAnimal(e) && getSetting(5).asToggle().getState()) {
			return getSetting(5).asToggle().getChild(0).asColor().getRGBArray();
		} else if (e instanceof ItemEntity && getSetting(6).asToggle().getState()) {
			return getSetting(6).asToggle().getChild(0).asColor().getRGBArray();
		} else if (e instanceof EndCrystalEntity && getSetting(7).asToggle().getState()) {
			return getSetting(7).asToggle().getChild(0).asColor().getRGBArray();
		} else if ((e instanceof BoatEntity || e instanceof AbstractMinecartEntity) && getSetting(8).asToggle().getState()) {
			return getSetting(8).asToggle().getChild(0).asColor().getRGBArray();
		} else if (e instanceof ArmorStandEntity && getSetting(9).asToggle().getState()) {
			return getSetting(9).asToggle().getChild(0).asColor().getRGBArray();
		}

		return null;
	}
}
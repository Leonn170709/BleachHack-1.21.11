/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import net.minecraft.entity.mob.MobEntity;
import org.bleachhack.event.events.EventLightTex;
import org.bleachhack.event.events.EventRenderBlock;
import org.bleachhack.event.events.EventRenderFluid;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingBlockList;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.world.WorldUtils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShortPlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.VertexConsumer;

public class Xray extends Module {

	private double gamma;

	public Xray() {
		super("Xray", KEY_UNBOUND, ModuleCategory.RENDER, "Makes chosen blocks (ores by default) visible through terrain.",
				new SettingToggle("Fluids", false).withDesc("Show fluids."),
				new SettingToggle("Opacity", true).withDesc("Toggles an adjustable alpha level for non-xray blocks.").withChildren(
						new SettingSlider("Value", 0, 255, 64, 0).withDesc("Block alpha value."),
						new SettingToggle("HideSurface", false).withDesc("Hides the surface of the world to make it easier to see blocks.")),
				new SettingBlockList("Edit Blocks", "Edit Xray Blocks",
						Blocks.COPPER_ORE,
						Blocks.IRON_ORE,
						Blocks.GOLD_ORE,
						Blocks.LAPIS_ORE,
						Blocks.REDSTONE_ORE,
						Blocks.DIAMOND_ORE,
						Blocks.EMERALD_ORE,
						Blocks.DEEPSLATE_COPPER_ORE,
						Blocks.DEEPSLATE_IRON_ORE,
						Blocks.DEEPSLATE_GOLD_ORE,
						Blocks.DEEPSLATE_LAPIS_ORE,
						Blocks.DEEPSLATE_REDSTONE_ORE,
						Blocks.DEEPSLATE_DIAMOND_ORE,
						Blocks.DEEPSLATE_EMERALD_ORE,
						Blocks.COPPER_BLOCK,
						Blocks.IRON_BLOCK,
						Blocks.GOLD_BLOCK,
						Blocks.LAPIS_BLOCK,
						Blocks.REDSTONE_BLOCK,
						Blocks.DIAMOND_BLOCK,
						Blocks.EMERALD_BLOCK,
						Blocks.NETHER_GOLD_ORE,
						Blocks.ANCIENT_DEBRIS).withDesc("Edit the xray blocks."));
	}

	@Override
	public void onEnable(boolean inWorld) {
		super.onEnable(inWorld);

		mc.chunkCullingEnabled = false;
		mc.worldRenderer.reload();

		gamma = mc.options.getGamma().getValue();
	}

	@Override
	public void onDisable(boolean inWorld) {
		mc.options.getGamma().setValue(gamma);

		mc.chunkCullingEnabled = true;
		mc.worldRenderer.reload();

		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onBrightness(EventLightTex.Brightness event) {
		event.setBrightness(1f);
	}

	@BleachSubscribe
	public void onRenderBlockLight(EventRenderBlock.Light event) {
		event.setLight(1f);
	}

	@BleachSubscribe
	public void onRenderBlockOpaque(EventRenderBlock.Opaque event) {
		event.setOpaque(true);
	}

	@BleachSubscribe
	public void onRenderBlockDrawSide(EventRenderBlock.ShouldDrawSide event) {
		boolean isTarget = getSetting(2).asList(Block.class).contains(event.getState().getBlock());
		boolean neighborIsTarget = getSetting(2).asList(Block.class).contains(event.getNeighborState().getBlock());

		if (isTarget) {
			// Always reveal target blocks (even fully enclosed in solid terrain), except the shared
			// face between two touching target blocks of the same type - nothing to see there.
			event.setDrawSide(!neighborIsTarget || event.getNeighborState().getBlock() != event.getState().getBlock());
		} else if (!getSetting(1).asToggle().getState()) {
			event.setDrawSide(false);
		} else if (neighborIsTarget) {
			// Also draw a non-target block's face where it directly touches a target block, so the
			// target isn't sitting in a fully culled (invisible) socket of translucent terrain.
			event.setDrawSide(true);
		}
	}

	@BleachSubscribe
	public void onRenderBlockTesselate(EventRenderBlock.Tesselate event) {
		if (!getSetting(2).asList(Block.class).contains(event.getState().getBlock())) {
			if (getSetting(1).asToggle().getState()) {
				if (getSetting(1).asToggle().getChild(1).asToggle().getState()
						&& (event.getState().getBlock() instanceof ShortPlantBlock
								|| event.getState().getBlock() instanceof TallPlantBlock
								|| WorldUtils.getTopBlockIgnoreLeaves(event.getPos().getX(), event.getPos().getZ()) == event.getPos().getY())) {
					event.setCancelled(true);
					return;
				}

				event.setVertexConsumer(new FixedAlphaVertexConsumer(event.getVertexConsumer(), getSetting(1).asToggle().getChild(0).asSlider().getValueInt()));
			} else {
				event.setCancelled(true);
			}
		}
	}

	@BleachSubscribe
	public void onRenderBlockLayer(EventRenderBlock.Layer event) {
		if (getSetting(1).asToggle().getState() && !getSetting(2).asList(Block.class).contains(event.getState().getBlock())) {
			event.setLayer(BlockRenderLayer.TRANSLUCENT);
		}
	}

	@BleachSubscribe
	public void onRenderFluid(EventRenderFluid event) {
		if (!getSetting(0).asToggle().getState()) {
			event.setCancelled(true);
		}
	}

	// 1.21.11 removed BufferBuilder's stateful fixedColor()/BufferVertexConsumer trick this module
	// used to force block transparency - this wrapping decorator (swapped in via
	// EventRenderBlock.Tesselate#setVertexConsumer) is the direct replacement: same effect, just an
	// immutable wrapper instead of an in-place field flip.
	private static class FixedAlphaVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int alpha;

		private FixedAlphaVertexConsumer(VertexConsumer delegate, int alpha) {
			this.delegate = delegate;
			this.alpha = alpha;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			return delegate.vertex(x, y, z);
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return delegate.color(red, green, blue, this.alpha);
		}

		@Override
		public VertexConsumer color(int argb) {
			return delegate.color((argb & 0xFFFFFF) | (this.alpha << 24));
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			return delegate.texture(u, v);
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return delegate.overlay(u, v);
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return delegate.light(u, v);
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return delegate.normal(x, y, z);
		}

		@Override
		public VertexConsumer lineWidth(float width) {
			return delegate.lineWidth(width);
		}
	}
}

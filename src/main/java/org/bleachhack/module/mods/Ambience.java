/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import org.bleachhack.event.events.EventBiomeColor;
import org.bleachhack.event.events.EventPacket;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingColor;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;

import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

public class Ambience extends Module {

	private final WeatherManager weatherManager = new WeatherManager();

	public Ambience() {
		super("Ambience", KEY_UNBOUND, ModuleCategory.WORLD, "Changes the world ambience.",
				new SettingToggle("Weather", true).withDesc("Changes the world weather.").withChildren(
						new SettingMode("Weather", "Clear", "Rain").withDesc("What weather to use."),
						new SettingSlider("Rain", 0, 2, 0, 2).withDesc("How much it should rain in rain mode.")),
				new SettingToggle("Time", false).withDesc("Changes the world time.").withChildren(
						new SettingSlider("Time", 0, 24000, 12500, 0).withDesc("What time to set the world to.")),
				new SettingToggle("Overworld", true).withDesc("Changes the overworld ambience-").withChildren(
						new SettingToggle("Sky Color", true).withDesc("Changes the overworld sky color.").withChildren(
								new SettingToggle("End Skybox", false).withDesc("2B2T QUeue SKY=!?!?!?"),
								new SettingColor("Sky Color", 128, 255, 128).withDesc("Main color of the sky.")),
						new SettingToggle("Foilage Color", false).withDesc("Changes the foilage color.").withChildren(
								new SettingColor("Color", 128, 255, 128).withDesc("The color of the foilage.")),
						new SettingToggle("Water Color", false).withDesc("Changes the water color.").withChildren(
								new SettingColor("Color", 128, 255, 128).withDesc("Color of the water."))),
				new SettingToggle("Nether", true).withDesc("Changes the nether ambience.").withChildren(
						new SettingToggle("Sky Color", true).withDesc("Changes the nether sky color.").withChildren(
								new SettingToggle("End Skybox", false).withDesc("2B2T QUeue SKY=!?!?!?"),
								new SettingColor("Sky Color", 128, 255, 128).withDesc("Main color of the sky.")),
						new SettingToggle("Foilage Color", false).withDesc("Changes the foilage color.").withChildren(
								new SettingColor("Color", 128, 255, 128).withDesc("The color of the foilage.")),
						new SettingToggle("Water Color", false).withDesc("Changes the water color").withChildren(
								new SettingColor("Color", 128, 255, 128).withDesc("The color of the water."))),
				new SettingToggle("End", true).withDesc("Changes the end ambience.").withChildren(
						new SettingToggle("Sky Color", true).withDesc("Changes the end sky color.").withChildren(
								new SettingToggle("End Skybox", false).withDesc("2B2T QUeue SKY=!?!?!?"),
								new SettingColor("Sky Color", 128, 255, 128).withDesc("Main color of the sky.")),
						new SettingToggle("Foilage Color", false).withDesc("Changes the foilage color.").withChildren(
								new SettingColor("Color", 128, 255, 128).withDesc("The color of the foilage.")),
						new SettingToggle("Water Color", false).withDesc("Changes the water color.").withChildren(
								new SettingColor("Color", 128, 255, 128).withDesc("The color of the water."))));
	}

	@Override
	public void onEnable(boolean inWorld) {
		super.onEnable(inWorld);

		if (inWorld) {
			// Grass/foliage/water color are baked into each chunk's mesh at build time, not looked
			// up live every frame - already-built chunks keep whatever color they had before this
			// toggled on until something rebuilds them, which makes the override look like it only
			// works in "some" chunks (whichever happen to rebuild afterward). Forcing every loaded
			// chunk to rebuild now makes it apply everywhere immediately, matching Xray's onEnable.
			mc.worldRenderer.reload();
		}
	}

	@Override
	public void onDisable(boolean inWorld) {
		if (inWorld) {
			weatherManager.applyWeather(mc.world);
			mc.worldRenderer.reload();
		}

		weatherManager.reset();

		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (getSetting(0).asToggle().getState()) {
			if (!weatherManager.isActive()) {
				weatherManager.setRain(mc.world.getRainGradient(mc.getRenderTickCounter().getTickProgress(true)));
				weatherManager.setThunder(mc.world.getThunderGradient(mc.getRenderTickCounter().getTickProgress(true)));
			}

			if (getSetting(0).asToggle().getChild(0).asMode().getMode() == 0) {
				mc.world.getLevelProperties().setRaining(false);
				mc.world.setRainGradient(0f);
			} else {
				mc.world.getLevelProperties().setRaining(true);
				mc.world.setRainGradient(getSetting(0).asToggle().getChild(1).asSlider().getValueFloat());
			}
		} else if (weatherManager.isActive()) {
			weatherManager.applyWeather(mc.world);
			weatherManager.reset();
		}

		if (getSetting(1).asToggle().getState()) {
			mc.world.getLevelProperties().setTimeOfDay(getSetting(1).asToggle().getChild(0).asSlider().getValueLong());
		}
	}

	@BleachSubscribe
	public void readPacket(EventPacket.Read event) {
		if (event.getPacket() instanceof GameStateChangeS2CPacket && getSetting(0).asToggle().getState()) {
			GameStateChangeS2CPacket packet = (GameStateChangeS2CPacket) event.getPacket();
			if (packet.getReason() == GameStateChangeS2CPacket.RAIN_STARTED) {
				weatherManager.setRain(1f);
			} else if (packet.getReason() == GameStateChangeS2CPacket.RAIN_STOPPED) {
				weatherManager.setRain(0f);
			} else if (packet.getReason() == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED) {
				weatherManager.setRain(packet.getValue());
			} else if (packet.getReason() == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED) {
				weatherManager.setThunder(packet.getValue());
			} else {
				return;
			}

			event.setCancelled(true);
		} else if (event.getPacket() instanceof DisconnectS2CPacket && getSetting(0).asToggle().getState()) {
			weatherManager.reset();
		} else if (event.getPacket() instanceof WorldTimeUpdateS2CPacket && getSetting(1).asToggle().getState()) {
			event.setCancelled(true);
		}
	}

	@BleachSubscribe
	public void onBiomeColor(EventBiomeColor event) {
		int type = event instanceof EventBiomeColor.Water ? 2 : 1;

		if (getCurrentDimSetting().getState() && getCurrentDimSetting().getChild(type).asToggle().getState()) {
			event.setColor(getCurrentDimSetting().getChild(type).asToggle().getChild(0).asColor().getRGB());
		}
	}

	// 1.21.11 replaced the old per-property DimensionEffects subclassing model (getSkyColor/
	// getCloudsColor/getDimensionEffects/getFogColorOverride) with a generic, position/biome-weighted
	// EnvironmentAttributes system (World.getEnvironmentAttributes().getAttributeValue(...)). The old
	// ClientWorld/DimensionEffects mixins that fired these hooks had to be removed since their target
	// methods no longer exist. Sky color and skybox choice are now overridden directly at render time
	// instead, from MixinSkyRendering (see getSkyColorOverride()/getSkyboxOverride()) rather than
	// through the attribute system itself - SkyRendering.updateRenderState() is the single place that
	// reads SKY_COLOR_VISUAL and the dimension's skybox type into the render state, so overwriting
	// them there after the fact is simpler than modeling our override as another
	// EnvironmentAttributeFunction. End Skybox forces DimensionType.Skybox.END regardless of the
	// current dimension and disables the Sky Color override while active, since the End's own skybox
	// rendering doesn't use skyColor at all.

	private SettingToggle getCurrentDimSetting() {
		return getSetting(mc.world.getRegistryKey() == World.END ? 4 : mc.world.getRegistryKey() == World.NETHER ? 3 : 2).asToggle();
	}

	// Used by MixinSkyRendering; null means "don't override".
	public Integer getSkyColorOverride() {
		if (mc.world == null || isEndSkyboxActive()) {
			return null;
		}

		SettingToggle skyColor = getCurrentDimSetting().getChild(0).asToggle();
		if (!getCurrentDimSetting().getState() || !skyColor.getState()) {
			return null;
		}

		return skyColor.getChild(1).asColor().getRGB();
	}

	// Used by MixinSkyRendering; null means "don't override".
	public DimensionType.Skybox getSkyboxOverride() {
		return isEndSkyboxActive() ? DimensionType.Skybox.END : null;
	}

	private boolean isEndSkyboxActive() {
		if (mc.world == null) {
			return false;
		}

		SettingToggle skyColor = getCurrentDimSetting().getChild(0).asToggle();
		return getCurrentDimSetting().getState() && skyColor.getState() && skyColor.getChild(0).asToggle().getState();
	}

	private static class WeatherManager {

		private float rain = -1f;
		private float thunder = -1f;

		public void setRain(float rain) {
			this.rain = rain;
		}

		public void setThunder(float thunder) {
			this.thunder = thunder;
		}

		public void reset() {
			rain = -1f;
			thunder = -1f;
		}

		public void applyWeather(World world) {
			if (rain >= 0f) {
				world.getLevelProperties().setRaining(rain > 0f);
				world.setRainGradient(rain);
			}

			if (thunder >= 0f) {
				world.setThunderGradient(thunder);
			}
		}

		public boolean isActive() {
			return rain >= 0f || thunder >= 0f;
		}
	}
}

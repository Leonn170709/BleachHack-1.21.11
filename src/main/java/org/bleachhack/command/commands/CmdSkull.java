/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.command.commands;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

import org.bleachhack.command.Command;
import org.bleachhack.command.CommandCategory;
import org.bleachhack.command.exception.CmdSyntaxException;
import org.bleachhack.util.BleachLogger;

import com.google.common.collect.HashMultimap;
import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Uuids;

public class CmdSkull extends Command {

	public CmdSkull() {
		super("skull", "Gives you a player skull.", "skull <player> | skull img <image url>", CommandCategory.CREATIVE,
				"playerhead", "head");
	}

	@Override
	public void onCommand(String alias, String[] args) throws Exception {
		if (!mc.interactionManager.getCurrentGameMode().isCreative()) {
			BleachLogger.error("Not In Creative Mode!");
			return;
		}

		if (args.length == 0) {
			throw new CmdSyntaxException();
		}

		ItemStack item = new ItemStack(Items.PLAYER_HEAD, 64);

		Random random = new Random();
		// ported: 1.19.4 built a "SkullOwner.Id" int-array nbt tag with 4 random ints; 1.21.11
		// stores the owner as a real GameProfile in DataComponentTypes.PROFILE, so the same 4
		// random ints are packed into a UUID via Uuids.toUuid the same way the nbt int-array was.
		UUID id = Uuids.toUuid(new int[] {random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()});

		if (args.length < 2) {
			try {
				JsonObject json = JsonParser.parseString(
						Resources.toString(new URL("https://api.mojang.com/users/profiles/minecraft/" + args[0]), StandardCharsets.UTF_8))
						.getAsJsonObject();

				JsonObject json2 = JsonParser.parseString(
						Resources.toString(new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + json.get("id").getAsString()), StandardCharsets.UTF_8))
						.getAsJsonObject();

				String value = json2.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
				item.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(createProfile(id, value)));
			} catch (Exception e) {
				e.printStackTrace();
				BleachLogger.error("Error getting head! (" + e.getClass().getSimpleName() + ")");
			}
		} else if (args[0].equalsIgnoreCase("img")) {
			GameProfile profile = createProfile(id, encodeUrl(args[1]));
			item.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(profile));
			BleachLogger.logger.info(profile);
		}

		mc.player.getInventory().insertStack(item);
	}

	private GameProfile createProfile(UUID id, String texturesValue) {
		PropertyMap properties = new PropertyMap(HashMultimap.create());
		properties.put("textures", new Property("textures", texturesValue));
		return new GameProfile(id, "", properties);
	}

	private String encodeUrl(String url) {
		return Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes());
	}

}

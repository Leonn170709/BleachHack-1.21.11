/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public class ItemContentUtils {

	public static List<ItemStack> getItemsInContainer(ItemStack item) {
		DefaultedList<ItemStack> items = DefaultedList.ofSize(27, new ItemStack(Items.AIR));

		// ported: 1.19.4 read shulker box contents from the item's "BlockEntityTag.Items" nbt list
		// and bundle contents from a top-level "Items" nbt list; 1.21.11 componentizes both as
		// DataComponentTypes.CONTAINER (shulker/chest-like blocks) and BUNDLE_CONTENTS (bundles).
		ContainerComponent container = item.get(DataComponentTypes.CONTAINER);
		if (container != null) {
			container.copyTo(items);
		} else {
			BundleContentsComponent bundle = item.get(DataComponentTypes.BUNDLE_CONTENTS);
			if (bundle != null) {
				int i = 0;
				for (ItemStack stack : bundle.iterateCopy()) {
					if (i >= items.size()) {
						break;
					}
					items.set(i++, stack);
				}
			}
		}

		return items;
	}

	public static List<List<String>> getTextInBook(ItemStack item) {
		List<String> pages = new ArrayList<>();

		// ported: 1.19.4 read book pages from the item's "pages" nbt list (raw strings for
		// writable books, json-text strings for written books); 1.21.11 stores them as typed
		// components with the written book pages already parsed into Text.
		if (item.getItem() == Items.WRITABLE_BOOK) {
			WritableBookContentComponent content = item.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
			if (content != null) {
				for (RawFilteredPair<String> page : content.pages()) {
					pages.add(page.raw());
				}
			}
		} else {
			WrittenBookContentComponent content = item.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
			if (content != null) {
				for (Text text : content.getPages(false)) {
					pages.add(text.getString());
				}
			}
		}

		List<List<String>> finalPages = new ArrayList<>();

		for (String s : pages) {
			String buffer = "";
			List<String> pageBuffer = new ArrayList<>();

			for (char c : s.toCharArray()) {
				if (MinecraftClient.getInstance().textRenderer.getWidth(buffer) > 114 || buffer.endsWith("\n")) {
					pageBuffer.add(buffer.replace("\n", ""));
					buffer = "";
				}

				buffer += c;
			}

			pageBuffer.add(buffer);
			finalPages.add(pageBuffer);
		}

		return finalPages;
	}
}

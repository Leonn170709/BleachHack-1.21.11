/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui;

import java.util.ArrayList;
import java.util.List;

import org.bleachhack.BleachHack;
import org.bleachhack.gui.window.Window;
import org.bleachhack.gui.window.WindowScreen;
import org.bleachhack.gui.window.widget.WindowButtonWidget;
import org.bleachhack.gui.window.widget.WindowScrollbarWidget;
import org.bleachhack.gui.window.widget.WindowTextFieldWidget;
import org.bleachhack.gui.window.widget.WindowTextWidget;
import org.bleachhack.util.io.BleachFileHelper;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class FriendManagerScreen extends WindowScreen {

	private static final int LIST_TOP = 52;
	private static final int ROW_HEIGHT = 16;

	private int hovered = -1;

	private WindowScrollbarWidget scrollbar;
	private WindowTextFieldWidget addField;
	private List<String> friends = new ArrayList<>();

	public FriendManagerScreen() {
		super(Text.literal("Friends"));
	}

	public void init() {
		super.init();

		friends = new ArrayList<>(BleachHack.friendMang.getFriends());

		Window mainWindow = addWindow(new Window(
				width / 8, height / 8, width - width / 8, height - height / 8, "Friends", new ItemStack(Items.PLAYER_HEAD)));

		int w = mainWindow.x2 - mainWindow.x1;
		int h = mainWindow.y2 - mainWindow.y1;

		mainWindow.addWidget(new WindowTextWidget("Add a friend by name:", true, 6, 17, 0xf0f0f0));

		addField = mainWindow.addWidget(new WindowTextFieldWidget(6, 28, w - 80, 18, ""));
		mainWindow.addWidget(new WindowButtonWidget(w - 72, 28, w - 3, 46, "\u00a7aAdd", this::addFriend));

		scrollbar = mainWindow.addWidget(new WindowScrollbarWidget(w - 11, LIST_TOP, friends.size() * ROW_HEIGHT - 1, h - LIST_TOP - 1, 0));
	}

	private void addFriend() {
		String name = addField.textField.getText().trim();
		if (!name.isEmpty()) {
			BleachHack.friendMang.add(name);
			BleachFileHelper.SCHEDULE_SAVE_FRIENDS.set(true);
			addField.textField.setText("");
			refreshList();
		}
	}

	private void removeFriend(String name) {
		BleachHack.friendMang.remove(name);
		BleachFileHelper.SCHEDULE_SAVE_FRIENDS.set(true);
		refreshList();
	}

	private void refreshList() {
		friends = new ArrayList<>(BleachHack.friendMang.getFriends());
		scrollbar.setTotalHeight(friends.size() * ROW_HEIGHT - 1);
	}

	public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
		hovered = -1;

		// Screen.renderWithTooltip() now calls renderBackground() once itself before render() runs
		// (1.21.11) - calling it again here throws "Can only blur once per frame".
		super.render(matrices, mouseX, mouseY, delta);

		if (friends.isEmpty()) {
			Window w = getWindow(0);
			matrices.drawCenteredTextWithShadow(textRenderer, "No friends added yet", (w.x1 + w.x2) / 2, w.y1 + LIST_TOP + 8, 0xa0a0a0);
		}
	}

	public void onRenderWindow(DrawContext matrices, int window, int mouseX, int mouseY) {
		super.onRenderWindow(matrices, window, mouseX, mouseY);

		if (window != 0) return;

		Window win = getWindow(0);
		int x = win.x1;
		int y = win.y1;
		int w = win.x2 - x;
		int h = win.y2 - y;

		for (int i = 0; i < friends.size(); i++) {
			int curY = y + LIST_TOP + i * ROW_HEIGHT - scrollbar.getPageOffset();

			if (curY + ROW_HEIGHT > y + h || curY < y + LIST_TOP - 1)
				continue;

			Window.fill(matrices, x + 2, curY + 1, x + w - 12, curY + ROW_HEIGHT - 1, i % 2 == 0 ? 0x40606090 : 0x30606090);
			matrices.drawTextWithShadow(textRenderer, friends.get(i), x + 5, curY + 4, -1);

			boolean removeHover = win.selected && mouseX >= x + w - 64 && mouseX <= x + w - 14 && mouseY >= curY && mouseY <= curY + ROW_HEIGHT - 1;
			matrices.drawTextWithShadow(textRenderer, removeHover ? "\u00a7l\u00a7cRemove" : "\u00a7cRemove", x + w - 62, curY + 4, -1);

			if (removeHover)
				hovered = i;
		}
	}

	public boolean mouseClicked(Click click, boolean doubleClick) {
		if (hovered >= 0 && hovered < friends.size()) {
			removeFriend(friends.get(hovered));
			hovered = -1;
		}

		return super.mouseClicked(click, doubleClick);
	}
}

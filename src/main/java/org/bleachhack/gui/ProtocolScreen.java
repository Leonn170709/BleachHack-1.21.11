/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui;

import net.minecraft.resource.ResourceType;
import org.apache.commons.lang3.math.NumberUtils;

import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ProtocolScreen extends Screen {

	public static String BRAND = null;

	// 1.21.11: SharedConstants.getGameVersion() returns an immutable GameVersion record - there are no
	// mutable MinecraftVersion fields left to overwrite globally. Instead of faking the client's whole
	// internal notion of its own version, this now only rewrites the actual outgoing handshake packet's
	// protocolVersion (see MixinClientConnection) - a more faithful implementation of the feature's own
	// stated intent ("only changes what the client says to servers") than the 1.19.4 approach was.
	// There's no working equivalent left for spoofing the reported data pack version specifically (it's
	// not part of any packet a server can observe), so that field stays display-only.
	public static Integer PROTOCOL = null;

	private TextFieldWidget versionField;
	private TextFieldWidget protocolField; // int
	private TextFieldWidget packVerField; // int
	private TextFieldWidget brandField;
	private ButtonWidget addButton;
	private Screen parent;

	public ProtocolScreen(Screen parent) {
		super(Text.literal("Protocol Screen"));
		this.parent = parent;
	}

	public void init() {
		super.init();

		addButton = addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
			PROTOCOL = Integer.parseInt(protocolField.getText());
			BRAND = brandField.getText();

			close();
		}).position(width / 2 - 100, height / 2 + 50).size(196, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
				button -> close()).position(width / 2 - 100, height / 2 + 73).size(196, 20).build());

		versionField = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - 98, height / 2 - 60, 196, 18, Text.empty()));
		versionField.setText(SharedConstants.getGameVersion().name());

		protocolField = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - 98, height / 2 - 35, 196, 18, Text.empty()));
		protocolField.setText(Integer.toString(PROTOCOL != null ? PROTOCOL : SharedConstants.getProtocolVersion()));
		protocolField.setChangedListener(text -> updateAddButton());

		packVerField = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - 98, height / 2 - 10, 196, 18, Text.empty()));
		packVerField.setText(Integer.toString(SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES).major()));
		packVerField.setChangedListener(text -> updateAddButton());

		brandField = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - 98, height / 2 + 15, 128, 18, Text.empty()));
		brandField.setText(BRAND != null ? BRAND : ClientBrandRetriever.getClientModName());

		addDrawableChild(ButtonWidget.builder(Text.literal("V"),
				button -> brandField.setText("vanilla")).position(width / 2 + 33, height / 2 + 14).size(20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Fa"),
				button -> brandField.setText("fabric")).position(width / 2 + 56, height / 2 + 14).size(20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Fo"),
				button -> brandField.setText("forge")).position(width / 2 + 79, height / 2 + 14).size(20, 20).build());
	}

	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Screen.renderWithTooltip() now calls renderBackground() once itself before render() runs
		// (1.21.11) - calling it again here throws "Can only blur once per frame".
		context.drawCenteredTextWithShadow(textRenderer, "NOTE: This will not make the game compatible with other versions", width / 2, 5, 0xaaaaaa);
		context.drawCenteredTextWithShadow(textRenderer, "It will only change what the client says it is to servers.", width / 2, 15, 0xaaaaaa);

		context.drawTextWithShadow(textRenderer, "Version:", width / 2 - 103 - textRenderer.getWidth("Version:"), height / 2 - 55, 0xaaaaaa);
		context.drawTextWithShadow(textRenderer, "Protocol:", width / 2 - 103 - textRenderer.getWidth("Protocol:"), height / 2 - 30, 0xaaaaaa);
		context.drawTextWithShadow(textRenderer, "Pack Ver:", width / 2 - 103 - textRenderer.getWidth("Pack Ver:"), height / 2 - 5, 0xaaaaaa);
		context.drawTextWithShadow(textRenderer, "Brand:", width / 2 - 103 - textRenderer.getWidth("Brand:"), height / 2 + 20, 0xaaaaaa);

		super.render(context, mouseX, mouseY, delta);
	}

	public void close() {
		client.setScreen(parent);
	}

	private void updateAddButton() {
		addButton.active = NumberUtils.isDigits(protocolField.getText()) && NumberUtils.isDigits(packVerField.getText());
	}
}

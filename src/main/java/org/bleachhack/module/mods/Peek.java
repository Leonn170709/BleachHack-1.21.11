/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import java.util.Arrays;
import java.util.List;

import org.bleachhack.event.events.EventRenderTooltip;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.ItemContentUtils;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.MapRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class Peek extends Module {

	private static final RenderLayer MAP_BACKGROUND_CHECKERBOARD = RenderLayers.text(Identifier.ofVanilla("textures/map/map_background_checkerboard.png"));
	private static final int FULL_LIGHT = 0xF000F0;

	private List<List<String>> pages;
	private int slotX = -1;
	private int slotY = -1;
	private int pageCount = 0;
	private boolean shown = false;

	public Peek() {
		super("Peek", KEY_UNBOUND, ModuleCategory.MISC, "Shows whats inside containers.",
				new SettingToggle("Containers", true).withDesc("Shows a tooltip for containers.").withChildren(
						new SettingMode("Info", "All", "Name", "None").withDesc("How to show the old tooltip.")),
				new SettingToggle("Books", true).withDesc("Show tooltips for books."),
				new SettingToggle("Maps", true).withDesc("Show tooltips for maps.").withChildren(
						new SettingSlider("Map Size", 0.25, 1.5, 0.85, 2).withDesc("How big to make the map.")));
	}

	@BleachSubscribe
	public void drawScreen(EventRenderTooltip event) {
		if (!(event.getScreen() instanceof HandledScreen)) {
			return;
		}

		Slot slot = ((HandledScreen<?>) event.getScreen()).focusedSlot;
		if (slot == null)
			return;

		if (slot.x != slotX || slot.y != slotY) {
			pageCount = 0;
			pages = null;

			slotX = slot.x;
			slotY = slot.y;
		}

		event.getMatrix().push();
		event.getMatrix().translate(0, 0, 400);

		if (getSetting(0).asToggle().getState()) {
			List<TooltipComponent> components = drawShulkerToolTip(event.getMatrix(), slot, event.getMouseX(), event.getMouseY());
			if (components != null) {
				if (components.isEmpty()) {
					event.setCancelled(true);
				} else {
					event.setComponents(components);
				}
			}
		}

		if (getSetting(1).asToggle().getState()) drawBookToolTip(event.getMatrix(), slot, event.getMouseX(), event.getMouseY());
		if (getSetting(2).asToggle().getState()) drawMapToolTip(event.getMatrix(), slot, event.getMouseX(), event.getMouseY());

		event.getMatrix().pop();
	}

	public List<TooltipComponent> drawShulkerToolTip(MatrixStack matrices, Slot slot, int mouseX, int mouseY) {
		if (!(slot.getStack().getItem() instanceof BlockItem)) {
			return null;
		}

		Block block = ((BlockItem) slot.getStack().getItem()).getBlock();

		if (!(block instanceof ShulkerBoxBlock)
				&& !(block instanceof ChestBlock)
				&& !(block instanceof BarrelBlock)
				&& !(block instanceof DispenserBlock)
				&& !(block instanceof HopperBlock)
				&& !(block instanceof AbstractFurnaceBlock)) {
			return null;
		}

		List<ItemStack> items = ItemContentUtils.getItemsInContainer(slot.getStack());

		if (items.stream().allMatch(ItemStack::isEmpty)) {
			return null;
		}

		int mode = getSetting(0).asToggle().getChild(0).asMode().getMode();
		int realY = mode == 2 ? mouseY + 24 : mouseY;
		int tooltipWidth = block instanceof AbstractFurnaceBlock ? 47 : block instanceof HopperBlock ? 82 : 150;
		int tooltipHeight = block instanceof AbstractFurnaceBlock || block instanceof HopperBlock || block instanceof DispenserBlock ? 13 : 47;

		renderTooltipBox(matrices, mouseX, realY - tooltipHeight - 7, tooltipWidth, tooltipHeight, true);

		int count = block instanceof HopperBlock || block instanceof DispenserBlock || block instanceof AbstractFurnaceBlock ? 18 : 0;

		VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
		OrderedRenderCommandQueue queue = new ImmediateRenderCommandQueue(immediate);

		for (ItemStack i : items) {
			if (count > 26) {
				break;
			}

			int x = mouseX + 11 + 17 * (count % 9);
			int y = realY - 67 + 17 * (count / 9);

			drawItemIcon(matrices, immediate, queue, i, x, y);
			count++;
		}

		immediate.draw();

		if (mode == 1) {
			return Arrays.asList(TooltipComponent.of(slot.getStack().getName().asOrderedText()));
		} else if (mode == 2) {
			return List.of();
		}

		return null;
	}

	public void drawBookToolTip(MatrixStack matrices, Slot slot, int mouseX, int mouseY) {
		if (slot.getStack().getItem() != Items.WRITABLE_BOOK && slot.getStack().getItem() != Items.WRITTEN_BOOK)
			return;

		if (pages == null) {
			pages = ItemContentUtils.getTextInBook(slot.getStack());
		}

		if (pages.isEmpty()) {
			return;
		}

		/* Cycle through pages */
		if (mc.player.age % 80 == 0 && !shown) {
			shown = true;
			if (pageCount == pages.size() - 1) {
				pageCount = 0;
			} else {
				pageCount++;
			}
		} else if (mc.player.age % 80 != 0) {
			shown = false;
		}

		VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

		drawTexturedQuad(matrices, immediate, BookScreen.BOOK_TEXTURE, mouseX, mouseY - 143, 0, 0, 134, 134, 179, 179);

		Text pageIndexText = Text.translatable("book.pageIndicator", pageCount + 1, pages.size());
		int pageIndexLength = mc.textRenderer.getWidth(pageIndexText);

		matrices.push();
		matrices.scale(0.7f, 0.7f, 1f);

		Matrix4f textMatrix = matrices.peek().getPositionMatrix();

		mc.textRenderer.draw(
				pageIndexText,
				(mouseX + 123 - pageIndexLength) * 1.43f,
				(mouseY - 133) * 1.43f,
				0x000000, false, textMatrix, immediate, TextRenderer.TextLayerType.NORMAL, 0, FULL_LIGHT);


		int count = 0;
		for (String s : pages.get(pageCount)) {
			mc.textRenderer.draw(
					s,
					(mouseX + 24) * 1.43f,
					(mouseY - 123 + count * 7) * 1.43f,
					0x000000, false, textMatrix, immediate, TextRenderer.TextLayerType.NORMAL, 0, FULL_LIGHT);

			count++;
		}

		matrices.pop();
		immediate.draw();
	}

	public void drawMapToolTip(MatrixStack matrices, Slot slot, int mouseX, int mouseY) {
		if (slot.getStack().getItem() != Items.FILLED_MAP) {
			return;
		}

		MapIdComponent mapId = slot.getStack().get(DataComponentTypes.MAP_ID);
		MapState mapState = FilledMapItem.getMapState(slot.getStack(), mc.world);

		if (mapId == null || mapState == null) {
			return;
		}

		float scale = getSetting(2).asToggle().getChild(0).asSlider().getValueFloat() / 1.25f;

		matrices.push();
		matrices.translate(mouseX + 14, mouseY - 18 - 135 * scale, 0);
		matrices.scale(scale, scale, 0.0078125f);

		VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer backgroundVertexer = immediate.getBuffer(MAP_BACKGROUND_CHECKERBOARD);
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		backgroundVertexer.vertex(matrix4f, -7f, 135f, -10f).color(255, 255, 255, 255).texture(0f, 1f).light(FULL_LIGHT);
		backgroundVertexer.vertex(matrix4f, 135f, 135f, -10f).color(255, 255, 255, 255).texture(1f, 1f).light(FULL_LIGHT);
		backgroundVertexer.vertex(matrix4f, 135f, -7f, -10f).color(255, 255, 255, 255).texture(1f, 0f).light(FULL_LIGHT);
		backgroundVertexer.vertex(matrix4f, -7f, -7f, -10f).color(255, 255, 255, 255).texture(0f, 0f).light(FULL_LIGHT);

		MapRenderState renderState = new MapRenderState();
		mc.getMapRenderer().update(mapId, mapState, renderState);
		mc.getMapRenderer().draw(renderState, matrices, new ImmediateRenderCommandQueue(immediate), false, FULL_LIGHT);
		immediate.draw();

		matrices.pop();

	}

	/**
	 * Renders a single item's icon (baked model) plus its durability/count overlay at the given
	 * screen position, mirroring what {@code DrawContext.drawItem}/{@code drawStackOverlay} do
	 * internally, but immediately (since this tooltip preview has no DrawContext of its own to
	 * queue into - it only has the raw MatrixStack handed to it by EventRenderTooltip).
	 */
	private void drawItemIcon(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, OrderedRenderCommandQueue queue, ItemStack stack, int x, int y) {
		if (stack.isEmpty()) {
			return;
		}

		ItemRenderState state = new ItemRenderState();
		mc.getItemModelManager().clearAndUpdate(state, stack, ItemDisplayContext.GUI, mc.world, null, 0);

		matrices.push();
		matrices.translate(x + 8.0F, y + 8.0F, 0.0F);
		matrices.scale(16.0F, -16.0F, 16.0F);
		mc.gameRenderer.getDiffuseLighting().setShaderLights(state.isSideLit() ? DiffuseLighting.Type.ITEMS_3D : DiffuseLighting.Type.ITEMS_FLAT);
		state.render(matrices, queue, FULL_LIGHT, OverlayTexture.DEFAULT_UV, 0);
		matrices.pop();

		drawItemOverlay(matrices, immediate, stack, x, y);
	}

	private void drawItemOverlay(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, ItemStack stack, int x, int y) {
		if (stack.isItemBarVisible()) {
			int i = x + 2;
			int j = y + 13;
			fillRect(matrices, immediate, i, j, i + 13, j + 2, 0xFF000000);
			fillRect(matrices, immediate, i, j, i + stack.getItemBarStep(), j + 1, 0xFF000000 | stack.getItemBarColor());
		}

		if (stack.getCount() != 1) {
			String string = String.valueOf(stack.getCount());
			mc.textRenderer.draw(
					string,
					x + 19 - 2 - mc.textRenderer.getWidth(string),
					y + 6 + 3,
					0xFFFFFFFF, true, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.NORMAL, 0, FULL_LIGHT);
		}
	}

	private void drawTexturedQuad(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight) {
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.text(texture));
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float u0 = (float) u / textureWidth;
		float u1 = (float) (u + width) / textureWidth;
		float v0 = (float) v / textureHeight;
		float v1 = (float) (v + height) / textureHeight;
		consumer.vertex(matrix, x, y + height, 0).color(255, 255, 255, 255).texture(u0, v1).light(FULL_LIGHT);
		consumer.vertex(matrix, x + width, y + height, 0).color(255, 255, 255, 255).texture(u1, v1).light(FULL_LIGHT);
		consumer.vertex(matrix, x + width, y, 0).color(255, 255, 255, 255).texture(u1, v0).light(FULL_LIGHT);
		consumer.vertex(matrix, x, y, 0).color(255, 255, 255, 255).texture(u0, v0).light(FULL_LIGHT);
	}

	private void fillRect(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float x1, float y1, float x2, float y2, int color) {
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.debugQuads());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float a = (color >> 24 & 255) / 255.0F;
		float r = (color >> 16 & 255) / 255.0F;
		float g = (color >> 8 & 255) / 255.0F;
		float b = (color & 255) / 255.0F;
		consumer.vertex(matrix, x2, y1, 0).color(r, g, b, a);
		consumer.vertex(matrix, x1, y1, 0).color(r, g, b, a);
		consumer.vertex(matrix, x1, y2, 0).color(r, g, b, a);
		consumer.vertex(matrix, x2, y2, 0).color(r, g, b, a);
	}

	private void renderTooltipBox(MatrixStack matrices, int x1, int y1, int x2, int y2, boolean wrap) {
		int xStart = x1 + 12;
		int yStart = y1 - 12;
		if (wrap) {
			if (xStart + x2 > mc.currentScreen.width)
				xStart -= 28 + x2;
			if (yStart + y2 + 6 > mc.currentScreen.height)
				yStart = mc.currentScreen.height - y2 - 6;
		}

		VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();

		fillGradient(matrix4f, immediate, xStart - 3, yStart - 4, xStart + x2 + 3, yStart - 3, -267386864, -267386864);
		fillGradient(matrix4f, immediate, xStart - 3, yStart + y2 + 3, xStart + x2 + 3, yStart + y2 + 4, -267386864, -267386864);
		fillGradient(matrix4f, immediate, xStart - 3, yStart - 3, xStart + x2 + 3, yStart + y2 + 3, -267386864, -267386864);
		fillGradient(matrix4f, immediate, xStart - 4, yStart - 3, xStart - 3, yStart + y2 + 3, -267386864, -267386864);
		fillGradient(matrix4f, immediate, xStart + x2 + 3, yStart - 3, xStart + x2 + 4, yStart + y2 + 3, -267386864, -267386864);
		fillGradient(matrix4f, immediate, xStart - 3, yStart - 3 + 1, xStart - 3 + 1, yStart + y2 + 3 - 1, 1347420415, 1344798847);
		fillGradient(matrix4f, immediate, xStart + x2 + 2, yStart - 3 + 1, xStart + x2 + 3, yStart + y2 + 3 - 1, 1347420415, 1344798847);
		fillGradient(matrix4f, immediate, xStart - 3, yStart - 3, xStart + x2 + 3, yStart - 3 + 1, 1347420415, 1347420415);
		fillGradient(matrix4f, immediate, xStart - 3, yStart + y2 + 2, xStart + x2 + 3, yStart + y2 + 3, 1344798847, 1344798847);

		immediate.draw();
	}

	private void fillGradient(Matrix4f matrix, VertexConsumerProvider vertexConsumers, int xStart, int yStart, int xEnd, int yEnd, int colorStart, int colorEnd) {
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.debugQuads());
		float f = (float)(colorStart >> 24 & 255) / 255.0F;
		float g = (float)(colorStart >> 16 & 255) / 255.0F;
		float h = (float)(colorStart >> 8 & 255) / 255.0F;
		float i = (float)(colorStart & 255) / 255.0F;
		float j = (float)(colorEnd >> 24 & 255) / 255.0F;
		float k = (float)(colorEnd >> 16 & 255) / 255.0F;
		float l = (float)(colorEnd >> 8 & 255) / 255.0F;
		float m = (float)(colorEnd & 255) / 255.0F;
		consumer.vertex(matrix, (float) xEnd, (float) yStart, 0f).color(g, h, i, f);
		consumer.vertex(matrix, (float) xStart, (float) yStart, 0f).color(g, h, i, f);
		consumer.vertex(matrix, (float) xStart, (float) yEnd, 0f).color(k, l, m, j);
		consumer.vertex(matrix, (float) xEnd, (float) yEnd, 0f).color(k, l, m, j);
	}

	/**
	 * Minimal {@link OrderedRenderCommandQueue} that renders {@code submitItem}/{@code submitCustom}
	 * (and the {@code submitText} used by the map decoration labels) immediately into a
	 * {@link VertexConsumerProvider}, instead of batching them like the real engine-owned queue does.
	 *
	 * 1.21.11 moved item/map rendering behind this batching-command-queue abstraction, which is only
	 * ever handed out by the frame's GUI/world renderer - there is no public API to render a single
	 * item model or map "right now" outside of that pass anymore. This tiny adapter reconstructs just
	 * enough of it (submitItem + submitCustom + submitText, which is all plain items and maps need)
	 * to keep this floating preview working. Other command kinds (special item models such as player
	 * heads/banners/shields, block models, entities, ...) are left as no-ops - ported: BleachHack's
	 * container/map preview never rendered those anyway, and reproducing the full batching renderer
	 * just for this tooltip isn't worth it.
	 */
	private static class ImmediateRenderCommandQueue implements OrderedRenderCommandQueue {
		private final VertexConsumerProvider vertexConsumers;

		private ImmediateRenderCommandQueue(VertexConsumerProvider vertexConsumers) {
			this.vertexConsumers = vertexConsumers;
		}

		@Override
		public RenderCommandQueue getBatchingQueue(int order) {
			return this;
		}

		@Override
		public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay, int outlineColors, int[] tintLayers, List<BakedQuad> quads, RenderLayer renderLayer, ItemRenderState.Glint glintType) {
			ItemRenderer.renderItem(displayContext, matrices, vertexConsumers, light, overlay, tintLayers, quads, renderLayer, glintType);
		}

		@Override
		public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, OrderedRenderCommandQueue.Custom customRenderer) {
			customRenderer.render(matrices.peek(), vertexConsumers.getBuffer(renderLayer));
		}

		@Override
		public void submitCustom(OrderedRenderCommandQueue.LayeredCustom customRenderer) {
			// not supported by this lightweight immediate-mode adapter, see class javadoc
		}

		@Override
		public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean dropShadow, TextRenderer.TextLayerType layerType, int light, int color, int backgroundColor, int outlineColor) {
			mc.textRenderer.draw(text, x, y, color, dropShadow, matrices.peek().getPositionMatrix(), vertexConsumers, layerType, backgroundColor, light);
		}

		@Override
		public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces) {
		}

		@Override
		public void submitLabel(MatrixStack matrices, Vec3d nameLabelPos, int y, Text label, boolean notSneaking, int light, double squaredDistanceToCamera, CameraRenderState cameraState) {
		}

		@Override
		public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
		}

		@Override
		public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData) {
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S state, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay, int tintedColor, Sprite sprite, int outlineColor, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
		}

		@Override
		public void submitModelPart(ModelPart part, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay, Sprite sprite, boolean sheeted, boolean hasGlint, int tintedColor, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, int i) {
		}

		@Override
		public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
		}

		@Override
		public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
		}

		@Override
		public void submitBlockStateModel(MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor) {
		}
	}
}

/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Meteor Client's ESP "Wireframe" mode re-renders the entity's actual posed 3D model as a
 * wireframe instead of drawing a bounding box - each face of every body-part cuboid outlined
 * (and optionally filled), not just one big AABB. Meteor's own port does this by re-submitting
 * the model through a custom vertex consumer that captures every quad; our 1.21.11 renderer
 * doesn't hand entities a raw VertexConsumerProvider any more (EntityRenderer.render takes an
 * OrderedRenderCommandQueue instead), so this intercepts that queue's submitModel/submitModelPart
 * calls - the model is already correctly posed for this frame by the entity's own renderer by the
 * time those are called - and walks each ModelPart's cuboids directly via ModelPart#forEachCuboid,
 * which hands out per-part-transformed MatrixStack.Entry objects ready to feed straight into a
 * VertexConsumer, exactly like ModelPart.Cuboid's own (private) renderCuboid does.
 */
public class WireframeEntityRenderer {

	private static final MinecraftClient mc = MinecraftClient.getInstance();

	private static final BufferAllocator FILL_BUFFER = new BufferAllocator(1536);
	private static final BufferAllocator LINE_BUFFER = new BufferAllocator(1536);

	public static void render(Entity entity, float tickDelta, int r, int g, int b, int fillAlpha, float lineWidth) {
		EntityRenderManager dispatcher = mc.getEntityRenderDispatcher();
		EntityRenderState state = dispatcher.getAndUpdateRenderState(entity, tickDelta);

		Camera camera = mc.gameRenderer.getCamera();
		Vec3d camPos = camera.getCameraPos();

		CameraRenderState cameraState = new CameraRenderState();
		cameraState.pos = camPos;

		MatrixStack matrices = new MatrixStack();
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

		Capture capture = new Capture(r, g, b, fillAlpha, lineWidth);
		dispatcher.render(state, cameraState, state.x - camPos.x, state.y - camPos.y, state.z - camPos.z, matrices, capture);
		capture.finish();
	}

	private static class Capture implements OrderedRenderCommandQueue {

		private final int r, g, b, fillAlpha;
		private final float lineWidth;

		private @Nullable BufferBuilder fillBuf;
		private @Nullable BufferBuilder lineBuf;

		private Capture(int r, int g, int b, int fillAlpha, float lineWidth) {
			this.r = r;
			this.g = g;
			this.b = b;
			this.fillAlpha = fillAlpha;
			this.lineWidth = lineWidth;

			if (fillAlpha != 0) {
				fillBuf = new BufferBuilder(FILL_BUFFER, RenderLayers.DEBUG_FILLED_BOX.getDrawMode(), RenderLayers.DEBUG_FILLED_BOX.getVertexFormat());
			}
			if (lineWidth != 0) {
				lineBuf = new BufferBuilder(LINE_BUFFER, RenderLayers.LINES.getDrawMode(), RenderLayers.LINES.getVertexFormat());
			}
		}

		private void finish() {
			// Not every entity submits a ModelPart/Model - dropped items, end crystals, fishing
			// bobbers etc. render through submitItem/submitCustom instead, which this doesn't
			// intercept, so nothing ever gets written to the buffers for them. end() throws on an
			// empty buffer; endNullable() just returns null instead, so use that here.
			if (fillBuf != null) {
				var built = fillBuf.endNullable();
				if (built != null) {
					RenderLayers.DEBUG_FILLED_BOX.draw(built);
				}
			}
			if (lineBuf != null) {
				var built = lineBuf.endNullable();
				if (built != null) {
					RenderLayers.LINES.draw(built);
				}
			}
		}

		private void drawPart(ModelPart part, MatrixStack matrices) {
			part.forEachCuboid(matrices, (entry, path, index, cuboid) -> {
				for (ModelPart.Quad quad : cuboid.sides) {
					ModelPart.Vertex[] v = quad.vertices();

					if (fillBuf != null) {
						for (ModelPart.Vertex vertex : v) {
							fillBuf.vertex(entry, vertex.worldX(), vertex.worldY(), vertex.worldZ()).color(r, g, b, fillAlpha);
						}
					}

					if (lineBuf != null) {
						var direction = entry.transformNormal(quad.direction(), new org.joml.Vector3f());
						for (int i = 0; i < v.length; i++) {
							ModelPart.Vertex from = v[i];
							ModelPart.Vertex to = v[(i + 1) % v.length];

							lineBuf.vertex(entry, from.worldX(), from.worldY(), from.worldZ())
									.color(r, g, b, 255).normal(direction.x(), direction.y(), direction.z()).lineWidth(lineWidth);
							lineBuf.vertex(entry, to.worldX(), to.worldY(), to.worldZ())
									.color(r, g, b, 255).normal(direction.x(), direction.y(), direction.z()).lineWidth(lineWidth);
						}
					}
				}
			});
		}

		// -------------------- The only commands ESP's wireframe cares about --------------------

		@Override
		public <S> void submitModel(Model<? super S> model, S modelState, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay,
				int tintedColor, @Nullable Sprite sprite, int outlineColor, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay) {
			drawPart(model.getRootPart(), matrices);
		}

		@Override
		public void submitModelPart(ModelPart part, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay, @Nullable Sprite sprite,
				boolean sheeted, boolean hasGlint, int tintedColor, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay, int i) {
			drawPart(part, matrices);
		}

		// -------------------- Everything else: not real geometry, ignore --------------------

		@Override
		public RenderCommandQueue getBatchingQueue(int order) {
			return this;
		}

		@Override
		public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces) {
		}

		@Override
		public void submitLabel(MatrixStack matrices, @Nullable Vec3d nameLabelPos, int y, Text label, boolean notSneaking, int light,
				double squaredDistanceToCamera, CameraRenderState cameraState) {
		}

		@Override
		public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean dropShadow, TextRenderer.TextLayerType layerType,
				int light, int color, int backgroundColor, int outlineColor) {
		}

		@Override
		public void submitFire(MatrixStack matrices, EntityRenderState renderState, org.joml.Quaternionf rotation) {
		}

		@Override
		public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData) {
		}

		@Override
		public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
		}

		@Override
		public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
		}

		@Override
		public void submitBlockStateModel(MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light,
				int overlay, int outlineColor) {
		}

		@Override
		public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay, int outlineColors, int[] tintLayers,
				List<BakedQuad> quads, RenderLayer renderLayer, ItemRenderState.Glint glintType) {
		}

		@Override
		public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, OrderedRenderCommandQueue.Custom customRenderer) {
		}

		@Override
		public void submitCustom(OrderedRenderCommandQueue.LayeredCustom customRenderer) {
		}
	}
}

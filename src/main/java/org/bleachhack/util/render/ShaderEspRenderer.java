/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util.render;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * A real, from-scratch port of Meteor Client's ESP "Shader" mode - a private silhouette
 * framebuffer, populated every frame with a flat-recolored render of each matched entity's actual
 * posed model (not vanilla's Glowing-effect outline system, which this used to piggyback on: that
 * meant sharing GPU resources/behavior with real Glowing potion effects/team glow, and capped how
 * different the result could ever look from vanilla's own built-in glow), then composited onto the
 * main framebuffer through Meteor's real distance-field glow shader.
 *
 * <p>Architecture, mapped from Meteor's utils.render.postprocess (EntityShader/EntityOutlineShader/
 * CustomOutlineVertexConsumerProvider + LevelRendererMixin's push/popEntityOutlineFramebuffer) onto
 * this engine's actual APIs:
 * <ul>
 * <li>Meteor's own MC snapshot hands entities a raw {@code VertexConsumerProvider}, so it captures
 * geometry by wrapping that. This engine's {@code EntityRenderer.render(...)} instead takes an
 * {@code OrderedRenderCommandQueue} (a command-pattern queue, not a raw vertex sink) - so instead
 * this intercepts {@code submitModel}/{@code submitModelPart} and calls {@code ModelPart.render(...)}
 * directly with a flat forced color, which is simpler than Meteor's own per-vertex-consumer capture
 * and gives real per-limb silhouette geometry (not just a bounding box).</li>
 * <li>Meteor pushes/pops its own {@code RenderTarget} onto the level renderer's outline framebuffer
 * field mid-frame. This engine's entity_outline framebuffer is a {@code Handle<Framebuffer>} baked
 * into a {@code FrameGraphBuilder} DAG, not a simple swappable field - so instead of hooking that
 * pipeline at all, this uses {@code RenderSystem.outputColorTextureOverride} (confirmed against
 * {@code RenderLayer.draw}'s own source: it already checks this override before every immediate
 * draw) to redirect this pass's draws into our own framebuffer, entirely independent of
 * WorldRenderer's frame graph.</li>
 * <li>Compositing (our shader, then blit) reuses the exact same 2-pass pattern vanilla's own
 * entity_outline post effect uses (a custom shader pass into a "swap" target, then blit back onto
 * the real target) - see assets/bleachhack/post_effect/esp_outline.json - finished off with
 * {@code Framebuffer.drawBlit(...)}, the same call {@code WorldRenderer.drawEntityOutlinesFramebuffer()}
 * uses for vanilla's own outline, which already carries the correct alpha blending
 * ({@code RenderPipelines.ENTITY_OUTLINE_BLIT}).</li>
 * </ul>
 */
public class ShaderEspRenderer {

	private static final MinecraftClient mc = MinecraftClient.getInstance();

	private static final Identifier ESP_OUTLINE = Identifier.of("bleachhack", "esp_outline");

	private static @Nullable SimpleFramebuffer framebuffer;

	private static final SilhouetteQueue QUEUE = new SilhouetteQueue();

	public static void render(Map<Entity, int[]> entities, float tickDelta) {
		if (entities.isEmpty()) {
			return;
		}

		ensureFramebuffer();

		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(framebuffer.getColorAttachment(), 0);

		Camera camera = mc.gameRenderer.getCamera();
		Vec3d camPos = camera.getCameraPos();

		CameraRenderState cameraState = new CameraRenderState();
		cameraState.pos = camPos;

		EntityRenderManager dispatcher = mc.getEntityRenderDispatcher();

		RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
		try {
			BufferBuilder buffer = new BufferBuilder(new net.minecraft.client.util.BufferAllocator(4096),
					Renderer.NO_DEPTH_FILL.getDrawMode(), Renderer.NO_DEPTH_FILL.getVertexFormat());
			QUEUE.target = new FlatVertexConsumer(buffer);

			for (Map.Entry<Entity, int[]> entry : entities.entrySet()) {
				Entity entity = entry.getKey();
				int[] color = entry.getValue();

				EntityRenderState state = dispatcher.getAndUpdateRenderState(entity, tickDelta);

				MatrixStack matrices = new MatrixStack();
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

				QUEUE.color = ColorHelper.getArgb(255, color[0], color[1], color[2]);
				dispatcher.render(state, cameraState, state.x - camPos.x, state.y - camPos.y, state.z - camPos.z, matrices, QUEUE);
			}

			var built = buffer.endNullable();
			if (built != null) {
				Renderer.NO_DEPTH_FILL.draw(built);
			}
		} finally {
			RenderSystem.outputColorTextureOverride = null;
		}

		PostEffectProcessor processor = mc.getShaderLoader().loadPostEffect(ESP_OUTLINE, Set.of(ESP_OUTLINE));
		if (processor == null) {
			return;
		}

		FrameGraphBuilder builder = new FrameGraphBuilder();
		PostEffectProcessor.FramebufferSet framebufferSet = PostEffectProcessor.FramebufferSet.singleton(
				ESP_OUTLINE, builder.createObjectNode("esp_outline", framebuffer));
		processor.render(builder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet);
		builder.run(ObjectAllocator.TRIVIAL);

		framebuffer.drawBlit(mc.getFramebuffer().getColorAttachmentView());
	}

	private static void ensureFramebuffer() {
		int w = mc.getWindow().getFramebufferWidth();
		int h = mc.getWindow().getFramebufferHeight();

		if (framebuffer == null) {
			framebuffer = new SimpleFramebuffer("BleachHack ESP Outline", w, h, true);
		} else if (framebuffer.textureWidth != w || framebuffer.textureHeight != h) {
			framebuffer.resize(w, h);
		}
	}

	/** Strips texture/overlay/light/normal channels ModelPart.render() sends that our plain position-color buffer doesn't support - only position+color actually matter for a flat silhouette. */
	private static final class FlatVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;

		private FlatVertexConsumer(VertexConsumer delegate) {
			this.delegate = delegate;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			delegate.color(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer color(int argb) {
			delegate.color(argb);
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer lineWidth(float width) {
			return this;
		}
	}

	/** Intercepts model submission and draws a flat-colored copy of the real posed model instead of the textured/lit mesh. Everything else (labels/fire/leash/blocks/items) is irrelevant to a silhouette pass and is ignored. */
	private static final class SilhouetteQueue implements OrderedRenderCommandQueue {

		private @Nullable VertexConsumer target;
		private int color;

		@Override
		public <S> void submitModel(Model<? super S> model, S modelState, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay,
				int tintedColor, @Nullable Sprite sprite, int outlineColor, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay) {
			model.getRootPart().render(matrices, target, light, overlay, color);
		}

		@Override
		public void submitModelPart(ModelPart part, MatrixStack matrices, RenderLayer renderLayer, int light, int overlay, @Nullable Sprite sprite,
				boolean sheeted, boolean hasGlint, int tintedColor, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay, int i) {
			part.render(matrices, target, light, overlay, color);
		}

		@Override
		public RenderCommandQueue getBatchingQueue(int order) {
			return this;
		}

		@Override
		public void submitShadowPieces(MatrixStack matrices, float shadowRadius, java.util.List<EntityRenderState.ShadowPiece> shadowPieces) {
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
				java.util.List<BakedQuad> quads, RenderLayer renderLayer, ItemRenderState.Glint glintType) {
		}

		@Override
		public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, OrderedRenderCommandQueue.Custom customRenderer) {
		}

		@Override
		public void submitCustom(OrderedRenderCommandQueue.LayeredCustom customRenderer) {
		}
	}
}

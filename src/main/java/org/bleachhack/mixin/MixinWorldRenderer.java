/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventBlockEntityRender;
import org.bleachhack.event.events.EventEntityRender;
import org.bleachhack.event.events.EventRenderBlockOutline;
import org.bleachhack.event.events.EventWorldRender;
import org.bleachhack.util.render.FrustumUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

	@Shadow private void drawBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer, double x, double y, double z,
			OutlineRenderState state, int color, float lineWidth) {}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V"), require = 0)
	private void render_swap(Profiler profiler, String string) {
		profiler.swap(string);

		if (string.equals("entities")) {
			BleachHack.eventBus.post(new EventEntityRender.PreAll());
		} else if (string.equals("blockentities")) {
			BleachHack.eventBus.post(new EventEntityRender.PostAll());
			BleachHack.eventBus.post(new EventBlockEntityRender.PreAll());
		} else if (string.equals("destroyProgress")) {
			BleachHack.eventBus.post(new EventBlockEntityRender.PostAll());
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void render_head(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
			Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor,
			boolean renderSky, CallbackInfo callback) {
		EventWorldRender.Pre event = new EventWorldRender.Pre(tickCounter.getTickProgress(false));
		BleachHack.eventBus.post(event);

		if (event.isCancelled()) {
			callback.cancel();
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void render_return(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
			Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor,
			boolean renderSky, CallbackInfo callback) {
		// RenderSystem.clear(int, boolean) no longer exists - depth clearing now goes through the
		// GPU command encoder directly.
		Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
		RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(framebuffer.getDepthAttachment(), 1.0);
		BleachHack.eventBus.post(new EventWorldRender.Post(tickCounter.getTickProgress(false)));
	}

	@Inject(method = "fillEntityRenderStates", at = @At("HEAD"))
	private void fillEntityRenderStates_head(Camera camera, Frustum frustum, RenderTickCounter tickCounter, WorldRenderState state, CallbackInfo callback) {
		FrustumUtils.setCurrentFrustum(frustum);
	}

	@Redirect(method = "fillEntityRenderStates", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
	private <E extends Entity> boolean fillEntityRenderStates_shouldRender(EntityRenderManager manager, E entity, Frustum frustum, double x, double y, double z) {
		if (!manager.shouldRender(entity, frustum, x, y, z)) {
			return false;
		}

		EventEntityRender.Single.Pre event = new EventEntityRender.Single.Pre(entity);
		BleachHack.eventBus.post(event);

		return !event.isCancelled();
	}

	@Redirect(method = "renderTargetBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V"),
			require = 0)
	private void render_drawBlockOutline(WorldRenderer worldRenderer, MatrixStack matrices, VertexConsumer vertexConsumer, double x, double y, double z,
			OutlineRenderState state, int color, float lineWidth) {
		EventRenderBlockOutline event = new EventRenderBlockOutline(matrices, vertexConsumer, state.pos());
		BleachHack.eventBus.post(event);

		if (!event.isCancelled()) {
			drawBlockOutline(event.getMatrices(), event.getVertexConsumer(), x, y, z, state, color, lineWidth);
		}
	}

}

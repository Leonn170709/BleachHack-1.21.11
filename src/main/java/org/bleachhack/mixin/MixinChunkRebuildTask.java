/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.blaze3d.vertex.VertexFormat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventRenderBlock;
import org.bleachhack.event.events.EventRenderFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Blocks are still tesselated even if they're transparent because Minecraft's
 * rendering engine is poop.
 *
 * 1.21.11 moved the entire per-block/per-fluid tesselation loop this mixin used to override out of
 * RebuildTask itself and into a new standalone SectionBuilder class (SectionBuilder.build(...)) -
 * the redirect target moved accordingly, but the loop body is otherwise a straight port (matched
 * line-for-line against SectionBuilder's real decompiled source) with our event hooks re-inserted.
 */
@Mixin(ChunkBuilder.BuiltChunk.RebuildTask.class)
public class MixinChunkRebuildTask {

	@Unique private static final boolean OPTIFABRIC_INSTALLED = FabricLoader.getInstance().isModLoaded("optifabric");

	@Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/SectionBuilder;build("
			+ "Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;"
			+ "Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;"
			+ ")Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;"))
	private SectionBuilder.RenderData run_build(SectionBuilder sectionBuilder, ChunkSectionPos sectionPos, ChunkRendererRegion region,
			VertexSorter vertexSorter, BlockBufferAllocatorStorage allocatorStorage) {
		return OPTIFABRIC_INSTALLED
				? sectionBuilder.build(sectionPos, region, vertexSorter, allocatorStorage)
				: build(sectionPos, region, vertexSorter, allocatorStorage);
	}

	private SectionBuilder.RenderData build(ChunkSectionPos sectionPos, ChunkRendererRegion region, VertexSorter vertexSorter,
			BlockBufferAllocatorStorage allocatorStorage) {
		SectionBuilder.RenderData renderData = new SectionBuilder.RenderData();
		BlockPos min = sectionPos.getMinPos();
		BlockPos max = min.add(15, 15, 15);
		ChunkOcclusionDataBuilder occlusionDataBuilder = new ChunkOcclusionDataBuilder();
		MatrixStack matrices = new MatrixStack();
		BlockModelRenderer.enableBrightnessCache();
		Map<BlockRenderLayer, BufferBuilder> buffersByLayer = new EnumMap<>(BlockRenderLayer.class);
		Random random = Random.create();
		List<BlockModelPart> parts = new ObjectArrayList<>();
		BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
		BlockEntityRenderManager blockEntityRenderManager = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();

		for (BlockPos pos : BlockPos.iterate(min, max)) {
			BlockState blockState = region.getBlockState(pos);
			if (blockState.isOpaqueFullCube()) {
				occlusionDataBuilder.markClosed(pos);
			}

			if (blockState.hasBlockEntity()) {
				BlockEntity blockEntity = region.getBlockEntity(pos);
				if (blockEntity != null) {
					addBlockEntity(blockEntityRenderManager, renderData, blockEntity);
				}
			}

			FluidState fluidState = blockState.getFluidState();
			if (!fluidState.isEmpty()) {
				BlockRenderLayer layer = BlockRenderLayers.getFluidLayer(fluidState);
				BufferBuilder bufferBuilder = beginBufferBuilding(buffersByLayer, allocatorStorage, layer);

				EventRenderFluid event = new EventRenderFluid(fluidState, pos, bufferBuilder);
				BleachHack.eventBus.post(event);

				if (!event.isCancelled()) {
					blockRenderManager.renderFluid(pos, region, event.getVertexConsumer(), blockState, fluidState);
				}
			}

			// Kept 1.19.4's broader "!= INVISIBLE" check rather than vanilla's new narrower
			// "== MODEL" one, so blocks that only vanilla would skip still get tesselated.
			if (blockState.getRenderType() != BlockRenderType.INVISIBLE) {
				BlockRenderLayer layer = BlockRenderLayers.getBlockLayer(blockState);
				BufferBuilder bufferBuilder = beginBufferBuilding(buffersByLayer, allocatorStorage, layer);

				EventRenderBlock.Tesselate event = new EventRenderBlock.Tesselate(blockState, pos, matrices, bufferBuilder);
				BleachHack.eventBus.post(event);

				if (event.isCancelled()) {
					continue;
				}

				random.setSeed(blockState.getRenderingSeed(pos));
				blockRenderManager.getModel(blockState).addParts(random, parts);

				matrices.push();
				matrices.translate(ChunkSectionPos.getLocalCoord(pos.getX()), ChunkSectionPos.getLocalCoord(pos.getY()), ChunkSectionPos.getLocalCoord(pos.getZ()));
				blockRenderManager.renderBlock(blockState, pos, region, matrices, event.getVertexConsumer(), true, parts);
				matrices.pop();
				parts.clear();
			}
		}

		for (Entry<BlockRenderLayer, BufferBuilder> entry : buffersByLayer.entrySet()) {
			BlockRenderLayer layer = entry.getKey();
			BuiltBuffer builtBuffer = entry.getValue().endNullable();
			if (builtBuffer != null) {
				if (layer == BlockRenderLayer.TRANSLUCENT) {
					renderData.translucencySortingData = builtBuffer.sortQuads(allocatorStorage.get(layer), vertexSorter);
				}

				renderData.buffers.put(layer, builtBuffer);
			}
		}

		BlockModelRenderer.disableBrightnessCache();
		renderData.chunkOcclusionData = occlusionDataBuilder.build();
		return renderData;
	}

	private BufferBuilder beginBufferBuilding(Map<BlockRenderLayer, BufferBuilder> builders, BlockBufferAllocatorStorage allocatorStorage, BlockRenderLayer layer) {
		BufferBuilder bufferBuilder = builders.get(layer);
		if (bufferBuilder == null) {
			BufferAllocator bufferAllocator = allocatorStorage.get(layer);
			bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
			builders.put(layer, bufferBuilder);
		}

		return bufferBuilder;
	}

	private <E extends BlockEntity> void addBlockEntity(BlockEntityRenderManager dispatcher, SectionBuilder.RenderData data, E blockEntity) {
		BlockEntityRenderer<E, ?> renderer = dispatcher.get(blockEntity);
		if (renderer != null && !renderer.rendersOutsideBoundingBox()) {
			data.blockEntities.add(blockEntity);
		}
	}
}

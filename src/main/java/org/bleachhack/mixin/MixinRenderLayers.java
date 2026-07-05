package org.bleachhack.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventRenderBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 1.21.11 split block-layer resolution into a lightweight BlockRenderLayers/BlockRenderLayer enum used
// specifically by chunk section building (see MixinChunkRebuildTask), separate from the old
// RenderLayers.getBlockLayer(...) -> full RenderLayer used elsewhere. The actual chunk-render call site
// Xray needs to override moved to BlockRenderLayers, so the hook moved with it.
@Mixin(BlockRenderLayers.class)
public class MixinRenderLayers {

	@Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
	private static void getBlockLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> callback) {
		EventRenderBlock.Layer event = new EventRenderBlock.Layer(state);
		BleachHack.eventBus.post(event);

		if (event.getLayer() != null)
			callback.setReturnValue(event.getLayer());
	}
}

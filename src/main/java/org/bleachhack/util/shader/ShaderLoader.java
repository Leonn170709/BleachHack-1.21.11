package org.bleachhack.util.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

import java.util.Set;

public class ShaderLoader {

	// 1.21.11: post effects are loaded (and compile-cached) through vanilla's own centralized
	// MinecraftClient.getShaderLoader() instead of a raw `new PostEffectProcessor(...)` construction -
	// mod-bundled assets/<namespace>/post_effect/*.json is found automatically since our mod jar is
	// already a resource pack, so the old OpenResourceManager hack is no longer needed at all.
	public static PostEffectProcessor loadEffect(Identifier id) {
		return MinecraftClient.getInstance().getShaderLoader().loadPostEffect(id, Set.of());
	}

}

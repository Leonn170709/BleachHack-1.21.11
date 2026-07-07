/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.event.events.EventWorldRender;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingColor;
import org.bleachhack.setting.module.SettingButton;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.io.BleachFileMang;
import org.bleachhack.util.render.Renderer;
import org.bleachhack.util.render.color.QuadColor;
import org.bleachhack.util.world.ChunkProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * New mode ported from Trouser-Streak's NewerNewChunks (https://github.com/etianl/Trouser-Streak).
 * Simplified from the original: one shared alarm (not five per-category ones), one "Old Generation"
 * toggle covering all three dimensions (not three), a single combined save file per dimension
 * instead of five, and no GUI double-confirm/auto-reload timer for clearing data - a plain button.
 */
public class NewChunks extends Module {

	private static final Direction[] SKIP_DIRS = new Direction[] { Direction.DOWN, Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH };
	private static final Direction[] SEARCH_DIRS = new Direction[] { Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP };

	private final Set<ChunkPos> newChunks = Collections.synchronizedSet(new HashSet<>());
	private final Set<ChunkPos> oldChunks = Collections.synchronizedSet(new HashSet<>());

	// New mode only
	private final Set<ChunkPos> beingUpdatedChunks = Collections.synchronizedSet(new HashSet<>());
	private final Set<ChunkPos> oldGenerationChunks = Collections.synchronizedSet(new HashSet<>());
	private final Set<ChunkPos> blockExploitChunks = Collections.synchronizedSet(new HashSet<>());
	private RegistryKey<World> lastDimension;
	private int alarmRingsLeft;
	private int alarmDelay;

	private static final Set<net.minecraft.block.Block> ORE_BLOCKS = Set.of(
			Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
			Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
			Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
			Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
	private static final Set<net.minecraft.block.Block> NEW_OVERWORLD_BLOCKS = Set.of(
			Blocks.DEEPSLATE, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AZALEA, Blocks.FLOWERING_AZALEA,
			Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.SMALL_DRIPLEAF, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
			Blocks.SPORE_BLOSSOM, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_IRON_ORE,
			Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
			Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.GLOW_LICHEN, Blocks.RAW_COPPER_BLOCK,
			Blocks.RAW_IRON_BLOCK, Blocks.DRIPSTONE_BLOCK, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.POINTED_DRIPSTONE,
			Blocks.SMOOTH_BASALT, Blocks.TUFF, Blocks.CALCITE, Blocks.HANGING_ROOTS, Blocks.ROOTED_DIRT,
			Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, Blocks.POWDER_SNOW);
	private static final Set<net.minecraft.block.Block> NEW_NETHER_BLOCKS = Set.of(
			Blocks.ANCIENT_DEBRIS, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
			Blocks.CRIMSON_STEM, Blocks.CRIMSON_NYLIUM, Blocks.NETHER_GOLD_ORE, Blocks.WARPED_NYLIUM, Blocks.WARPED_STEM,
			Blocks.TWISTING_VINES, Blocks.WEEPING_VINES, Blocks.BONE_BLOCK, Blocks.IRON_CHAIN, Blocks.OBSIDIAN,
			Blocks.CRYING_OBSIDIAN, Blocks.SOUL_SOIL, Blocks.SOUL_FIRE);

	private ChunkProcessor processor = new ChunkProcessor(1,
			(cp, chunk) -> {
				if (getSetting(0).asMode().getMode() == 0) {
					oldModeLoadChunk(cp, chunk);
				} else {
					newModeLoadChunk(cp, chunk);
				}
			},
			null,
			(pos, state) -> {
				if (getSetting(0).asMode().getMode() == 0) {
					oldModeUpdateBlock(pos, state);
				} else {
					newModeUpdateBlock(pos, state);
				}
			});

	public NewChunks() {
		this(new SettingMode("Mode", "Old", "New").withDesc("Old: the original flowing-liquid heuristic. New: Trouser-Streak's chunk-palette scanning, with more detection methods and detail."));
	}

	// A visibleWhen(...) predicate can't call an instance method like getSetting(0) - "this" isn't
	// allowed yet in a super(...) argument list, even inside a lambda. Building the Mode setting as
	// a constructor parameter first lets the predicates below capture that local variable instead.
	private NewChunks(SettingMode mode) {
		super("NewChunks", KEY_UNBOUND, ModuleCategory.WORLD, "Detects new and old chunks.",
				mode,
				new SettingSlider("Y-Offset", -100, 100, 0, 0).withDesc("The offset from the bottom of the world to render the squares at."),
				new SettingToggle("Remove", true).withDesc("Removes the cached chunks when disabling the module."),
				new SettingToggle("Fill", true).withDesc("Fills in the chunks.").withChildren(
						new SettingSlider("Opacity", 0.01, 1, 0.3, 2).withDesc("The opacity of the fill.")),
				new SettingToggle("NewChunks", true).withDesc("Shows all the chunks that are (most likely) completely new.").withChildren(
						new SettingColor("Color", 200, 150, 215).withDesc("The color of NewChunks.")),
				new SettingToggle("OldChunks", false).withDesc("Shows all the chunks that have (most likely) been loaded before.").withChildren(
						new SettingColor("Color", 230, 50, 50).withDesc("The color of OldChunks.")),
				new SettingToggle("BeingUpdated", false).withDesc("New mode only: chunks currently being upgraded from an old version.").withChildren(
						new SettingColor("Color", 255, 210, 0).withDesc("The color of BeingUpdated chunks."))
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("OldGeneration", false).withDesc("New mode only: chunks that were generated in an old version.").withChildren(
						new SettingColor("Color", 190, 255, 0).withDesc("The color of OldGeneration chunks."))
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("BlockExploit", false).withDesc("New mode only: chunks flagged via a block-update packet - may possibly be old, see Block Update Exploit below.").withChildren(
						new SettingColor("Color", 0, 0, 255).withDesc("The color of BlockExploit chunks."))
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Palette Exploit", true).withDesc("New mode only: detects new/being-updated chunks by scanning the order of chunk section block palettes.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Liquid Exploit", false).withDesc("New mode only: estimates chunks based on flowing liquids.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Block Update Exploit", false).withDesc("New mode only: estimates chunks based on block-update packets. May possibly be old.").withChildren(
						new SettingToggle("Fold Into Old", false).withDesc("Shows BlockExploit chunks as OldChunks instead of their own category."))
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Old Generation Detector", true).withDesc("New mode only: marks chunks as old-generation if they're missing blocks added after their dimension's last major terrain update.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Being Updated Detector", true).withDesc("New mode only: marks chunks as being-updated if they're currently being upgraded from an old version.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingSlider("Render Distance", 6, 128, 32, 0).withDesc("New mode only: how many chunks away to render detected chunks.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Save Data", true).withDesc("New mode only: saves detected chunks to a file, per server/world and dimension.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Load Data", true).withDesc("New mode only: loads previously saved chunks when entering a dimension.")
						.visibleWhen(() -> mode.getMode() == 1),
				// Can't use this::clearSavedData here - "this" isn't allowed in a super(...) argument
				// list until the super constructor returns. Routing through the static module lookup
				// instead avoids capturing "this" directly.
				new SettingButton("Clear Saved Chunk Data", () -> org.bleachhack.module.ModuleManager.getModule(NewChunks.class).clearSavedData()).withDesc("New mode only: deletes the saved chunk data for the current dimension and clears the cache.")
						.visibleWhen(() -> mode.getMode() == 1),
				new SettingToggle("Alarm", false).withDesc("New mode only: plays a sound when a new chunk is detected.").withChildren(
						new SettingSlider("Volume", 0, 1, 1, 2).withDesc("The volume of the alarm."),
						new SettingSlider("Pitch", 0.5, 2, 1, 2).withDesc("The pitch of the alarm."))
						.visibleWhen(() -> mode.getMode() == 1));
	}

	@Override
	public void onDisable(boolean inWorld) {
		if (getSetting(2).asToggle().getState()) {
			clearAll();
		}

		processor.stop();
		super.onDisable(inWorld);
	}

	@Override
	public void onEnable(boolean inWorld) {
		super.onEnable(inWorld);
		processor.start();
		lastDimension = null;
		alarmRingsLeft = 0;
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (getSetting(0).asMode().getMode() != 1 || mc.world == null) {
			return;
		}

		// Reload the saved data whenever the dimension changes, instead of Trouser-Streak's
		// screen-open/leave-world tracking - simpler, and covers the same "just entered a new
		// dimension" case.
		RegistryKey<World> dim = mc.world.getRegistryKey();
		if (dim != lastDimension) {
			lastDimension = dim;
			clearAll();
			if (getSetting(16).asToggle().getState()) {
				loadSavedData();
			}
		}

		if (alarmRingsLeft > 0) {
			if (alarmDelay <= 0) {
				playAlarm();
				alarmDelay = 10;
				alarmRingsLeft--;
			} else {
				alarmDelay--;
			}
		}
	}

	private void clearAll() {
		newChunks.clear();
		oldChunks.clear();
		beingUpdatedChunks.clear();
		oldGenerationChunks.clear();
		blockExploitChunks.clear();
	}

	private void clearSavedData() {
		clearAll();
		if (mc.world != null) {
			try {
				Files.deleteIfExists(saveFilePath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@BleachSubscribe
	public void onWorldRender(EventWorldRender.Post event) {
		int renderY = mc.world.getBottomY() + getSetting(1).asSlider().getValueInt();
		int opacity = (int) (getSetting(3).asToggle().getChild(0).asSlider().getValueFloat() * 255);
		boolean newMode = getSetting(0).asMode().getMode() == 1;
		double maxDist = newMode ? getSetting(14).asSlider().getValue() * 16 : 1024;

		renderSet(event, newChunks, getSetting(4), renderY, opacity, maxDist);
		renderSet(event, oldChunks, getSetting(5), renderY, opacity, maxDist);

		if (newMode) {
			renderSet(event, beingUpdatedChunks, getSetting(6), renderY, opacity, maxDist);
			renderSet(event, oldGenerationChunks, getSetting(7), renderY, opacity, maxDist);
			renderSet(event, blockExploitChunks, getSetting(8), renderY, opacity, maxDist);
		}
	}

	private void renderSet(EventWorldRender.Post event, Set<ChunkPos> chunks, org.bleachhack.setting.module.ModuleSetting<?> toggleSetting, int renderY, int opacity, double maxDist) {
		if (!toggleSetting.asToggle().getState()) {
			return;
		}

		int[] color = toggleSetting.asToggle().getChild(0).asColor().getRGBArray();
		QuadColor outlineColor = QuadColor.single(color[0], color[1], color[2], 255);
		QuadColor fillColor = QuadColor.single(color[0], color[1], color[2], opacity);

		synchronized (chunks) {
			for (ChunkPos c : chunks) {
				if (mc.getCameraEntity().getBlockPos().isWithinDistance(c.getStartPos(), maxDist)) {
					Box box = new Box(
							c.getStartX(), renderY, c.getStartZ(),
							c.getStartX() + 16, renderY, c.getStartZ() + 16);

					if (getSetting(3).asToggle().getState()) {
						Renderer.drawBoxFill(box, fillColor, SKIP_DIRS);
					}

					Renderer.drawBoxOutline(box, outlineColor, 2f, SKIP_DIRS);
				}
			}
		}
	}

	// ===================== Old mode (unchanged from before the New mode port) =====================

	private void oldModeLoadChunk(ChunkPos cp, WorldChunk chunk) {
		if (!newChunks.contains(cp) && mc.world.getChunkManager().getChunk(cp.x, cp.z) == null) {
			for (int x = 0; x < 16; x++) {
				for (int y = mc.world.getBottomY(); y <= mc.world.getTopYInclusive(); y++) {
					for (int z = 0; z < 16; z++) {
						FluidState fluid = chunk.getFluidState(x, y, z);

						if (!fluid.isEmpty() && !fluid.isStill()) {
							oldChunks.add(cp);
							return;
						}
					}
				}
			}
		}
	}

	private void oldModeUpdateBlock(net.minecraft.util.math.BlockPos pos, BlockState state) {
		if (!state.getFluidState().isEmpty() && !state.getFluidState().isStill()) {
			ChunkPos cpos = new ChunkPos(pos);
			for (Direction dir : SEARCH_DIRS) {
				if (mc.world.getBlockState(pos.offset(dir)).getFluidState().isStill() && !oldChunks.contains(cpos)) {
					newChunks.add(cpos);
					return;
				}
			}
		}
	}

	// ===================== New mode (ported from Trouser-Streak's NewerNewChunks) =====================

	private void newModeUpdateBlock(net.minecraft.util.math.BlockPos pos, BlockState state) {
		ChunkPos cpos = new ChunkPos(pos);
		if (alreadyClassified(cpos)) {
			return;
		}

		if (getSetting(11).asToggle().getState()) {
			boolean foldIntoOld = getSetting(11).asToggle().getChild(0).asToggle().getState();
			addChunk(foldIntoOld ? oldChunks : blockExploitChunks, cpos, false);
		}

		if (getSetting(10).asToggle().getState() && !state.getFluidState().isEmpty() && !state.getFluidState().isStill()) {
			for (Direction dir : SEARCH_DIRS) {
				if (mc.world.getBlockState(pos.offset(dir)).getFluidState().isStill()) {
					blockExploitChunks.remove(cpos);
					addChunk(newChunks, cpos, true);
					return;
				}
			}
		}
	}

	private void newModeLoadChunk(ChunkPos cp, WorldChunk chunk) {
		if (alreadyClassified(cp) || mc.world == null) {
			return;
		}

		RegistryKey<World> dim = mc.world.getRegistryKey();
		ChunkSection[] sections = chunk.getSectionArray();

		boolean isOldGeneration = getSetting(12).asToggle().getState() && detectOldGeneration(sections, dim);

		if (getSetting(9).asToggle().getState()) {
			scanPalette(cp, sections, dim, isOldGeneration);
		} else if (isOldGeneration) {
			addChunk(oldGenerationChunks, cp, false);
		}
	}

	// Marks a chunk as generated in an old version if it's missing the blocks added to its
	// dimension's terrain generation since - checked via hasAny() over each section's palette
	// instead of Trouser-Streak's per-block x/y/z loop, since a palette entry only needs to exist
	// once in a section to prove the block is (or isn't) there.
	private boolean detectOldGeneration(ChunkSection[] sections, RegistryKey<World> dim) {
		if (dim == World.OVERWORLD) {
			boolean foundOre = false;
			boolean foundNewBlock = false;
			for (int i = 0; i < Math.min(sections.length, 17); i++) {
				ChunkSection section = sections[i];
				if (section == null || section.isEmpty()) {
					continue;
				}

				PalettedContainer<BlockState> states = section.getBlockStateContainer();
				if (!foundOre && states.hasAny(s -> ORE_BLOCKS.contains(s.getBlock()))) {
					foundOre = true;
				}
				if ((i > 4) && !foundNewBlock && states.hasAny(s -> NEW_OVERWORLD_BLOCKS.contains(s.getBlock()))) {
					foundNewBlock = true;
				}
			}
			return foundOre && !foundNewBlock;
		} else if (dim == World.NETHER) {
			for (int i = 0; i < Math.min(sections.length, 8); i++) {
				ChunkSection section = sections[i];
				if (section != null && !section.isEmpty()
						&& section.getBlockStateContainer().hasAny(s -> NEW_NETHER_BLOCKS.contains(s.getBlock()))) {
					return false;
				}
			}
			return true;
		} else if (dim == World.END) {
			ChunkSection section = sections.length > 0 ? sections[0] : null;
			return section != null && section.getBiomeContainer() instanceof PalettedContainer<RegistryEntry<Biome>> biomes
					&& biomes.hasAny(b -> b.matchesKey(BiomeKeys.THE_END));
		}

		return false;
	}

	// Trouser-Streak's "PaletteExploit" - a section's block palette gets its distinct entries
	// appended in the order they're first read while decoding chunk NBT, which differs
	// consistently enough between a freshly-generated section and a previously-explored one
	// (loaded from disk, with player-made changes) to use as a heuristic. Needs the "data" field
	// widened (see bleachhack.accesswidener) since PalettedContainer has no public accessor for
	// the palette itself, only aggregate queries.
	private void scanPalette(ChunkPos pos, ChunkSection[] sections, RegistryKey<World> dim, boolean isOldGeneration) {
		boolean isNether = dim == World.NETHER;
		boolean isEnd = dim == World.END;

		boolean firstSectionAppearsNew = false;
		boolean isNewChunk = false;
		boolean chunkIsBeingUpdated = false;
		int loops = 0;
		int newChunkQuantifier = 0;
		int oldChunkQuantifier = 0;

		try {
			for (ChunkSection section : sections) {
				if (section == null || section.isEmpty()) {
					continue;
				}

				int isNewSection = 0;
				int isBeingUpdatedSection = 0;

				Palette<BlockState> palette = section.getBlockStateContainer().data.palette();
				int paletteSize = palette.getSize();

				for (int i = 0; i < paletteSize; i++) {
					BlockState entry = palette.get(i);
					if (i == 0 && loops == 0 && entry.getBlock() == Blocks.AIR && !isEnd) {
						firstSectionAppearsNew = true;
					}
					if (i == 0 && entry.getBlock() == Blocks.AIR && !isNether && !isEnd) {
						isNewSection++;
					}
					if (i == 1 && (entry.getBlock() == Blocks.WATER || entry.getBlock() == Blocks.STONE
							|| entry.getBlock() == Blocks.GRASS_BLOCK || entry.getBlock() == Blocks.SNOW_BLOCK) && !isNether && !isEnd) {
						isNewSection++;
					}
					if (i == 2 && (entry.getBlock() == Blocks.SNOW_BLOCK || entry.getBlock() == Blocks.DIRT
							|| entry.getBlock() == Blocks.POWDER_SNOW) && !isNether && !isEnd) {
						isNewSection++;
					}
					if (loops == 4 && entry.getBlock() == Blocks.BEDROCK && !isNether && !isEnd
							&& getSetting(13).asToggle().getState()) {
						chunkIsBeingUpdated = true;
					}
					if (entry.getBlock() == Blocks.AIR && (isNether || isEnd)) {
						isBeingUpdatedSection++;
					}
				}

				if (isBeingUpdatedSection >= 2) {
					oldChunkQuantifier++;
				}
				if (isNewSection >= 2) {
					newChunkQuantifier++;
				}

				loops++;
			}

			if (loops > 0) {
				if (getSetting(13).asToggle().getState() && (isNether || isEnd)) {
					if (((double) oldChunkQuantifier / loops) * 100 >= 25) {
						chunkIsBeingUpdated = true;
					}
				} else if (!isNether && !isEnd) {
					if (((double) newChunkQuantifier / loops) * 100 >= 51) {
						isNewChunk = true;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (firstSectionAppearsNew) {
			isNewChunk = true;
		}

		boolean notOldGeneration = isEnd ? isNewChunk : !isOldGeneration;

		if (isNewChunk && !chunkIsBeingUpdated && notOldGeneration) {
			addChunk(newChunks, pos, true);
		} else if (!isNewChunk && !chunkIsBeingUpdated && isOldGeneration) {
			addChunk(oldGenerationChunks, pos, false);
		} else if (chunkIsBeingUpdated) {
			addChunk(beingUpdatedChunks, pos, false);
		} else if (getSetting(9).asToggle().getState()) {
			addChunk(oldChunks, pos, false);
		}
	}

	private boolean alreadyClassified(ChunkPos pos) {
		return newChunks.contains(pos) || oldChunks.contains(pos) || beingUpdatedChunks.contains(pos)
				|| oldGenerationChunks.contains(pos) || blockExploitChunks.contains(pos);
	}

	private void addChunk(Set<ChunkPos> set, ChunkPos pos, boolean ringAlarm) {
		if (alreadyClassified(pos)) {
			return;
		}

		set.add(pos);

		if (ringAlarm && getSetting(18).asToggle().getState()) {
			alarmRingsLeft = 1;
			alarmDelay = 0;
		}

		if (getSetting(15).asToggle().getState()) {
			saveChunk(set, pos);
		}
	}

	private void playAlarm() {
		if (mc.player == null || mc.world == null) {
			return;
		}

		float volume = getSetting(18).asToggle().getChild(0).asSlider().getValueFloat();
		float pitch = getSetting(18).asToggle().getChild(1).asSlider().getValueFloat();
		mc.world.playSoundClient(SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, volume, pitch);
	}

	// ===================== Persistence (New mode) - one file per dimension instead of Trouser- =====================
	// ===================== Streak's five, tagged per line by category. =====================

	private Path saveFilePath() {
		String server = mc.isIntegratedServerRunning() && mc.getServer() != null
				? mc.getServer().getSavePath(WorldSavePath.ROOT).getFileName().toString()
				: mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "unknown";
		String dim = mc.world.getRegistryKey().getValue().toString();

		String safeServer = server.replaceAll("[^a-zA-Z0-9._-]", "_");
		String safeDim = dim.replaceAll("[^a-zA-Z0-9._-]", "_");

		return BleachFileMang.getDir().resolve("NewChunks").resolve(safeServer).resolve(safeDim + ".txt");
	}

	private String tagFor(Set<ChunkPos> set) {
		if (set == newChunks) return "NEW";
		if (set == oldChunks) return "OLD";
		if (set == beingUpdatedChunks) return "UPDATING";
		if (set == oldGenerationChunks) return "OLDGEN";
		return "EXPLOIT";
	}

	private void saveChunk(Set<ChunkPos> set, ChunkPos pos) {
		try {
			Path path = saveFilePath();
			Files.createDirectories(path.getParent());
			String line = tagFor(set) + "," + pos.x + "," + pos.z + System.lineSeparator();
			Files.write(path, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadSavedData() {
		try {
			Path path = saveFilePath();
			if (!Files.exists(path)) {
				return;
			}

			List<String> lines = Files.readAllLines(path);
			for (String line : lines) {
				if (line == null || line.isBlank()) {
					continue;
				}

				String[] parts = line.split(",");
				if (parts.length != 3) {
					continue;
				}

				try {
					ChunkPos pos = new ChunkPos(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
					switch (parts[0].trim()) {
						case "NEW" -> newChunks.add(pos);
						case "OLD" -> oldChunks.add(pos);
						case "UPDATING" -> beingUpdatedChunks.add(pos);
						case "OLDGEN" -> oldGenerationChunks.add(pos);
						case "EXPLOIT" -> blockExploitChunks.add(pos);
						default -> {}
					}
				} catch (NumberFormatException ignored) {}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import com.google.common.collect.Streams;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.event.events.EventWorldRender;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingColor;
import org.bleachhack.setting.module.SettingRotate;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.InventoryUtils;
import org.bleachhack.util.render.Renderer;
import org.bleachhack.util.render.WorldRenderer;
import org.bleachhack.util.render.color.QuadColor;
import org.bleachhack.util.world.DamageUtils;
import org.bleachhack.util.world.EntityUtils;
import org.bleachhack.util.world.WorldUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

// i am morbidly obese
public class CrystalAura extends Module {

	private BlockPos render = null;
	private float renderDamage = 0;
	private int breakCooldown = 0;
	private int placeCooldown = 0;

	// ConcurrentHashMap since the Support search thread reads this (via getCrystalPoses) while the
	// main thread ticks it down at the same time.
	private Map<BlockPos, Integer> blacklist = new ConcurrentHashMap<>();

	// Support: places an obsidian block via the same item-use-cooldown desync AirPlace exploits,
	// then waits supportDelay ticks (so the server has time to register it) before placing the
	// crystal on top. pendingSupport = "still trying to get the block down", supportDelay =
	// "block's down, waiting out the delay".
	private Set<BlockPos> pendingSupport = new HashSet<>();
	private Map<BlockPos, Integer> supportDelay = new HashMap<>();
	private int supportCooldown = 0;

	// Support treats every replaceable block in range as a candidate, not just existing obsidian/
	// bedrock, so the scan + per-candidate explosion damage math gets a lot bigger than the normal
	// path and was blocking the render thread long enough to lag. Only used while Support is on -
	// the normal path stays synchronous since it's cheap enough not to need this.
	private ExecutorService searchExecutor;
	private final AtomicBoolean searching = new AtomicBoolean(false);
	private volatile Map<BlockPos, Float> cachedPlaceBlocks = new LinkedHashMap<>();
	private volatile Map<BlockPos, Float> cachedPlaceDamage = new HashMap<>();

	public CrystalAura() {
		super("CrystalAura", KEY_UNBOUND, ModuleCategory.COMBAT, "Automatically does crystalpvp for you.",
				new SettingToggle("Players", true).withDesc("Targets players."),
				new SettingToggle("Mobs", false).withDesc("Targets mobs."),
				new SettingToggle("Animals", false).withDesc("Targets animals."),
				new SettingToggle("Explode", true).withDesc("Hits/explodes crystals.").withChildren(
						new SettingToggle("AntiWeakness", true).withDesc("Hits crystals with your strongest weapon when you have weakness."),
						new SettingToggle("AntiSuicide", true).withDesc("Prevents you from killing yourself with a crystal."),
						new SettingSlider("CPT", 1, 10, 2, 0).withDesc("How many crystals to hit per tick."),
						new SettingSlider("Cooldown", 0, 10, 0, 0).withDesc("How many ticks to wait before exploding the next batch of crystals."),
						new SettingSlider("MinHealth", 0, 20, 2, 0).withDesc("Wont explode the crystal if it makes you got below the specified health.")),
				new SettingToggle("Place", true).withDesc("Places crystals.").withChildren(
						new SettingToggle("AutoSwitch", true).withDesc("Automatically switches to crystal when in combat.").withChildren(
								new SettingToggle("SwitchBack", true).withDesc("Switches back to your previous item.")),
						new SettingToggle("1.12 Place", false).withDesc("Only places on blocks with 2 air blocks above instead of 1 because of an extra check in pre 1.13."),
						new SettingToggle("Blacklist", true).withDesc("Blacklists a crystal when it can't place so it doesn't spam packets."),
						new SettingToggle("Raycast", false).withDesc("Only places a crystal if you can see it."),
						new SettingSlider("MinDmg", 1, 20, 2, 0).withDesc("Minimum damage to the target to place crystals."),
						new SettingSlider("MinRatio", 0.5, 6, 2, 1).withDesc("Minimum damage ratio to place a crystal at (Target dmg/Player dmg)."),
						new SettingSlider("CPT", 1, 10, 2, 0).withDesc("How many crystals to place per tick."),
						new SettingSlider("Cooldown", 0, 10, 0, 0).withDesc("How many ticks to wait before placing the next batch of crystals."),
						new SettingColor("Place Color", 178, 178, 255).withDesc("The color of the block you're placing crystals on."),
						new SettingToggle("Support", false).withDesc("Places an obsidian support block (using the same trick as AirPlace) when a good spot has no solid block to place the crystal on.").withChildren(
								new SettingSlider("Support Delay", 0, 10, 2, 0).withDesc("Ticks to wait after placing the support block before placing the crystal on it."))),
				new SettingToggle("Render Damage", true).withDesc("Shows the expected damage above the last block you placed a crystal on."),
				new SettingToggle("SameTick", false).withDesc("Enables exploding and placing crystals at the same tick."),
				new SettingRotate(false).withDesc("Rotates to crystals."),
				new SettingSlider("Range", 0, 6, 4.5, 2).withDesc("Range to place and attack crystals."));
	}

	@Override
	public void onEnable(boolean inWorld) {
		searchExecutor = Executors.newSingleThreadExecutor();
		super.onEnable(inWorld);
	}

	@Override
	public void onDisable(boolean inWorld) {
		if (searchExecutor != null) {
			searchExecutor.shutdownNow();
			searchExecutor = null;
		}
		searching.set(false);
		cachedPlaceBlocks = new LinkedHashMap<>();
		cachedPlaceDamage = new HashMap<>();
		pendingSupport.clear();
		supportDelay.clear();
		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		breakCooldown = Math.max(0, breakCooldown - 1);
		placeCooldown = Math.max(0, placeCooldown - 1);
		supportCooldown = Math.max(0, supportCooldown - 1);

		for (Entry<BlockPos, Integer> e : new HashMap<>(blacklist).entrySet()) {
			if (e.getValue() > 0) {
				blacklist.replace(e.getKey(), e.getValue() - 1);
			} else {
				blacklist.remove(e.getKey());
			}
		}

		for (Entry<BlockPos, Integer> e : new HashMap<>(supportDelay).entrySet()) {
			if (e.getValue() > 0) {
				supportDelay.replace(e.getKey(), e.getValue() - 1);
			}
		}

		if (mc.player.isUsingItem() && mc.player.getMainHandStack().contains(DataComponentTypes.FOOD)) {
			return;
		}

		List<LivingEntity> targets = Streams.stream(mc.world.getEntities())
				.filter(e -> EntityUtils.isAttackable(e, true))
				.filter(e -> (getSetting(0).asToggle().getState() && EntityUtils.isPlayer(e))
						|| (getSetting(1).asToggle().getState() && EntityUtils.isMob(e))
						|| (getSetting(2).asToggle().getState() && EntityUtils.isAnimal(e)))
				.map(e -> (LivingEntity) e)
				.toList();

		if (targets.isEmpty()) {
			return;
		}

		// Explode
		SettingToggle explodeToggle = getSetting(3).asToggle();
		List<EndCrystalEntity> nearestCrystals = Streams.stream(mc.world.getEntities())
				.filter(e -> e instanceof EndCrystalEntity)
				.map(e -> (EndCrystalEntity) e)
				.sorted(Comparator.comparing(mc.player::distanceTo))
				.toList();

		int breaks = 0;
		if (explodeToggle.getState() && !nearestCrystals.isEmpty() && breakCooldown <= 0) {
			boolean end = false;
			for (EndCrystalEntity c : nearestCrystals) {
				if (mc.player.distanceTo(c) > getSetting(8).asSlider().getValue()
						|| mc.world.getOtherEntities(null, new Box(c.getEntityPos(), c.getEntityPos()).expand(7), targets::contains).isEmpty())
					continue;

				float damage = DamageUtils.getExplosionDamage(c.getEntityPos(), 6f, mc.player);
				if (DamageUtils.willGoBelowHealth(mc.player, damage, explodeToggle.getChild(4).asSlider().getValueFloat()))
					continue;

				int oldSlot = mc.player.getInventory().getSelectedSlot();
				if (explodeToggle.getChild(0).asToggle().getState() && mc.player.hasStatusEffect(StatusEffects.WEAKNESS)) {
					InventoryUtils.selectSlot(false, true, Comparator.comparing(i -> DamageUtils.getItemAttackDamage(mc.player.getInventory().getStack(i))));
				}

				if (getSetting(7).asRotate().getState()) {
					Vec3d eyeVec = mc.player.getEyePos();
					Vec3d v = new Vec3d(c.getX(), c.getY() + 0.5, c.getZ());
					for (Direction d : Direction.values()) {
						Vec3d vd = WorldUtils.getLegitLookPos(c.getBoundingBox(), d, true, 5, -0.001);
						if (vd != null && eyeVec.distanceTo(vd) <= eyeVec.distanceTo(v)) {
							v = vd;
						}
					}

					WorldUtils.facePosAuto(v.x, v.y, v.z, getSetting(7).asRotate());
				}

				mc.interactionManager.attackEntity(mc.player, c);
				mc.player.swingHand(Hand.MAIN_HAND);
				blacklist.remove(c.getBlockPos().down());

				InventoryUtils.selectSlot(oldSlot);

				end = true;
				breaks++;
				if (breaks >= explodeToggle.getChild(2).asSlider().getValue()) {
					break;
				}
			}

			breakCooldown = explodeToggle.getChild(3).asSlider().getValueInt() + 1;

			if (!getSetting(6).asToggle().getState() && end) {
				return;
			}
		}

		// Place
		SettingToggle placeToggle = getSetting(4).asToggle();
		if (placeToggle.getState() && placeCooldown <= 0) {
			int crystalSlot = !placeToggle.getChild(0).asToggle().getState()
					? (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL ? mc.player.getInventory().getSelectedSlot()
							: mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL ? 40
									: -1)
							: InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.END_CRYSTAL);

			if (crystalSlot == -1) {
				return;
			}

			boolean supportOn = placeToggle.getChild(9).asToggle().getState();
			double minDmg = placeToggle.getChild(4).asSlider().getValue();
			double minRatio = placeToggle.getChild(5).asSlider().getValue();

			Map<BlockPos, Float> placeBlocks;
			Map<BlockPos, Float> placeDamage;

			if (supportOn) {
				// Support turns most of the range into a candidate (any replaceable block, not just
				// existing obsidian/bedrock), so the scan + per-candidate explosion damage math is
				// heavy enough to lag the render thread. Run it in the background instead and place
				// off whatever the last completed scan found - one scan in flight at a time, so scans
				// don't pile up faster than they finish.
				if (searchExecutor != null && searching.compareAndSet(false, true)) {
					searchExecutor.submit(() -> {
						try {
							ScoreResult result = scoreCandidates(targets, minDmg, minRatio);
							cachedPlaceBlocks = result.blocks();
							cachedPlaceDamage = result.damage();
						} catch (Exception ex) {
							// World/entity state can shift mid-scan since it's read off-thread while
							// the game keeps ticking - just drop this cycle's result and retry next.
						} finally {
							searching.set(false);
						}
					});
				}

				placeBlocks = cachedPlaceBlocks;
				placeDamage = cachedPlaceDamage;
			} else {
				ScoreResult result = scoreCandidates(targets, minDmg, minRatio);
				placeBlocks = result.blocks();
				placeDamage = result.damage();
			}

			int oldSlot = mc.player.getInventory().getSelectedSlot();
			int places = 0;
			for (Entry<BlockPos, Float> e : placeBlocks.entrySet()) {
				BlockPos block = e.getKey();

				// One support block per tick, max - placing several in the same tick is exactly the
				// kind of burst that gets them rubberbanded/invalidated server-side.
				if (supportOn && !isSolidBase(block)) {
					if (supportCooldown <= 0) {
						placeSupportBlock(block);
						supportCooldown = 1;
					}
					pendingSupport.add(block);
					continue;
				}

				if (pendingSupport.remove(block)) {
					// Even at a Support Delay of 0, the support block and the crystal still can't
					// land in the same tick without looking like the same burst - one tick minimum.
					int delay = Math.max(1, placeToggle.getChild(9).asToggle().getChild(0).asSlider().getValueInt());
					supportDelay.put(block, delay);
				}

				if (supportDelay.getOrDefault(block, 0) > 0) {
					continue;
				}
				supportDelay.remove(block);

				Vec3d eyeVec = mc.player.getEyePos();

				Vec3d vec = Vec3d.ofCenter(block, 1);
				Direction dir = null;
				for (Direction d : Direction.values()) {
					Vec3d vd = WorldUtils.getLegitLookPos(block, d, true, 5);
					if (vd != null && eyeVec.distanceTo(vd) <= eyeVec.distanceTo(vec)) {
						vec = vd;
						dir = d;
					}
				}

				if (dir == null) {
					if (placeToggle.getChild(3).asToggle().getState())
						continue;

					dir = Direction.UP;
				}

				if (placeToggle.getChild(2).asToggle().getState())
					blacklist.put(block, 4);

				if (getSetting(7).asRotate().getState()) {
					WorldUtils.facePosAuto(vec.x, vec.y, vec.z, getSetting(7).asRotate());
				}

				Hand hand = InventoryUtils.selectSlot(crystalSlot);

				render = block;
				renderDamage = placeDamage.getOrDefault(block, 0f);
				mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(vec, dir, block, false));

				places++;
				if (places >= placeToggle.getChild(6).asSlider().getValueInt()) {
					break;
				}
			}

			if (places > 0) {
				if (placeToggle.getChild(0).asToggle().getState()
						&& placeToggle.getChild(0).asToggle().getChild(0).asToggle().getState()) {
					InventoryUtils.selectSlot(oldSlot);
				}

				placeCooldown = placeToggle.getChild(7).asSlider().getValueInt() + 1;
			}
		}
	}

	private record ScoreResult(Map<BlockPos, Float> blocks, Map<BlockPos, Float> damage) {}

	// Read-only (world/entity queries + math, no packets/rotation/inventory changes) so it's safe
	// to run off the main thread for Support.
	private ScoreResult scoreCandidates(List<LivingEntity> targets, double minDmg, double minRatio) {
		Map<BlockPos, Float> placeBlocks = new LinkedHashMap<>();
		Map<BlockPos, Float> placeDamage = new HashMap<>();

		for (Vec3d v : getCrystalPoses()) {
			float playerDamg = DamageUtils.getExplosionDamage(v, 6f, mc.player);

			if (DamageUtils.willKill(mc.player, playerDamg))
				continue;

			for (LivingEntity e : targets) {
				float targetDamg = DamageUtils.getExplosionDamage(v, 6f, e);
				if (DamageUtils.willPop(mc.player, playerDamg) && !DamageUtils.willPopOrKill(e, targetDamg)) {
					continue;
				}

				if (targetDamg >= minDmg) {
					float ratio = playerDamg == 0 ? targetDamg : targetDamg / playerDamg;

					if (ratio > minRatio) {
						BlockPos pos = BlockPos.ofFloored(v).down();
						placeBlocks.put(pos, ratio);
						placeDamage.merge(pos, targetDamg, Math::max);
					}
				}
			}
		}

		Map<BlockPos, Float> sorted = placeBlocks.entrySet().stream()
				.sorted((b1, b2) -> Float.compare(b2.getValue(), b1.getValue()))
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x, y) -> y, LinkedHashMap::new));

		return new ScoreResult(sorted, placeDamage);
	}

	@BleachSubscribe
	public void onRenderWorld(EventWorldRender.Post event) {
		if (this.render != null) {
			int[] col = getSetting(4).asToggle().getChild(8).asColor().getRGBArray();
			Renderer.drawBoxBoth(render, QuadColor.single(col[0], col[1], col[2], 100), 2.5f);

			if (getSetting(5).asToggle().getState()) {
				WorldRenderer.drawText(Text.literal(String.format("%.1f", renderDamage)),
						render.getX() + 0.5, render.getY() + 1.3, render.getZ() + 0.5, 1.5, true);
			}
		}
	}

	public Set<Vec3d> getCrystalPoses() {
		Set<Vec3d> poses = new HashSet<>();

		double range = getSetting(8).asSlider().getValue();
		int intRange = (int) Math.floor(range);
		Vec3d playerPos = mc.player.getEntityPos();
		BlockPos eyeBlock = BlockPos.ofFloored(mc.player.getEyePos());

		for (int x = -intRange; x <= intRange; x++) {
			for (int y = -intRange; y <= intRange; y++) {
				for (int z = -intRange; z <= intRange; z++) {
					BlockPos basePos = eyeBlock.add(x, y, z);
					Vec3d pos = Vec3d.of(basePos).add(0.5, 1, 0.5);

					// Cheapest check first - the loop bounds are a cube, but the real limit is a
					// sphere, so this alone skips the corners before any world/entity lookups run.
					// That corner waste barely mattered against the old obsidian/bedrock-only check
					// (it exits just as fast), but Support treats most of the cube as a candidate,
					// so skipping it early here matters a lot more now.
					if (playerPos.distanceTo(pos) > range + 0.25)
						continue;

					if (!canPlace(basePos) || (blacklist.containsKey(basePos) && getSetting(4).asToggle().getChild(2).asToggle().getState()))
						continue;

					if (getSetting(4).asToggle().getChild(3).asToggle().getState()) {
						boolean allBad = true;
						for (Direction d : Direction.values()) {
							if (WorldUtils.getLegitLookPos(basePos, d, true, 5) != null) {
								allBad = false;
								break;
							}
						}

						if (allBad) {
							continue;
						}
					}

					poses.add(pos);
				}
			}
		}

		return poses;
	}

	private boolean isSolidBase(BlockPos basePos) {
		BlockState baseState = mc.world.getBlockState(basePos);
		return baseState.getBlock() == Blocks.BEDROCK || baseState.getBlock() == Blocks.OBSIDIAN;
	}

	private boolean canPlace(BlockPos basePos) {
		if (!isSolidBase(basePos)) {
			boolean supportOn = getSetting(4).asToggle().getChild(9).asToggle().getState();
			if (!supportOn || !mc.world.getBlockState(basePos).isReplaceable())
				return false;
		}

		boolean oldPlace = getSetting(4).asToggle().getChild(1).asToggle().getState();
		BlockPos placePos = basePos.up();
		if (!mc.world.isAir(placePos) || (oldPlace && !mc.world.isAir(placePos.up())))
			return false;

		return mc.world.getOtherEntities(null, new Box(Vec3d.of(placePos), Vec3d.of(placePos.up(oldPlace ? 2 : 1)))).isEmpty();
	}

	// Meteor's AirPlace doesn't rely on any cooldown/timing trick - it just builds its own
	// BlockHitResult and feeds it straight into the normal interact call, instead of the crosshair
	// raycast (which reports MISS on a replaceable block and would otherwise stop vanilla from
	// ever attempting a placement there). CrystalAura's own crystal placement already does exactly
	// that for solid blocks, so the support block just needs the same call aimed at the air spot.
	private void placeSupportBlock(BlockPos pos) {
		int obsidianSlot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN);
		if (obsidianSlot == -1) {
			return;
		}

		Hand hand = InventoryUtils.selectSlot(obsidianSlot);
		Vec3d eyeVec = mc.player.getEyePos();

		// Prefer aiming at a real solid neighbor's face when one happens to be there, purely so the
		// resulting hitResult/rotation looks legitimate - falls back to aiming straight at the
		// empty spot itself, which vanilla places into directly since it's replaceable.
		BlockPos clickedPos = pos;
		Direction side = Direction.UP;
		Vec3d vec = Vec3d.ofCenter(pos);

		for (Direction d : Direction.values()) {
			BlockPos neighbor = pos.offset(d);
			if (!mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) {
				continue;
			}

			Vec3d vd = WorldUtils.getLegitLookPos(neighbor, d.getOpposite(), true, 5);
			if (vd != null && eyeVec.distanceTo(vd) <= eyeVec.distanceTo(vec)) {
				vec = vd;
				side = d.getOpposite();
				clickedPos = neighbor;
			}
		}

		mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(vec, side, clickedPos, false));
	}
}

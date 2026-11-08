package com.hitbeacons;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Tracks every player you hit. Each player (keyed by UUID) has its own color slot and its own
 * independent 20 second timer. All access happens on the client main thread.
 */
public final class BeaconTracker {
	/** How long a beacon lives after the LAST successful hit on that player. */
	public static final long BEACON_DURATION_NANOS = 20L * 1_000_000_000L;

	/**
	 * true  = only count a swing as a hit once the target's hurt animation proves damage landed
	 *         (ignores misses, blocked hits and hits during invulnerability frames).
	 * false = mark the player the moment you attack them.
	 */
	public static final boolean REQUIRE_CONFIRMED_DAMAGE = true;

	/** Ticks (20/s) to wait for the server to confirm damage after a swing. Covers ~600 ms ping. */
	private static final int CONFIRM_WINDOW_TICKS = 12;

	public static final class Mark {
		public final UUID id;
		public final int slot;
		public final float r;
		public final float g;
		public final float b;
		long expiresAt;

		Mark(UUID id, int slot, long expiresAt) {
			this.id = id;
			this.slot = slot;
			this.expiresAt = expiresAt;
			int rgb = BeaconColors.rgb(slot);
			this.r = ((rgb >> 16) & 0xFF) / 255.0f;
			this.g = ((rgb >> 8) & 0xFF) / 255.0f;
			this.b = (rgb & 0xFF) / 255.0f;
		}
	}

	private static final class Pending {
		int lastHurtTime;
		int ticksLeft;
	}

	private final Map<UUID, Mark> marks = new LinkedHashMap<>();
	private final Map<UUID, Pending> pending = new HashMap<>();

	/** Called when the local player swings at another player. */
	public void onAttack(AbstractClientPlayer target) {
		UUID id = target.getUUID();
		if (!REQUIRE_CONFIRMED_DAMAGE) {
			markHit(id, System.nanoTime());
			return;
		}
		Pending p = new Pending();
		p.lastHurtTime = target.hurtTime;
		p.ticksLeft = CONFIRM_WINDOW_TICKS;
		pending.put(id, p);
	}

	/** Called once per client tick. */
	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			clear();
			return;
		}
		long now = System.nanoTime();

		Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> e = it.next();
			Pending p = e.getValue();
			Player target = level.getPlayerByUUID(e.getKey());
			if (target == null) {
				it.remove();
				continue;
			}
			int hurt = target.hurtTime;
			if (hurt > p.lastHurtTime) {
				// hurtTime jumped up: a new hit just landed on this player.
				markHit(e.getKey(), now);
				it.remove();
				continue;
			}
			p.lastHurtTime = hurt;
			if (--p.ticksLeft <= 0) {
				it.remove();
			}
		}

		marks.values().removeIf(m -> now >= m.expiresAt);
	}

	private void markHit(UUID id, long now) {
		Mark existing = marks.get(id);
		if (existing != null) {
			// Same player again: reset ONLY this player's timer, keep their color.
			existing.expiresAt = now + BEACON_DURATION_NANOS;
			return;
		}
		Set<Integer> used = new HashSet<>();
		for (Mark m : marks.values()) {
			used.add(m.slot);
		}
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		marks.put(id, new Mark(id, slot, now + BEACON_DURATION_NANOS));
	}

	public Collection<Mark> marks() {
		return marks.values();
	}

	public boolean isEmpty() {
		return marks.isEmpty();
	}

	public void clear() {
		marks.clear();
		pending.clear();
	}
}

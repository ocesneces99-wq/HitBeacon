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

public final class BeaconTracker {
	public static final long BEACON_DURATION_NANOS = 20L * 1_000_000_000L;

	/**
	 * false = mark a player the moment you swing at them (works on every player).
	 * true  = only mark once the target was visibly hurt.
	 */
	public static final boolean REQUIRE_CONFIRMED_DAMAGE = false;

	private static final int CONFIRM_WINDOW_TICKS = 16;

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
		int ticksLeft;
	}

	private final Map<UUID, Mark> marks = new LinkedHashMap<>();
	private final Map<UUID, Pending> pending = new HashMap<>();
	private boolean enabled = true;

	public boolean isEnabled() {
		return enabled;
	}

	/** Flips beacons on/off. Turning off clears all beacons. Returns the new state. */
	public boolean toggle() {
		enabled = !enabled;
		if (!enabled) {
			clear();
		}
		return enabled;
	}

	public void onAttack(AbstractClientPlayer target) {
		if (!enabled) {
			return;
		}
		UUID id = target.getUUID();
		if (!REQUIRE_CONFIRMED_DAMAGE) {
			markHit(id, System.nanoTime());
			return;
		}
		Pending p = new Pending();
		p.ticksLeft = CONFIRM_WINDOW_TICKS;
		pending.put(id, p);
	}

	public void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			clear();
			return;
		}
		if (!enabled) {
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
			if (target.hurtTime >= 9) {
				markHit(e.getKey(), now);
				it.remove();
				continue;
			}
			if (--p.ticksLeft <= 0) {
				it.remove();
			}
		}

		marks.values().removeIf(m -> now >= m.expiresAt);
	}

	private void markHit(UUID id, long now) {
		Mark existing = marks.get(id);
		if (existing != null) {
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

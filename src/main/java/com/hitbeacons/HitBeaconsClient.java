package com.hitbeacons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

public class HitBeaconsClient implements ClientModInitializer {
	public static final String MOD_ID = "hitbeacons";

	private final BeaconTracker tracker = new BeaconTracker();
	private BeaconRenderer renderer;

	@Override
	public void onInitializeClient() {
		renderer = new BeaconRenderer(tracker);

		// Hit detection. This event also fires for the integrated server's player in singleplayer,
		// so only react to the local player attacking a *client-side* player entity.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof LocalPlayer
					&& entity instanceof AbstractClientPlayer target
					&& entity != Minecraft.getInstance().player) {
				tracker.onAttack(target);
			}
			return InteractionResult.PASS; // never cancel or alter the attack
		});

		ClientTickEvents.END_CLIENT_TICK.register(tracker::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> tracker.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tracker.clear());

		// If beams ever fail to show, try END_MAIN here instead (see README).
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(renderer::render);

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> renderer.close());
	}
}

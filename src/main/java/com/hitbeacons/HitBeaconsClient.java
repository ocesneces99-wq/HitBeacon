package com.hitbeacons;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

		// Toggle key: "=" by default, rebindable in Options > Controls > Key Binds.
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.hitbeacons.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_EQUAL,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				boolean on = tracker.toggle();
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("Hit Beacons: " + (on ? "ON" : "OFF")), true);
				}
			}
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof LocalPlayer
					&& entity instanceof AbstractClientPlayer target
					&& entity != Minecraft.getInstance().player) {
				tracker.onAttack(target);
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(tracker::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> tracker.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tracker.clear());

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(renderer::render);

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> renderer.close());
	}
}

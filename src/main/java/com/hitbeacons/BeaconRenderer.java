package com.hitbeacons;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Draws all beacons in a single batched draw call using a no-depth-test pipeline, so they show
 * through blocks, walls, entities and crowds. Structure follows Fabric's 1.21.11 "Rendering in
 * the World" guide.
 */
public final class BeaconRenderer {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(HitBeaconsClient.MOD_ID, "pipeline/hit_beacon_through_walls"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	/** Beam length above the player's head, in blocks. */
	private static final double BEAM_HEIGHT = 320.0;
	/** Beam starts this far above the player's head so it never covers the player. */
	private static final double HEAD_GAP = 0.3;

	private final BeaconTracker tracker;
	private MappableRingBuffer vertexBuffer;

	public BeaconRenderer(BeaconTracker tracker) {
		this.tracker = tracker;
	}

	public void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || tracker.isEmpty()) {
			return;
		}

		PoseStack matrices = context.matrices();
		Matrix4fc pose = matrices.last().pose();
		Vec3 cam = context.worldState().cameraRenderState.pos;
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = System.nanoTime();

		BufferBuilder buffer = null;

		for (BeaconTracker.Mark mark : tracker.marks()) {
			if (now >= mark.expiresAt) {
				continue;
			}
			Player p = level.getPlayerByUUID(mark.id);
			if (p == null || p.isRemoved() || !p.isAlive()) {
				continue; // out of render distance / dead: hidden, timer keeps running
			}

			// Interpolated position -> beam follows smoothly every frame.
			double x = p.xo + (p.getX() - p.xo) * partialTick - cam.x;
			double y = p.yo + (p.getY() - p.yo) * partialTick - cam.y;
			double z = p.zo + (p.getZ() - p.zo) * partialTick - cam.z;

			double dist = Math.sqrt(x * x + y * y + z * z);
			// Thickness grows with distance so beams stay easy to spot from far away.
			double core = Math.min(0.6, 0.06 + 0.0025 * dist);
			double glow = core * 2.6;

			float minY = (float) (y + p.getBbHeight() + HEAD_GAP);
			float maxY = (float) (y + p.getBbHeight() + HEAD_GAP + BEAM_HEIGHT);

			if (buffer == null) {
				buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
			}

			box(pose, buffer, (float) (x - glow), minY, (float) (z - glow), (float) (x + glow), maxY, (float) (z + glow),
					mark.r, mark.g, mark.b, 0.28f);
			box(pose, buffer, (float) (x - core), minY, (float) (z - core), (float) (x + core), maxY, (float) (z + core),
					mark.r, mark.g, mark.b, 0.90f);
		}

		if (buffer != null) {
			drawBuffer(mc, buffer);
		}
	}

	private static void box(Matrix4fc m, BufferBuilder b, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			float r, float g, float bl, float a) {
		// Front
		b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		// Back
		b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		// Left
		b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		// Right
		b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		// Top
		b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		// Bottom
		b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
	}

	private void drawBuffer(Minecraft client, BufferBuilder buffer) {
		MeshData builtBuffer = buffer.buildOrThrow();
		MeshData.DrawState drawParameters = builtBuffer.drawState();
		VertexFormat format = drawParameters.format();

		GpuBuffer vertices = upload(drawParameters, format, builtBuffer);
		draw(client, builtBuffer, drawParameters, vertices, format);

		vertexBuffer.rotate();
	}

	private GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
		int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

		if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = new MappableRingBuffer(() -> HitBeaconsClient.MOD_ID + " beacon vertices",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
		}

		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
				vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
		}

		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, MeshData builtBuffer, MeshData.DrawState drawParameters, GpuBuffer vertices,
			VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;

		if (PIPELINE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			// Sort quads back-to-front so overlapping translucent beams blend correctly.
			builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
			indexType = builtBuffer.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
			indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
			indexType = shapeIndexBuffer.type();
		}

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> HitBeaconsClient.MOD_ID + " beacon pass",
						client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
						client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(PIPELINE);

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			renderPass.setVertexBuffer(0, vertices);
			renderPass.setIndexBuffer(indices, indexType);
			renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
		}

		builtBuffer.close();
	}

	public void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}

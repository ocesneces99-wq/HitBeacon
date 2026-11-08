package com.hitbeacons;

/** Beacon colors by slot. Slots 0-5 are the requested order; later slots stay distinct. */
public final class BeaconColors {
	private static final int[] PALETTE = {
		0xFF2020, // 0 RED
		0x2F5BFF, // 1 BLUE
		0x20E020, // 2 GREEN
		0xB030F0, // 3 PURPLE
		0xFFE820, // 4 YELLOW
		0x20F0F0, // 5 CYAN
		0xFF8000, // 6 ORANGE
		0xFF69B4, // 7 PINK
		0xFFFFFF, // 8 WHITE
		0x9AFF00, // 9 LIME
	};

	private BeaconColors() {
	}

	public static int rgb(int slot) {
		if (slot < PALETTE.length) {
			return PALETTE[slot];
		}
		// Beyond the palette: golden-ratio hue steps keep neighbouring slots far apart.
		float hue = ((slot - PALETTE.length) * 0.61803398875f + 0.07f) % 1.0f;
		return hsvToRgb(hue, 0.85f, 1.0f);
	}

	private static int hsvToRgb(float h, float s, float v) {
		float h6 = h * 6.0f;
		int i = (int) Math.floor(h6);
		float f = h6 - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);
		float r;
		float g;
		float b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}
}

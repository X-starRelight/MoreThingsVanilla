package com.tt23xrstudio.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CloudStatus;

/** Same camera, same sun, then a red wall with a 3x4 sky window. */
public final class BoxVisualTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.options.cloudStatus().set(CloudStatus.FANCY));
		try (var world = ctx.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamemode spectator @a");
			server.runCommand("time set 1000");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("weather clear");
			server.runCommand("tp @a -1.5 80 0.5 -90 -15");
			server.runCommand("setblock 4 81 3 more-things-vanilla:end_box");
			server.runCommand("setblock 4 80 2 minecraft:end_portal");
			world.getConnection().waitForClientboundPackets();
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(40);
			BufferedImage reference = ImageIO.read(ctx.takeScreenshot("box-open-sky").toFile());
			server.runCommand("fill 7 80 -5 7 86 5 minecraft:red_concrete");
			server.runCommand("fill 4 81 -1 4 84 1 more-things-vanilla:sky_box");
			world.getConnection().waitForClientboundPackets();
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(40);
			BufferedImage window = ImageIO.read(ctx.takeScreenshot("box-sky-window").toFile());
			// The adjacent wall must be visible, while sky inside the window stays
			// closer to open sky. Relative comparison tolerates exposure changes.
			double inside = change(reference, window, 0.45, 0.55);
			double wall = change(reference, window, 0.32, 0.38);
			if (wall < 10 || inside >= wall / 2) {
				throw new AssertionError("Sky window mismatch: inside=" + inside + ", adjacent wall=" + wall);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static double change(BufferedImage a, BufferedImage b, double left, double right) {
		int sum = 0, samples = 0;
		for (int y = a.getHeight()*4/10; y < a.getHeight()*6/10; y += 2) {
			for (int x = (int)(a.getWidth()*left); x < a.getWidth()*right; x += 2) {
				int old = a.getRGB(x,y), current = b.getRGB(x,y);
				for (int shift = 0; shift < 24; shift += 8) {
					sum += Math.abs(((old >> shift) & 255) - ((current >> shift) & 255));
					samples++;
				}
			}
		}
		return (double)sum / samples;
	}
}

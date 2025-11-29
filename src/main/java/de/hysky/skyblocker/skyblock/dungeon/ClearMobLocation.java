package de.hysky.skyblocker.skyblock.dungeon;

import com.mojang.logging.LogUtils;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfig;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.DungeonsConfig;
import de.hysky.skyblocker.utils.Utils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager.*;

public class ClearMobLocation {
	public static final String LOG_FILE_PATH = FabricLoader.getInstance().getGameDir() + File.separator + "skyblockerClearLocation.log";
	private static final Pattern KEY_PICKUP = Pattern.compile("^.+ (?<username>\\S+) has obtained (Blood|Wither) Key!$");
	//	private static final Pattern KEY_FOUND = Pattern.compile("^RIGHT CLICK on (?:the BLOOD DOOR|a WITHER door) to open it. This key can only be used to open 1 door!$");
	private static final Logger LOGGER = LogUtils.getLogger();

	@Init
	public static void init() {
//		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
//		Scheduler.INSTANCE.scheduleCyclic(ClearMobLocation::tick, 20);
//		DungeonEvents.DUNGEON_STARTED.register(ClearMobLocation::onDungeonStart);

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (!overlay && Utils.isInDungeons() && SkyblockerConfigManager.get().dungeons.recordClearLocations) {
				onChat(message.getString());
			}
			return true;
		});
	}

//	private static boolean dungeonStarted;
//
//	private static void reset() {
//		dungeonStarted = false;
//	}
//
//	private static void onDungeonStart() {
//		reset();
//		dungeonStarted = true;
//	}

	private static void onChat(String chatMessage) {
		Matcher match = KEY_PICKUP.matcher(chatMessage);
		if (match.matches()) {
			LOGGER.info("[Skyblocker ClearMobLocation] some1 picked up a room key");

			final var pickupPlayerName = match.group("username");
			playerPickupKey(pickupPlayerName);
		}
	}

	private static void playerPickupKey(String pickupPlayerName) {
		MinecraftClient client = MinecraftClient.getInstance();
		final var selfName = client.getSession().getUsername();
		final var didSelfPickupKey = Objects.equals(pickupPlayerName, selfName);

		if (!isCurrentRoomMatched()) return;
		if (!didSelfPickupKey) return;
		// for all non-self pickup's:
		// i dont think thats possible because the mod dosent "index" rooms that we havent yet visited
		// cuz that would involve checking blocks that are "out of reach",
		// but in theory: get that player's position -> get the room at their pos -> get their relative position


		assert client.player != null;
		final var playerPosition = client.player.getBlockPos();
		final var relativePosition = getCurrentRoom().actualToRelative(playerPosition);
		final var roomName = getCurrentRoom().getName();
		LOGGER.info("[Skyblocker ClearMobLocation] Key pickup pos:{} roomName:{}", relativePosition.toString(), roomName);
		recordKeyPickup(roomName, relativePosition);
	}

	private static void recordKeyPickup(String roomName, BlockPos location) {
		// create new writer for each write, cuz i can't be bothered
		try (var writer = new FileWriter(LOG_FILE_PATH, true)) {
			writer.write(roomName + ": " + location.toShortString() + "\n");
		} catch (IOException e) {
			LOGGER.info("[Skyblocker ClearMobLocation] Something went wrong writing to the log file: {}", e.toString());
		}
	}

//	private static void tick() {
//		MinecraftClient client = MinecraftClient.getInstance();
//
//		if (!(Utils.isInDungeons() && DungeonManager.isInBoss() && client.player != null && client.world != null)) return;
//		if (!dungeonStarted) return;
//	}
}

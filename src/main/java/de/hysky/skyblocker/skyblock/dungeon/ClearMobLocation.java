package de.hysky.skyblocker.skyblock.dungeon;

import com.jcraft.jorbis.Block;
import com.mojang.logging.LogUtils;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.events.DungeonEvents;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import kotlin.reflect.jvm.internal.impl.util.ArrayMap;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.logging.Log;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager.*;

public class ClearMobLocation {
	public static final String LOG_FILE_PATH = FabricLoader.getInstance().getGameDir() + File.separator + "skyblockerClearLocation.log";
	public static final String MOB_LOG_FILE_PATH = FabricLoader.getInstance().getGameDir() + File.separator + "skyblockerBestMatchClearMobPosition.log";
	private static final Pattern KEY_PICKUP = Pattern.compile("^.+ (?<username>\\S+) has obtained (Blood|Wither) Key!$");
	private static final Pattern MOB_LIKE_ENTITY_NAME = Pattern.compile(".+❤");
	//	"A Wither Key was picked up" also appears in the game
	//	private static final Pattern KEY_FOUND = Pattern.compile("^RIGHT CLICK on (?:the BLOOD DOOR|a WITHER door) to open it. This key can only be used to open 1 door!$");
	private static final Logger LOGGER = LogUtils.getLogger();

	@Init
	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
		Scheduler.INSTANCE.scheduleCyclic(ClearMobLocation::onTick, 1);
		DungeonEvents.DUNGEON_STARTED.register(ClearMobLocation::onDungeonStart);


		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (!overlay && Utils.isInDungeons() && SkyblockerConfigManager.get().dungeons.recordClearLocations) {
				onChat(message.getString());
			}
			return true;
		});
	}

	private static boolean dungeonStarted;

	private static void reset() {
		dungeonStarted = false;
		checkedEntityIds.clear();
		entitySpawnLocations.clear();
		entityMetas.clear();
	}

	private static void onDungeonStart() {
		reset();
		dungeonStarted = true;
	}

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

	static Vector<Integer> checkedEntityIds = new Vector<>();

	static HashMap<Integer, BlockPos> entitySpawnLocations = new HashMap<>();
	static HashMap<Integer, EntityMeta> entityMetas = new HashMap<>();

	record EntityMeta(int id, String name, BlockPos pos) {}

	private static void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.world == null || client.player == null || !Utils.isInDungeons() || DungeonManager.isInBoss() || !dungeonStarted) return;

		client.world.getEntities().forEach(e -> { // TODO: use some already existing function for filtering by type
			if (e instanceof ArmorStandEntity) {
				final var id = e.getId();
				final var customName = e.getCustomName();

				if (customName != null) {
					entityMetas.put(id, new EntityMeta(id, customName.getString(), e.getBlockPos()));
				}

				if (!checkedEntityIds.contains(id)) { // only new entities
					checkedEntityIds.add(id);

					// note the first position we saw the entity
					entitySpawnLocations.put(e.getId(), e.getBlockPos());


					if (customName != null) {
//						LOGGER.info("name: {}, id: {}", customName.getString(), e.getId());
						if (Objects.equals(customName.getString(), "Wither Key") || Objects.equals(customName.getString(), "Blood Key")) {
							var keyGlobalPos = e.getBlockPos();
							var roomRelativePos = getCurrentRoom().actualToRelative(keyGlobalPos);
							LOGGER.info("[Skyblocker] Found a wither|blood key like entity at rel:{}, type: {}", roomRelativePos.toString(), e.getType().getName());

							var distanceOrderedMetas = new TreeMap<Double, EntityMeta>();

							// calculate distances
							entityMetas.forEach((savedId, meta) -> {
								distanceOrderedMetas.put(keyGlobalPos.getSquaredDistance(meta.pos), meta);
							});


//							LOGGER.info("CLOSES ENTITIES TO KEY");
//							int i = 0;
//							for(var entry: distanceOrderedMetas.entrySet()) {
//								if(i > 5) break;
//								double distance = entry.getKey();
//								EntityMeta meta = entry.getValue();
//								if (MOB_LIKE_ENTITY_NAME.matcher(meta.name).matches()) {
//									i += 1;
//									var spawnPos = entitySpawnLocations.get(meta.id);
//									LOGGER.info("entity id:{}, distance:{}, rel position: {}, rel spawn pos: {},  name: {}", meta.id, distance, getCurrentRoom().actualToRelative(meta.pos), getCurrentRoom().actualToRelative(spawnPos), meta.name);
//								}
//							}

							for(var entry: distanceOrderedMetas.entrySet()) {
								EntityMeta meta = entry.getValue();
								if (!MOB_LIKE_ENTITY_NAME.matcher(meta.name).matches()) continue;

								BlockPos relativeSpawnLocation = getCurrentRoom().actualToRelative(entitySpawnLocations.get(meta.id));
								LOGGER.info("Best ClearMob Candidate: name: {}, relative spawn position: {}", meta.name, relativeSpawnLocation.toString());
								recordBestMatchClearMobPosition(getCurrentRoom().getName(), relativeSpawnLocation);
								return;
							}
						}
					}
				}
			}
		});

	}


	private static void recordBestMatchClearMobPosition(String roomName, BlockPos location) {
		// create new writer for each write, cuz i can't be bothered
		try (var writer = new FileWriter(MOB_LOG_FILE_PATH, true)) {
			writer.write(roomName + ": " + location.toShortString() + "\n");
		} catch (IOException e) {
			LOGGER.info("[Skyblocker ClearMobLocation] Something went wrong writing to the log file: {}", e.toString());
		}
	}
}

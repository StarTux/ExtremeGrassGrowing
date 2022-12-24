package com.cavetale.egg;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.font.Unicode;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.core.util.Json;
import com.cavetale.mytems.Mytems;
import com.destroystokyo.paper.MaterialTags;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * The runtime class of a game of Extreme Grass Growing. An instance
 * manages its Arena and State.
 */
public final class Game {
    protected final ExtremeGrassGrowingPlugin plugin;
    @Getter protected final String name;
    protected Arena arena;
    protected State state;
    protected Random random = ThreadLocalRandom.current();
    protected int growCooldown = 50;
    protected boolean explodeGrass = true;
    protected List<Material> flowers =
        Stream.concat(Stream.of(Material.values())
                      .filter(m -> Tag.FLOWERS.isTagged(m)),
                      Stream.of(Material.GRASS, Material.TALL_GRASS,
                                // Material.BROWN_MUSHROOM,
                                // Material.RED_MUSHROOM,
                                Material.CRIMSON_FUNGUS,
                                Material.WARPED_FUNGUS))
        .collect(Collectors.toList());
    protected List<Material> signs = Arrays
        .asList(Material.ACACIA_SIGN,
                Material.BIRCH_SIGN,
                Material.DARK_OAK_SIGN,
                Material.JUNGLE_SIGN,
                Material.OAK_SIGN,
                Material.SPRUCE_SIGN,
                Material.CRIMSON_SIGN,
                Material.WARPED_SIGN);
    protected List<Material> dirts = List.of(new Material[] {
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.ROOTED_DIRT,
        });
    protected List<Snowman> snowmen = new ArrayList<>();
    protected Map<Vec, ArmorStand> armorStands = new HashMap<>();
    protected BukkitTask task;
    protected File arenaFile;
    protected File stateFile;
    protected Duration placeTime = Duration.ofSeconds(120);
    protected Duration endTime = Duration.ofSeconds(30);

    protected Game(final ExtremeGrassGrowingPlugin plugin, final String name) {
        this.plugin = plugin;
        this.name = name;
        this.arenaFile = new File(plugin.arenasFolder, name + ".json");
        this.stateFile = new File(plugin.statesFolder, name + ".json");
    }

    protected void enable() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (UUID uuid : List.copyOf(state.snowmen)) {
            if (Bukkit.getEntity(uuid) instanceof Snowman snowman) {
                snowmen.add(snowman);
            } else {
                state.snowmen.remove(uuid);
            }
        }
    }

    protected void disable() {
        cleanUp();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    protected void loadArena() {
        arena = Json.load(arenaFile, Arena.class, Arena::new);
    }

    protected void saveArena() {
        Json.save(arenaFile, arena, true);
    }

    protected void loadState() {
        state = Json.load(stateFile, State.class, State::new);
    }

    protected void saveState() {
        Json.save(stateFile, state, true);
    }

    protected void cleanUp() {
        for (ArmorStand as : armorStands.values()) {
            as.remove();
        }
        armorStands.clear();
        cleanUpSnowmen();
    }

    protected void cleanUpSnowmen() {
        for (Snowman snowman : snowmen) {
            snowman.remove();
        }
        snowmen.clear();
        for (UUID uuid : state.snowmen) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity instanceof Snowman snowman) {
                snowman.remove();
            }
        }
        state.snowmen.clear();
    }

    public boolean isInArena(Entity entity) {
        return isInArena(entity.getLocation());
    }

    public boolean isInArena(Location location) {
        return location.getWorld().getName().equals(arena.world)
            && arena.area.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean isInArena(Block block) {
        return block.getWorld().getName().equals(arena.world)
            && arena.area.contains(block.getX(), block.getY(), block.getZ());
    }

    private Material randomSign() {
        return signs.get(random.nextInt(signs.size()));
    }

    private boolean isSign(Material mat) {
        return signs.contains(mat);
    }

    private void spreadTo(Block block) {
        Material flower = flowers.get(random.nextInt(flowers.size()));
        Component flowerName = flower.isItem()
            ? ItemKinds.chatDescription(new ItemStack(flower))
            : text(toCamelCase(" ", flower));
        boolean removed = purgeSign(block, flowerName);
        if (removed) {
            growCooldown = 200;
            saveState();
        }
        BlockData flowerData = flower.createBlockData();
        block.getRelative(0, 1, 0).setBlockData(flowerData, false);
        if (flowerData instanceof Bisected) {
            Bisected bisected = (Bisected) flowerData;
            bisected.setHalf(Bisected.Half.TOP);
            block.getRelative(0, 2, 0).setBlockData(bisected, false);
        }
        block.getWorld().playSound(block.getLocation(),
                                   Sound.BLOCK_GRASS_BREAK,
                                   SoundCategory.MASTER,
                                   1.0f, 1.0f);
        block.getWorld().spawnParticle(Particle.BLOCK_DUST,
                                       block.getLocation().add(0.5, 1.5, 0.5),
                                       8,
                                       0.2, 0.2, 0.2,
                                       0.0,
                                       Material.GRASS_BLOCK.createBlockData());
        if (removed) checkForWinner();
    }

    protected boolean isMainEventGame() {
        return plugin.global.event && name.equals(plugin.global.mainGame);
    }

    private void checkForWinner() {
        if (plugin.global.debug) return;
        if (state.placedSigns.size() == 1) {
            cleanUp();
            Placed winner = state.placedSigns.get(0);
            Player winningPlayer = Bukkit.getPlayer(winner.owner);
            String winnerName = winningPlayer != null
                ? winningPlayer.getName()
                : winner.ownerName;
            Component winnerDisplayName = winningPlayer != null
                ? winningPlayer.displayName()
                : text(winner.ownerName);
            announceArena(join(noSeparators(), new Component[] {
                        newline(),
                        winnerDisplayName,
                        text(" wins the game!"),
                        newline(),
                    }).color(GREEN));
            state.winners.add(winnerName);
            setupGameState(GameState.END);
            if (isMainEventGame()) {
                plugin.getLogger().info(name + ": " + winnerName + " wins the game!");
                String cmd = "titles unlockset " + winnerName + " "
                    + String.join(" ", (state.snow
                                        ? plugin.SNOW_WINNER_TITLES
                                        : plugin.WINNER_TITLES));
                plugin.getLogger().info("Running command: " + cmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                if (winningPlayer != null) {
                    Mytems.KITTY_COIN.giveItemStack(winningPlayer, 1);
                }
            }
        } else if (state.placedSigns.size() == 0) {
            if (isMainEventGame()) {
                plugin.getLogger().info(name + ": Nobody wins the game!");
            }
            announceArena(text("\nNobody wins the game!\n ", GREEN));
            cleanUp();
            setupGameState(GameState.END);
        }
    }

    protected void announceArena(String msg) {
        for (Player player : getPlayers()) {
            player.sendMessage(msg);
        }
    }

    protected void announceArena(Component msg) {
        for (Player player : getPlayers()) {
            player.sendMessage(msg);
        }
    }


    private Placed findPlacedSign(Block block) {
        return findPlacedSign(block.getX(), block.getY(), block.getZ());
    }

    private Placed findPlacedSign(int x, int y, int z) {
        for (Placed placed : state.placedSigns) {
            if (x == placed.x && y == placed.y && z == placed.z) {
                return placed;
            }
        }
        return null;
    }

    private Placed findPlacedSign(Player player) {
        for (Placed placed : state.placedSigns) {
            if (placed.isOwner(player)) return placed;
        }
        return null;
    }

    protected boolean warpPlayerOutside(Player player, TeleportCause cause) {
        List<Vec> blocks = new ArrayList<>(arena.viewerBlocks);
        if (blocks.isEmpty()) return false;
        Vec block = blocks.get(random.nextInt(blocks.size()));
        World world = getWorld();
        world.getChunkAtAsync(block.x >> 4, block.z >> 4, (Consumer<Chunk>) chunk -> {
                Location loc = new Location(world,
                                            (double) block.x + 0.5,
                                            (double) block.y + 1.0,
                                            (double) block.z + 0.5,
                                            player.getLocation().getYaw(),
                                            player.getLocation().getPitch());
                player.teleport(loc, cause);
            });
        return true;
    }

    public World getWorld() {
        return Bukkit.getWorld(arena.world);
    }

    public List<Player> getPlayers() {
        List<Player> result = getWorld().getPlayers();
        result.removeIf(p -> !isInArena(p));
        return result;
    }

    private void tick() {
        if (state.gameState == GameState.PAUSE) return;
        if (getWorld() == null) {
            state.gameState = GameState.PAUSE;
            return;
        }
        if (getPlayers().isEmpty()) {
            cleanUp();
            setupGameState(GameState.PAUSE);
            return;
        }
        if (state.gameState == GameState.PLACE) {
            if (state.placedSigns.isEmpty()) {
                // reset timer
                state.placeStarted = System.currentTimeMillis();
                return;
            }
            long timeLeft = placeTime.toMillis() - (System.currentTimeMillis() - state.placeStarted);
            if (timeLeft < 0) {
                cleanUp();
                setupGameState(GameState.GROW);
            }
            return;
        }
        if (state.gameState == GameState.END) {
            long timeLeft = endTime.toMillis() - (System.currentTimeMillis() - state.endStarted);
            if (timeLeft < 0) {
                cleanUp();
                if (plugin.global.event && !isMainEventGame()) {
                    // During events, don't auto start non-event arenas.
                    setupGameState(GameState.PAUSE);
                } else {
                    setupGameState(GameState.PLACE);
                }
            }
            return;
        }
        if (state.gameState != GameState.GROW) return;
        World world = getWorld();
        if (world == null) return;
        updateArmorStands(world);
        if (world == null) return;
        if (state.snow) {
            if (snowmen.isEmpty()) {
                spawnSnowman();
            } else {
                for (Snowman snowman : List.copyOf(snowmen)) {
                    handleSnowman(snowman);
                }
            }
        } else {
            if (growCooldown == 0) {
                if (!state.spreadOptions.isEmpty()) {
                    Collections.shuffle(state.spreadOptions);
                    Vec vec = state.spreadOptions.get(0);
                    Block dirtBlock = vec.toBlock(world);
                    state.spreadOptions.clear();
                    spreadTo(dirtBlock);
                    dirtBlock.setType(Material.GRASS_BLOCK);
                }
                if (growCooldown < 80) growCooldown = 80; // spreadTo can update it
                return;
            }
            growCooldown -= 1;
            if (state.spreadOptions.isEmpty()) {
                state.signOption = false;
                List<Block> grassBlocks = arena.grassBlocks.stream()
                    .map(v -> world.getBlockAt(v.x, v.y, v.z))
                    .filter(b -> b.getType() == Material.GRASS_BLOCK)
                    .collect(Collectors.toList());
                if (grassBlocks.isEmpty()) return;
                Block grassBlock = grassBlocks.get(random.nextInt(grassBlocks.size()));
                for (int dx = -1; dx <= 1; dx += 1) {
                    for (int dz = -1; dz <= 1; dz += 1) {
                        for (int dy = -1; dy <= 1; dy += 1) {
                            Block dirtBlock = grassBlock.getRelative(dx, dy, dz);
                            if (!dirts.contains(dirtBlock.getType())) continue;
                            if (!arena.grassBlocks.contains(Vec.v(dirtBlock))) continue;
                            Vec vec = Vec.v(dirtBlock);
                            if (!state.spreadOptions.contains(vec)) {
                                state.spreadOptions.add(vec);
                                for (Placed placed : state.placedSigns) {
                                    if (placed.x == vec.x && placed.z == vec.z) {
                                        state.signOption = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (growCooldown < 80 && growCooldown > 10 && !state.spreadOptions.isEmpty() && !state.signOption) {
                growCooldown = 10;
            }
            if (growCooldown < 60 && !state.spreadOptions.isEmpty()) {
                for (Vec vec : state.spreadOptions) {
                    Block b = vec.toBlock(world);
                    world.spawnParticle(Particle.BLOCK_DUST, b.getLocation().add(0.5, 1.5, 0.5),
                                        1, 0.125, 0.125, 0.125, 0,
                                        Material.GRASS.createBlockData());
                }
            }
        }
    }

    protected Snowman spawnSnowman() {
        List<Vec> grassBlocks = List.copyOf(arena.grassBlocks);
        Vec goal = grassBlocks.get(random.nextInt(grassBlocks.size()));
        return spawnSnowman(goal.toBlock(getWorld()).getLocation().add(0.5, 1.0, 0.5));
    }

    protected Snowman spawnSnowman(Location location) {
        Snowman snowman = location.getWorld().spawn(location, Snowman.class, s -> {
                s.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25);
                s.setDerp(true);
                s.setPersistent(false);
            });
        if (snowman == null) {
            plugin.getLogger().severe("Failed spawning snowman at " + Vec.v(location));
            return null;
        }
        snowmen.add(snowman);
        state.snowmen.add(snowman.getUniqueId());
        plugin.getLogger().info("Spawned snowman at " + Vec.v(location));
        return snowman;
    }

    private void handleSnowman(Snowman snowman) {
        if (!snowman.isValid() || snowman.isDead()) {
            state.snowmen.remove(snowman.getUniqueId());
            snowmen.remove(snowman);
        }
        Location location = snowman.getLocation();
        Block block = location.getBlock().getRelative(0, -1, 0);
        Vec vec = Vec.v(block);
        if (!arena.grassBlocks.contains(vec)) {
            snowman.remove();
            return;
        }
        if (block.getType() != Material.SNOW_BLOCK) {
            block.setType(Material.SNOW_BLOCK);
            block.getRelative(0, 1, 0).setType(Material.SNOW);
        }
        if (purgeSign(block, text("Snowman"))) {
            snowman.getPathfinder().stopPathfinding();
            spawnSnowman(block.getLocation().add(0.5, 1.0, 0.5));
            for (Snowman other : snowmen) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 9, true, false));
            }
            checkForWinner();
        } else {
            var path = snowman.getPathfinder().getCurrentPath();
            if (path == null || path.getFinalPoint() == null || path.getFinalPoint().distanceSquared(location) < 0.5) {
                List<Vec> grassBlocks = List.copyOf(arena.grassBlocks);
                Vec goal = grassBlocks.get(random.nextInt(grassBlocks.size()));
                snowman.getPathfinder().moveTo(goal.toBlock(snowman.getWorld()).getLocation().add(0.5, 1.0, 0.5));
                plugin.getLogger().info("Moving snowman to " + goal);
            }
        }
    }

    /**
     * Purge a sign at location.
     * @param block the block _below_ the sign
     * @param destroyer name of the destructor
     * @return true if a sign was purged, false otherwise.
     */
    private boolean purgeSign(Block block, Component destroyer) {
        Placed placed = null;
        for (Iterator<Placed> iter = state.placedSigns.iterator(); iter.hasNext();) {
            Placed it = iter.next();
            if (it.x == block.getX() && it.z == block.getZ()) {
                placed = it;
                iter.remove();
                break;
            }
        }
        if (placed == null) return false;
        Player owner = Bukkit.getPlayer(placed.owner);
        Component ownerName = owner != null
            ? owner.displayName()
            : text(placed.ownerName);
        announceArena(join(noSeparators(), ownerName, text(" was destroyed by "), destroyer).color(GREEN));
        Block signBlock = block.getWorld().getBlockAt(placed.x, placed.y, placed.z);
        BlockState blockState = signBlock.getState();
        if (blockState instanceof Sign) {
            Sign sign = (Sign) blockState;
            for (Component line: sign.lines()) {
                if (line == null) continue;
                announceArena(text()
                              .append(VanillaItems.componentOf(Material.OAK_SIGN))
                              .append(space())
                              .append(line)
                              .build());
            }
        }
        World world = block.getWorld();
        world.playSound(block.getLocation(),
                        Sound.ENTITY_GENERIC_EXPLODE,
                        SoundCategory.MASTER,
                        1.0f, 2.0f);
        world.spawnParticle(Particle.EXPLOSION_LARGE,
                            block.getLocation().add(0.5, 1.5, 0.5),
                            8,
                            0.2, 0.2, 0.2,
                            0.0);
        Vec vec = Vec.v(placed.x, placed.y, placed.z);
        ArmorStand armorStand = armorStands.remove(vec);
        if (armorStand != null) armorStand.remove();
        if (explodeGrass && !state.snow) {
            final int explodeRadius = 3;
            final double explodeDistance = (double) explodeRadius + 0.5;
            for (int dz = -explodeRadius; dz <= explodeRadius; dz += 1) {
                for (int dx = -explodeRadius; dx <= explodeRadius; dx += 1) {
                    if (dx == 0 && dz == 0) continue;
                    double distance = Math.sqrt((double) (dx * dx + dz * dz));
                    if (distance > explodeDistance) continue;
                    Vec vec2 = Vec.v(block.getX() + dx, block.getY(), block.getZ() + dz);
                    if (!arena.grassBlocks.contains(vec2)) continue;
                    Block block2 = block.getRelative(dx, 0, dz);
                    if (block2.getType() == Material.GRASS_BLOCK) {
                        block2.setType(dirts.get(random.nextInt(dirts.size())));
                        Block block3 = block2.getRelative(0, 1, 0);
                        if (flowers.contains(block3.getType())) {
                            block3.setType(Material.AIR);
                            Block block4 = block3.getRelative(0, 1, 0);
                            if (flowers.contains(block4.getType())) {
                                block4.setType(Material.AIR);
                            }
                        }
                    }
                }
            }
        }
        if (isMainEventGame()) {
            final int size = state.placedSigns.size();
            if (size <= 3) {
                for (Placed survivor : state.placedSigns) {
                    plugin.global.addScore(survivor.owner, size == 1 ? 3 : 2);
                }
            } else {
                for (Placed survivor : state.placedSigns) {
                    plugin.global.addScore(survivor.owner, 1);
                }
            }
            plugin.saveGlobal();
            plugin.computeHighscore();
        }
        return true;
    }

    private void updateArmorStands(World w) {
        for (Placed placed : state.placedSigns) {
            Vec vec = Vec.v(placed.x, placed.y, placed.z);
            ArmorStand armorStand = armorStands.get(vec);
            Player owner = Bukkit.getPlayer(placed.owner);
            Component ownerName = owner != null
                ? owner.displayName()
                : text(placed.ownerName);
            if (armorStand == null || armorStand.isDead()) {
                armorStand = w.spawn(vec.toBlock(w).getLocation().add(0.5, 1.0, 0.5), ArmorStand.class, as -> {
                        as.setPersistent(false);
                        as.customName(ownerName);
                        as.setCustomNameVisible(true);
                        as.setInvisible(true);
                        as.setGravity(false);
                        as.setMarker(true);
                    });
            }
            armorStands.put(vec, armorStand);
        }
    }

    public GameState getGameState() {
        return state.gameState;
    }

    protected void setupGameState(GameState gameState) {
        switch (gameState) {
        case PAUSE: {
            break;
        }
        case PLACE: {
            state.placeStarted = System.currentTimeMillis();
            for (Vec vec: arena.grassBlocks) {
                Block block = getWorld().getBlockAt(vec.x, vec.y, vec.z);
                if (state.snow) {
                    block.setType(Material.GRASS_BLOCK);
                } else {
                    block.setType(dirts.get(random.nextInt(dirts.size())));
                }
                block.getRelative(0, 1, 0).setType(Material.AIR);
            }
            for (Player player : getPlayers()) {
                Material signMaterial = randomSign();
                if (isMainEventGame()) {
                    player.getInventory().addItem(new ItemStack(signMaterial));
                }
                player.showTitle(Title.title(VanillaItems.componentOf(signMaterial),
                                             text("Place your signs!", GREEN)));
                player.sendMessage(text("\nPlace your signs!\n ", GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.5f, 2.0f);
            }
            state.placedSigns.clear();
            break;
        }
        case GROW: {
            List<Vec> blocks = new ArrayList<>(arena.grassBlocks);
            Collections.shuffle(blocks);
            World world = getWorld();
            if (blocks.size() > 0) {
                int count = 0;
                for (int i = 0; i < 100; i += 1) {
                    Vec vec = blocks.get(i % blocks.size());
                    Block block = world.getBlockAt(vec.x, vec.y, vec.z);
                    boolean conflicts = false;
                    for (Placed placed: state.placedSigns) {
                        if (placed.x == vec.x && placed.z == vec.z) {
                            conflicts = true;
                            break;
                        }
                    }
                    if (!conflicts) {
                        if (state.snow) {
                            if (count == 0) {
                                Location loc = block.getLocation().add(0.5, 1.0, 0.5);
                                Snowman snowman = spawnSnowman(loc);
                            }
                        } else {
                            block.setType(Material.GRASS_BLOCK);
                        }
                        count += 1;
                    }
                    if (count >= 1) break;
                }
            }
            for (int y = arena.area.lo.y; y <= arena.area.hi.y; y += 1) {
                for (int z = arena.area.lo.z; z <= arena.area.hi.z; z += 1) {
                    for (int x = arena.area.lo.x; x <= arena.area.hi.x; x += 1) {
                        Block block = world.getBlockAt(x, y, z);
                        if (MaterialTags.FENCE_GATES.isTagged(block.getType())) {
                            Openable openable = (Openable) block.getBlockData();
                            if (openable.isOpen()) {
                                openable.setOpen(false);
                                block.setBlockData(openable, false);
                            }
                        }
                    }
                }
            }
            for (Player player : getPlayers()) {
                Vec vec = Vec.v(player.getLocation());
                boolean forbidden = arena.grassBlocks.contains(vec)
                    || arena.grassBlocks.contains(vec.add(0, -1, 0))
                    || arena.grassBlocks.contains(vec.add(0, -2, 0));
                if (forbidden) warpPlayerOutside(player, TeleportCause.PLUGIN);
            }
            if (isMainEventGame()) {
                for (Placed placed : state.placedSigns) {
                    plugin.global.addScore(placed.owner, 1);
                }
                plugin.saveGlobal();
                plugin.computeHighscore();
            }
            announceArena(ChatColor.GREEN + "Watch the grass spread!");
            break;
        }
        case END: {
            state.endStarted = System.currentTimeMillis();
            cleanUpSnowmen();
            break;
        }
        default: throw new IllegalStateException(gameState.name());
        }
        state.gameState = gameState;
        saveState();
    }


    protected void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (state.gameState != GameState.PLACE) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        if (!isSign(block.getType()) || !arena.grassBlocks.contains(Vec.v(block.getRelative(0, -1, 0)))) {
            event.setCancelled(true);
            return;
        }
        World world = getWorld();
        for (Iterator<Placed> iter = state.placedSigns.iterator(); iter.hasNext();) {
            Placed placed = iter.next();
            if (placed.owner.equals(player.getUniqueId())) {
                world.getBlockAt(placed.x, placed.y, placed.z).setType(Material.AIR);
                iter.remove();
            }
        }
        Placed placed = new Placed(player.getUniqueId(), player.getName(), block.getX(), block.getY(), block.getZ());
        state.placedSigns.add(placed);
        saveState();
        event.setCancelled(false);
        player.sendMessage(ChatColor.GREEN + "Sign placed");
        if (isMainEventGame()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
        }
    }

    protected void onBlockSpread(BlockSpreadEvent event) {
        if (state.snow) return;
        Block block = event.getBlock();
        if (event.getSource().getType() != Material.GRASS_BLOCK || !dirts.contains(event.getBlock().getType())) return;
        if (!arena.grassBlocks.contains(Vec.v(block))) return;
        if (state.gameState != GameState.GROW || growCooldown > 0) {
            event.setCancelled(true);
            return;
        }
        spreadTo(block);
    }

    protected void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.hasBlock())) return;
        Block block = event.getClickedBlock();
        Vec vector = Vec.v(block);
        if (vector.equals(arena.startButton)) {
            if (state.gameState != GameState.PAUSE) {
                player.sendActionBar(text("Game already running!", RED));
                return;
            }
            if (plugin.global.event) {
                player.sendActionBar(text("Cannot start during events!", RED));
                return;
            }
            setupGameState(GameState.PLACE);
            player.sendActionBar(text("Starting Game", GREEN));
            return;
        }
        if (player.isOp()) return;
        if (state.gameState == GameState.GROW) {
            Material mat = block.getType();
            if (Tag.DOORS.isTagged(mat) || MaterialTags.FENCE_GATES.isTagged(mat)) {
                event.setCancelled(true);
            }
        } else if (state.gameState == GameState.PLACE) {
            if (arena.grassBlocks.contains(vector)) {
                event.setCancelled(false);
            } else {
                Placed placed = findPlacedSign(block);
                if (placed != null && player.getUniqueId().equals(placed.owner)) {
                    event.setCancelled(false);
                }
            }
        }
    }

    protected void onPlayerHud(PlayerHudEvent event) {
        List<Component> ls = new ArrayList<>();
        Player player = event.getPlayer();
        if (state.gameState == GameState.GROW) {
            if (plugin.global.debug) {
                ls.add(text("DEBUG MODE", RED));
            }
            int left = state.placedSigns.size();
            ls.add(text("Signs Left ", GREEN)
                   .append(text("" + left, WHITE)));
            String all = state.placedSigns.stream()
                .map(s -> s.ownerName)
                .sorted()
                .collect(Collectors.joining(" "));
            List<String> alls = Text.wrap(all, 24);
            if (alls.size() > 3) {
                alls = alls.subList(0, 3);
            }
            for (String l : alls) {
                ls.add(text(l, GRAY));
            }
        } else if (state.gameState == GameState.PLACE) {
            if (findPlacedSign(player) == null) {
                ls.add(text("Place your signs!", GREEN));
            } else {
                ls.add(text(Unicode.CHECKMARK.string + " Sign placed", GREEN));
            }
            long seconds = state.placedSigns.isEmpty()
                ? placeTime.toSeconds()
                : Math.max(0, (placeTime.toMillis() - (System.currentTimeMillis() - state.placeStarted) - 1L) / 1000L + 1L);
            ls.add(text("Time Left ", GRAY)
                   .append(text(seconds + "s", WHITE)));
        }
        if (isMainEventGame()) {
            ls.add(join(noSeparators(), text("Your Score ", GRAY), text(plugin.global.getScore(player.getUniqueId()), GREEN)));
            ls.addAll(plugin.highscoreLines);
        }
        event.sidebar(PlayerHudPriority.HIGHEST, ls);
    }

    /**
     * Uncancel build permissions for your own sign.
     */
    protected void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        switch (query.getAction()) {
        case BUILD: break;
        default: return;
        }
        if (!query.isCancelled()) return;
        if (state.gameState != GameState.PLACE) return;
        Block block = query.getBlock();
        Placed placed = findPlacedSign(block);
        if (placed == null) return;
        Player player = query.getPlayer();
        if (!placed.isOwner(player) && !player.isOp()) return;
        query.setCancelled(false);
    }

    /**
     * Uncancel sign change event.
     */
    protected void onSignChange(SignChangeEvent event) {
        if (!event.isCancelled()) return;
        if (state.gameState != GameState.PLACE) return;
        Block block = event.getBlock();
        Placed placed = findPlacedSign(block);
        if (placed == null) return;
        Player player = event.getPlayer();
        if (!placed.isOwner(player) && !player.isOp()) return;
        event.setCancelled(false);
    }

    /**
     * Remove sign broken by owner.
     * Uncancel build permissions.
     */
    protected void onBlockBreak(BlockBreakEvent event) {
        if (state.gameState != GameState.PLACE) return;
        Block block = event.getBlock();
        Placed placed = findPlacedSign(block);
        if (placed == null) return;
        Player player = event.getPlayer();
        if (!placed.isOwner(player) && !player.isOp()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        state.placedSigns.remove(placed);
    }

    /**
     * Remove sign broken by owner.
     * Uncancel build permissions.
     */
    protected void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        if (state.gameState != GameState.PLACE) return;
        Block block = event.getBlock();
        Placed placed = findPlacedSign(block);
        if (placed == null) return;
        Player player = event.getPlayer();
        if (!placed.isOwner(player) && !player.isOp()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        state.placedSigns.remove(placed);
    }

    protected void onPlayerMove(PlayerMoveEvent event) {
        if (state.gameState != GameState.GROW) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        Vec vec = Vec.v(event.getTo());
        boolean forbidden = arena.grassBlocks.contains(vec)
            || arena.grassBlocks.contains(vec.add(0, -1, 0))
            || arena.grassBlocks.contains(vec.add(0, -2, 0));
        if (forbidden) event.setCancelled(true);
    }

    protected void onPlayerTeleport(PlayerTeleportEvent event) {
        if (state.gameState != GameState.GROW) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        if (event.getCause() == TeleportCause.PLUGIN || event.getCause() == TeleportCause.COMMAND) return;
        Vec vec = Vec.v(event.getTo());
        boolean forbidden = arena.grassBlocks.contains(vec)
            || arena.grassBlocks.contains(vec.add(0, -1, 0))
            || arena.grassBlocks.contains(vec.add(0, -2, 0));
        if (forbidden) event.setCancelled(true);
    }
}

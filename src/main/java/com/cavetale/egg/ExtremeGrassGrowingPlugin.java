package com.cavetale.egg;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.destroystokyo.paper.MaterialTags;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ExtremeGrassGrowingPlugin extends JavaPlugin implements Listener, Runnable {
    private Arena arena = new Arena();
    private State state = new State();
    private static final String META_ARENA = "egg.arena";
    protected Random random = ThreadLocalRandom.current();
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
    protected int growCooldown = 50;
    protected boolean explodeGrass = true;
    private List<Snowman> snowmen = new ArrayList<>();
    private Map<Vec, ArmorStand> armorStands = new HashMap<>();

    // --- Plugin Overrides

    @Override
    public void onEnable() {
        loadState();
        loadArena();
        getCommand("egga").setExecutor((s, c, l, a) -> onAdminCommand(s, a));
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this, 1L, 1L);
    }

    @Override
    public void onDisable() {
        saveState();
        cleanUp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        warpPlayerOutside(player);
        return true;
    }

    void cleanUp() {
        for (ArmorStand as : armorStands.values()) {
            as.remove();
        }
        armorStands.clear();
    }

    void updateArmorStands(World w) {
        for (Placed placed : state.placedSigns) {
            Vec vec = Vec.v(placed.x, placed.y, placed.z);
            ArmorStand armorStand = armorStands.get(vec);
            if (armorStand == null || armorStand.isDead()) {
                armorStand = w.spawn(vec.toBlock(w).getLocation().add(0.5, 1.0, 0.5), ArmorStand.class, as -> {
                        as.setPersistent(false);
                        as.setCustomName(ChatColor.GRAY + placed.ownerName);
                        as.setCustomNameVisible(true);
                        as.setInvisible(true);
                        as.setGravity(false);
                        as.setMarker(true);
                    });
            }
            armorStands.put(vec, armorStand);
        }
    }

    void warpPlayerOutside(Player player) {
        List<Vec> blocks = new ArrayList<>(arena.viewerBlocks);
        if (blocks.size() > 0) {
            Vec block = blocks.get(random.nextInt(blocks.size()));
            Location loc = new Location(getServer().getWorld(arena.world),
                                        (double) block.x + 0.5,
                                        (double) block.y + 1.0,
                                        (double) block.z + 0.5,
                                        player.getLocation().getYaw(),
                                        player.getLocation().getPitch());
            player.teleport(loc);
        }
    }

    private boolean onAdminCommand(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) {
            sender.sendMessage("State: " + state.gameState);
            sender.sendMessage("Placed signs: " + state.placedSigns.size());
            sender.sendMessage("Winners: " + state.winners);
            return false;
        }
        switch (args[0]) {
        case "viewer": {
            if (player == null) {
                sender.sendMessage("Player expected.");
                return true;
            }
            return viewerCommand(player, args);
        }
        case "grass": {
            if (player == null) {
                sender.sendMessage("Player expected.");
                return true;
            }
            boolean remove = false;
            if (args.length >= 2) {
                switch (args[1]) {
                case "remove": remove = true; break;
                case "clear":
                    arena.grassBlocks.clear();
                    saveArena();
                    sender.sendMessage("Grass blocks cleared.");
                    return true;
                default: return false;
                }
            }
            Cuboid sel = WorldEdit.getSelection(player);
            if (sel != null) {
                World world = player.getWorld();
                Set<Vec> blocks = new HashSet<>();
                for (int y = sel.lo.y; y <= sel.hi.y; y += 1) {
                    for (int z = sel.lo.z; z <= sel.hi.z; z += 1) {
                        for (int x = sel.lo.x; x <= sel.hi.x; x += 1) {
                            Block block = world.getBlockAt(x, y, z);
                            switch (block.getType()) {
                            case GRASS_BLOCK: case DIRT:
                                if (remove || block.getRelative(0, 1, 0).isEmpty()) {
                                    blocks.add(Vec.v(x, y, z));
                                }
                            default: break;
                            }
                        }
                    }
                }
                if (remove) {
                    arena.grassBlocks.removeAll(blocks);
                } else {
                    arena.grassBlocks.addAll(blocks);
                }
                sender.sendMessage("" + blocks.size() + " grass blocks.");
                saveArena();
            } else {
                sender.sendMessage("No selection made.");
            }
            return true;
        }
        case "area": {
            if (player == null) {
                sender.sendMessage("Player expected.");
                return true;
            }
            Cuboid selection = WorldEdit.getSelection(player);
            if (selection != null) {
                arena.area = selection;
                arena.world = player.getWorld().getName();
                sender.sendMessage("Arena area set: " + selection);
                saveArena();
            } else {
                sender.sendMessage("No selection made.");
            }
            return true;
        }
        case "state": {
            if (args.length == 2) {
                GameState newState;
                try {
                    newState = GameState.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException iae) {
                    sender.sendMessage("Illegal state: " + args[1]);
                    return true;
                }
                setupGameState(newState);
                cleanUp();
                sender.sendMessage("Switched to state " + newState);
            }
            return true;
        }
        case "clearwinners": {
            state.winners.clear();
            saveState();
            sender.sendMessage("Winners cleared");
            return true;
        }
        case "snow": {
            state.snow = !state.snow;
            saveState();
            sender.sendMessage("Snow is now " + (state.snow ? "on" : "off") + ".");
            return true;
        }
        case "list": {
            sender.sendMessage(state.placedSigns.size() + " placed signs:");
            for (Placed placed: state.placedSigns) {
                sender.sendMessage(placed.getOwnerName()
                                   + " " + placed.getX()
                                   + " " + placed.getY()
                                   + " " + placed.getZ());
            }
            sender.sendMessage("");
            return true;
        }
        case "hi": {
            if (player == null) return false;
            for (Vec v: arena.viewerBlocks) {
                Block block = player.getWorld().getBlockAt(v.x, v.y, v.z);
                player.sendBlockChange(block.getLocation(),
                                       Material.GLOWSTONE.createBlockData());
            }
            for (Vec v: arena.grassBlocks) {
                Block block = player.getWorld().getBlockAt(v.x, v.y, v.z);
                player.sendBlockChange(block.getLocation(),
                                       Material.REDSTONE_BLOCK.createBlockData());
            }
            sender.sendMessage("Highlighting blocks");
            return true;
        }
        case "info": {
            Gson gson = new Gson();
            sender.sendMessage(gson.toJson(state));
        }
        case "debug": {
            state.debug = !state.debug;
            sender.sendMessage("debug mode: " + state.debug);
            return true;
        }
        case "snowman": {
            spawnSnowman(player.getLocation());
            player.sendMessage("snowman spawned");
            return true;
        }
        case "event": {
            if (args.length == 1) {
                sender.sendMessage(Component.text("Event mode: " + state.event, NamedTextColor.YELLOW));
                return true;
            }
            if (args.length != 2) return false;
            try {
                state.event = Boolean.parseBoolean(args[1]);
            } catch (IllegalArgumentException iae) {
                sender.sendMessage(Component.text("Boolean expected: " + args[1], NamedTextColor.RED));
                return true;
            }
            saveState();
            sender.sendMessage(Component.text("Event mode set to " + state.event, NamedTextColor.YELLOW));
            return true;
        }
        default: return false;
        }
    }

    boolean viewerCommand(Player player, String[] args) {
        boolean remove = false;
        if (args.length >= 2) {
            switch (args[1]) {
            case "remove": remove = true; break;
            case "clear":
                arena.viewerBlocks.clear();
                saveArena();
                player.sendMessage("Viewer blocks cleared.");
                return true;
            default: return false;
            }
        }
        Cuboid sel = WorldEdit.getSelection(player);
        if (sel != null) {
            World world = player.getWorld();
            Set<Vec> blocks = new HashSet<>();
            for (int y = sel.lo.y; y <= sel.hi.y; y += 1) {
                for (int z = sel.lo.z; z <= sel.hi.z; z += 1) {
                    for (int x = sel.lo.x; x <= sel.hi.x; x += 1) {
                        Block block = world.getBlockAt(x, y, z);
                        if (remove || (block.getType().isSolid()
                                       && block.getRelative(0, 1, 0).isEmpty()
                                       && block.getRelative(0, 2, 0).isEmpty())) {
                            blocks.add(Vec.v(x, y, z));
                        }
                    }
                }
            }
            if (remove) {
                arena.viewerBlocks.removeAll(blocks);
            } else {
                arena.viewerBlocks.addAll(blocks);
            }
            player.sendMessage("" + blocks.size() + " viewer blocks.");
            saveArena();
        } else {
            player.sendMessage("No selection made.");
        }
        return true;
    }

    @Override
    public void run() {
        if (state.gameState != GameState.GROW) return;
        World world = Bukkit.getWorld(arena.world);
        updateArmorStands(world);
        if (world == null) return;
        if (state.snow) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof Snowman)) continue;
                Snowman snowman = (Snowman) e;
                if (!snowman.isValid()) {
                    snowmen.remove(snowman);
                    continue;
                }
                handleSnowman(snowman);
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
                        Block dirtBlock = grassBlock.getRelative(dx, 0, dz);
                        if (dirtBlock.getType() != Material.DIRT) continue;
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

    Snowman spawnSnowman(Location location) {
        Snowman snowman = location.getWorld().spawn(location, Snowman.class, s -> {
                s.setDerp(true);
            });
        snowmen.add(snowman);
        snowman.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.6);
        return snowman;
    }

    void handleSnowman(Snowman snowman) {
        Block block = snowman.getLocation().getBlock().getRelative(0, -1, 0);
        Vec vec = Vec.v(block);
        if (!arena.grassBlocks.contains(vec)) return;
        if (block.getType() == Material.SNOW_BLOCK) return;
        if (!snowmen.contains(snowman)) snowmen.add(snowman);
        block.setType(Material.SNOW_BLOCK);
        if (purgeSign(block, "snowman")) {
            spawnSnowman(block.getLocation().add(0.5, 1.0, 0.5));
            for (Snowman other : snowmen) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 9, true, false));
            }
            checkForWinner();
        }
        block.getRelative(0, 1, 0).setType(Material.SNOW);
    }

    /**
     * Purge a sign at location.
     * @param block the block _below_ the sign
     * @param destroyer name of the destructor
     * @return true if a sign was purged, false otherwise.
     */
    boolean purgeSign(Block block, String destroyer) {
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
        announceArena(ChatColor.GREEN + placed.ownerName
                      + " was destroyed by " + destroyer + ":");
        Block signBlock = block.getWorld().getBlockAt(placed.x, placed.y, placed.z);
        BlockState blockState = signBlock.getState();
        if (blockState instanceof Sign) {
            Sign sign = (Sign) blockState;
            for (Component line: sign.lines()) {
                if (line == null) continue;
                announceArena(Component.text()
                              .append(VanillaItems.componentOf(Material.OAK_SIGN))
                              .append(Component.space())
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
                        block2.setType(Material.DIRT);
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
        return true;
    }

    void setupGameState(GameState gameState) {
        switch (gameState) {
        case PAUSE: {
            if (state.snow) {
                World world = Bukkit.getWorld(arena.world);
                if (world != null) {
                    for (Snowman snowman : snowmen) {
                        snowman.remove();
                    }
                    snowmen.clear();
                }
            }
            break;
        }
        case PLACE: {
            for (Vec vec: arena.grassBlocks) {
                Block block = getServer().getWorld(arena.world).getBlockAt(vec.x, vec.y, vec.z);
                if (state.snow) {
                    block.setType(Material.GRASS_BLOCK);
                } else {
                    block.setType(Material.DIRT);
                }
                block.getRelative(0, 1, 0).setType(Material.AIR);
            }
            for (Player player: getServer().getWorld(arena.world).getPlayers()) {
                if (isInArena(player)) {
                    player.getInventory().addItem(new ItemStack(randomSign()));
                }
            }
            state.placedSigns.clear();
            announceArena(ChatColor.GREEN + "Place your signs!");
            break;
        }
        case GROW: {
            for (Player player: getServer().getWorld(arena.world).getPlayers()) {
                if (isInArena(player)) {
                    if (arena.grassBlocks.contains(Vec.v(player.getLocation().getBlock().getRelative(0, -1, 0)))) {
                        warpPlayerOutside(player);
                    }
                }
            }
            List<Vec> blocks = new ArrayList<>(arena.grassBlocks);
            Collections.shuffle(blocks);
            World world = getServer().getWorld(arena.world);
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
            announceArena(ChatColor.GREEN + "Watch the grass spread!");
            break;
        }
        default: break;
        }
        state.gameState = gameState;
        saveState();
    }

    static class Arena {
        String world = "world";
        Cuboid area = Cuboid.ZERO;
        Set<Vec> grassBlocks = new HashSet<>();
        Set<Vec> viewerBlocks = new HashSet<>();
    }

    @Value
    static class Placed {
        private final UUID owner;
        private final String ownerName;
        private final int x;
        private final int y;
        private final int z;

        public boolean isOwner(Player player) {
            return player.getUniqueId().equals(owner);
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

    enum GameState {
        PAUSE,
        PLACE,
        GROW;
    }

    static class State {
        GameState gameState = GameState.PAUSE;
        List<Placed> placedSigns = new ArrayList<>();
        List<String> winners = new ArrayList<>();
        boolean snow = false;
        boolean debug = false;
        List<Vec> spreadOptions = new ArrayList<>();
        boolean signOption = false;
        boolean event = false;
    }

    void loadArena() {
        Gson gson = new Gson();
        File file = new File(getDataFolder(), "arena.json");
        try (FileReader reader = new FileReader(file)) {
            arena = gson.fromJson(reader, Arena.class);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        if (arena == null) arena = new Arena();
    }

    void loadState() {
        Gson gson = new Gson();
        File file = new File(getDataFolder(), "state.json");
        try (FileReader reader = new FileReader(file)) {
            state = gson.fromJson(reader, State.class);
        } catch (FileNotFoundException fnfe) {
            state = new State();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void saveArena() {
        Gson gson = new Gson();
        getDataFolder().mkdirs();
        try {
            FileWriter fw = new FileWriter(new File(getDataFolder(), "arena.json"));
            gson.toJson(arena, fw);
            fw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void saveState() {
        Gson gson = new Gson();
        getDataFolder().mkdirs();
        try {
            FileWriter fw = new FileWriter(new File(getDataFolder(), "state.json"));
            gson.toJson(state, fw);
            fw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    boolean isInArena(Entity entity) {
        Location loc = entity.getLocation();
        return arena.area.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    boolean isInArena(Block block) {
        return arena.area.contains(block.getX(), block.getY(), block.getZ());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!player.getWorld().getName().equals(arena.world)) return;
        if (isInArena(player)) {
            if (!player.hasMetadata(META_ARENA)) {
                player.setMetadata(META_ARENA,
                                   new FixedMetadataValue(this, true));
            }
            switch (state.gameState) {
            case PAUSE: break;
            case PLACE: break;
            case GROW: {
                Block block = player.getLocation().getBlock().getRelative(0, -1, 0);
                if (arena.grassBlocks.contains(Vec.v(block))) {
                    warpPlayerOutside(player);
                }
                break;
            }
            default: break;
            }
        } else {
            if (player.hasMetadata(META_ARENA)) {
                player.removeMetadata(META_ARENA, this);
            }
        }
    }

    Material randomSign() {
        return signs.get(random.nextInt(signs.size()));
    }

    boolean isSign(Material mat) {
        return signs.contains(mat);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(arena.world)) return;
        if (!isInArena(block)) return;
        if (state.gameState == GameState.PLACE
            && isSign(block.getType())
            && arena.grassBlocks.contains(Vec.v(block.getRelative(0, -1, 0)))) {
            World world = getServer().getWorld(arena.world);
            for (Iterator<Placed> iter = state.placedSigns.iterator(); iter.hasNext();) {
                Placed placed = iter.next();
                if (placed.owner.equals(player.getUniqueId())) {
                    world.getBlockAt(placed.x, placed.y, placed.z).setType(Material.AIR);
                    iter.remove();
                }
            }
            Placed placed = new Placed(player.getUniqueId(),
                                       player.getName(),
                                       block.getX(),
                                       block.getY(),
                                       block.getZ());
            state.placedSigns.add(placed);
            saveState();
            event.setCancelled(false);
            player.sendMessage(ChatColor.GREEN + "Sign placed.");
            if (state.event) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            }
        } else {
            if (!player.isOp()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        if (state.snow) return;
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(arena.world)) return;
        if (event.getSource().getType() != Material.GRASS_BLOCK
            || event.getBlock().getType() != Material.DIRT) return;
        if (!arena.grassBlocks.contains(Vec.v(block))) return;
        if (state.gameState != GameState.GROW || growCooldown > 0) {
            event.setCancelled(true);
            return;
        }
        spreadTo(block);
    }

    void spreadTo(Block block) {
        Material flower = flowers.get(random.nextInt(flowers.size()));
        boolean removed = purgeSign(block, flower.name().toLowerCase().replace("_", " "));
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

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.hasBlock())) return;
        Block block = event.getClickedBlock();
        if (!block.getWorld().getName().equals(arena.world)) return;
        if (!isInArena(block)) return;
        if (state.gameState == GameState.GROW) {
            Material mat = block.getType();
            if (Tag.DOORS.isTagged(mat) || MaterialTags.FENCE_GATES.isTagged(mat)) {
                event.setCancelled(true);
            }
        } else if (state.gameState == GameState.PLACE) {
            if (arena.grassBlocks.contains(Vec.v(block))) {
                event.setCancelled(false);
            } else {
                Placed placed = findPlacedSign(block);
                if (placed != null && player.getUniqueId().equals(placed.owner)) {
                    event.setCancelled(false);
                }
            }
        }
    }

    void checkForWinner() {
        if (state.debug) return;
        if (state.placedSigns.size() == 1) {
            cleanUp();
            Placed winner = state.placedSigns.get(0);
            announceArena(ChatColor.GREEN + winner.ownerName + " wins the game!");
            state.winners.add(winner.ownerName);
            setupGameState(GameState.PAUSE);
            if (state.event) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winner.ownerName + " GrassGrower EGGspert Bee BuzzBee Bumblebee");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mytems give " + winner.ownerName + " kitty_coin");
            }
        }
    }

    void announceArena(String msg) {
        for (Player player: getServer().getWorld(arena.world).getPlayers()) {
            if (isInArena(player)) {
                player.sendMessage(msg);
            }
        }
    }

    void announceArena(Component msg) {
        for (Player player: getServer().getWorld(arena.world).getPlayers()) {
            if (isInArena(player)) {
                player.sendMessage(msg);
            }
        }
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        if (state.gameState != GameState.GROW) return;
        Player player = event.getPlayer();
        if (!isInArena(player)) return;
        List<Component> ls = new ArrayList<>();
        int left = state.placedSigns.size();
        ls.add(Component.text("Signs Left ", NamedTextColor.GREEN)
               .append(Component.text("" + left, NamedTextColor.WHITE)));
        String all = state.placedSigns.stream()
            .map(s -> s.ownerName)
            .sorted()
            .collect(Collectors.joining(" "));
        List<String> alls = Text.wrap(all, 30);
        if (alls.size() > 3) {
            alls = alls.subList(0, 3);
        }
        for (String l : alls) {
            ls.add(Component.text(l));
        }
        event.add(this, Priority.HIGH, ls);
    }

    /**
     * Uncancel build permissions for your own sign.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        switch (query.getAction()) {
        case BUILD: break;
        default: return;
        }
        if (!query.isCancelled()) return;
        if (state.gameState != GameState.PLACE) return;
        Block block = query.getBlock();
        if (!isInArena(block)) return;
        Placed placed = findPlacedSign(block);
        if (placed == null) return;
        Player player = query.getPlayer();
        if (!placed.isOwner(player) && !player.isOp()) return;
        query.setCancelled(false);
    }

    /**
     * Uncancel sign change event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onSignChange(SignChangeEvent event) {
        if (!event.isCancelled()) return;
        if (state.gameState != GameState.PLACE) return;
        Block block = event.getBlock();
        if (!isInArena(block)) return;
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
    @EventHandler(priority = EventPriority.HIGHEST)
    void onBlockBreak(BlockBreakEvent event) {
        if (state.gameState != GameState.PLACE) return;
        Block block = event.getBlock();
        if (!isInArena(block)) return;
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
    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        if (state.gameState != GameState.PLACE) return;
        Block block = event.getBlock();
        if (!isInArena(block)) return;
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
}

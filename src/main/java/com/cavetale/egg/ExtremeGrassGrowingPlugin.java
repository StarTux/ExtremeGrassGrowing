package com.cavetale.egg;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ExtremeGrassGrowingPlugin extends JavaPlugin implements Listener, Runnable {
    private Arena arena = new Arena();
    private State state = new State();
    private static final String META_ARENA = "egg.arena";

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
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player)sender;
        warpPlayerOutside(player);
        return true;
    }

    void warpPlayerOutside(Player player) {
        List<Vec> blocks = new ArrayList<>(arena.viewerBlocks);
        if (blocks.size() > 0) {
            Vec block = blocks.get(ThreadLocalRandom.current().nextInt(blocks.size()));
            player.teleport(new Location(getServer().getWorld(arena.world), (double)block.x + 0.5, (double)block.y + 1.0, (double)block.z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch()));
        }
    }

    private boolean onAdminCommand(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
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
            boolean remove = false;
            if (args.length >= 2) {
                switch (args[1]) {
                case "remove": remove = true; break;
                case "clear":
                    arena.viewerBlocks.clear();
                    saveArena();
                    sender.sendMessage("Viewer blocks cleared.");
                    return true;
                default: return false;
                }
            }
            Cuboid sel = getSelection(player);
            if (sel != null) {
                World world = player.getWorld();
                Set<Vec> blocks = new HashSet<>();
                for (int y = sel.lo.y; y <= sel.hi.y; y += 1) {
                    for (int z = sel.lo.z; z <= sel.hi.z; z += 1) {
                        for (int x = sel.lo.x; x <= sel.hi.x; x += 1) {
                            Block block = world.getBlockAt(x, y, z);
                            if (block.getType().isSolid()
                                && block.getRelative(0, 1, 0).isEmpty()
                                && block.getRelative(0, 2, 0).isEmpty()) {
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
                sender.sendMessage("" + blocks.size() + " viewer blocks.");
                saveArena();
            } else {
                sender.sendMessage("No selection made.");
            }
            return true;
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
            Cuboid sel = getSelection(player);
            if (sel != null) {
                World world = player.getWorld();
                Set<Vec> blocks = new HashSet<>();
                for (int y = sel.lo.y; y <= sel.hi.y; y += 1) {
                    for (int z = sel.lo.z; z <= sel.hi.z; z += 1) {
                        for (int x = sel.lo.x; x <= sel.hi.x; x += 1) {
                            Block block = world.getBlockAt(x, y, z);
                            switch (block.getType()) {
                            case GRASS_BLOCK: case DIRT:
                                if (block.getRelative(0, 1, 0).isEmpty()) {
                                    blocks.add(Vec.v(x, y, z));
                                }
                            default: break;
                            }
                        }
                    }
                }
                arena.grassBlocks.addAll(blocks);
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
            Cuboid selection = getSelection(player);
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
                sender.sendMessage("Switched to state " + newState);
            }
            return true;
        }
        case "clearwinners": {
            this.state.winners.clear();
            saveState();
            sender.sendMessage("Winners cleared");
            return true;
        }
        case "snow": {
            this.state.snow = !this.state.snow;
            saveState();
            sender.sendMessage("Snow is now " + (this.state.snow ? "on" : "off") + ".");
            return true;
        }
        case "list": {
            sender.sendMessage(this.state.placedSigns.size() + " placed signs:");
            for (Placed placed: this.state.placedSigns) {
                sender.sendMessage(placed.getOwnerName()
                                   + " " + placed.getX() + " " + placed.getY() + " " + placed.getZ());
            }
            sender.sendMessage("");
            return true;
        }
        case "hi": {
            if (player == null) return false;
            for (Vec v: arena.viewerBlocks) {
                Block block = player.getWorld().getBlockAt(v.x, v.y, v.z);
                player.sendBlockChange(block.getLocation(), Material.GLOWSTONE.createBlockData());
            }
            for (Vec v: arena.grassBlocks) {
                Block block = player.getWorld().getBlockAt(v.x, v.y, v.z);
                player.sendBlockChange(block.getLocation(), Material.REDSTONE_BLOCK.createBlockData());
            }
            sender.sendMessage("Highlighting blocks");
            return true;
        }
        default: return false;
        }
    }

    @Override
    public void run() {
        if (!this.state.snow) return;
        if (this.state.gameState != GameState.GROW) return;
        World world = Bukkit.getWorld(this.arena.world);
        if (world == null) return;
        for (Entity e: world.getEntities()) {
            if (!(e instanceof Snowman)) continue;
            handleSnowman((Snowman)e);
        }
    }

    void handleSnowman(Snowman snowman) {
        Block block = snowman.getLocation().getBlock().getRelative(0, -1, 0);
        Vec vec = Vec.v(block);
        if (!this.arena.grassBlocks.contains(vec)) return;
        if (block.getType() == Material.SNOW_BLOCK) return;
        block.setType(Material.SNOW_BLOCK);
        if (purgeSign(block)) {
            Snowman snowman2 = block.getWorld().spawn(block.getLocation().add(0.5, 1.0, 0.5), Snowman.class);
            snowman2.setDerp(true);
            snowman2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9999, 5, true, false));
            checkForWinner();
        }
        block.getRelative(0, 1, 0).setType(Material.SNOW);
    }

    boolean purgeSign(Block block) {
        for (Iterator<Placed> iter = this.state.placedSigns.iterator(); iter.hasNext();) {
            Placed placed = iter.next();
            if (placed.x == block.getX() && placed.z == block.getZ()) {
                announceArena(ChatColor.GREEN + placed.ownerName + "'s sign was destroyed. Its message to the world:");
                iter.remove();
                Block signBlock = block.getWorld().getBlockAt(placed.x, placed.y, placed.z);
                BlockState blockState = signBlock.getState();
                if (blockState instanceof Sign) {
                    Sign sign = (Sign)blockState;
                    for (String line: sign.getLines()) {
                        if (line != null) {
                            announceArena(ChatColor.GREEN + "> " + ChatColor.WHITE + line);
                        }
                    }
                }
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.0f, 2.0f);
                block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, block.getLocation().add(0.5, 1.5, 0.5), 8, 0.2, 0.2, 0.2, 0.0);
                return true;
            }
        }
        return false;
    }

    void setupGameState(GameState gameState) {
        switch (gameState) {
        case PAUSE: {
            if (this.state.snow) {
                World world = Bukkit.getWorld(this.arena.world);
                if (world != null) {
                    for (Entity e: world.getEntities()) {
                        if (!(e instanceof Snowman)) continue;
                        if (isInArena(e)) e.remove();
                    }
                }
            }
        }
        case PLACE: {
            for (Vec vec: arena.grassBlocks) {
                Block block = getServer().getWorld(arena.world).getBlockAt(vec.x, vec.y, vec.z);
                if (this.state.snow) {
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
                    warpPlayerOutside(player);
                }
            }
            List<Vec> blocks = new ArrayList<>(arena.grassBlocks);
            if (blocks.size() > 0) {
                int count = 0;
                for (int i = 0; i < 10; i += 1) {
                    Vec vec = blocks.get(ThreadLocalRandom.current().nextInt(blocks.size()));
                    Block block = getServer().getWorld(arena.world).getBlockAt(vec.x, vec.y, vec.z);
                    boolean conflicts = false;
                    for (Placed placed: state.placedSigns) {
                        if (placed.x == vec.x && placed.z == vec.z) {
                            conflicts = true;
                            break;
                        }
                    }
                    if (!conflicts) {
                        if (this.state.snow) {
                            if (count == 0) {
                                Snowman snowman = block.getWorld().spawn(block.getLocation().add(0.5, 1.0, 0.5), Snowman.class);
                                snowman.setDerp(true);
                                snowman.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9999, 5, true, false));
                            }
                        } else {
                            block.setType(Material.GRASS_BLOCK);
                        }
                        count += 1;
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

    // --- JSON Structures

    @Value
    static class Vec {
        static final Vec ZERO = new Vec(0, 0, 0);
        private final int x, y, z;

        static Vec v(int x, int y, int z) {
            return new Vec(x, y, z);
        }
        static Vec v(Block block) {
            return new Vec(block.getX(), block.getY(), block.getZ());
        }
    }

    @Value
    static class Cuboid {
        static final Cuboid ZERO = new Cuboid(Vec.ZERO, Vec.ZERO);
        private final Vec lo, hi;
        boolean contains(int x, int y, int z) {
            return x >= lo.x && x <= hi.x
                && y >= lo.y && y <= hi.y
                && z >= lo.z && z <= hi.z;
        }
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
        private final int x, y, z;
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
    }

    void loadArena() {
        Gson gson = new Gson();
        try {
            arena = gson.fromJson(new FileReader(new File(getDataFolder(), "arena.json")), Arena.class);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    void loadState() {
        Gson gson = new Gson();
        try {
            state = gson.fromJson(new FileReader(new File(getDataFolder(), "state.json")), State.class);
        } catch (FileNotFoundException fnfe) {
            state = new State();
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

    // --- Selection Utility

    private Cuboid getSelection(Player player) {
        int ax, ay, az, bx, by, bz;
        try {
            ax = player.getMetadata("SelectionAX").get(0).asInt();
            ay = player.getMetadata("SelectionAY").get(0).asInt();
            az = player.getMetadata("SelectionAZ").get(0).asInt();
            bx = player.getMetadata("SelectionBX").get(0).asInt();
            by = player.getMetadata("SelectionBY").get(0).asInt();
            bz = player.getMetadata("SelectionBZ").get(0).asInt();
        } catch (Exception e) {
            return null;
        }
        return new Cuboid(Vec.v(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz)),
                          Vec.v(Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)));
    }

    // --- Event Handlers

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
            if (!player.hasMetadata(META_ARENA)) player.setMetadata(META_ARENA, new FixedMetadataValue(this, true));
            switch (state.gameState) {
            case PAUSE: {
                player.setGameMode(GameMode.ADVENTURE);
                break;
            }
            case PLACE: {
                player.setGameMode(GameMode.SURVIVAL);
                break;
            }
            case GROW: {
                player.setGameMode(GameMode.ADVENTURE);
                if (arena.grassBlocks.contains(Vec.v(player.getLocation().getBlock().getRelative(0, -1, 0)))) {
                    warpPlayerOutside(player);
                }
                break;
            }
            default: break;
            }
        } else {
            if (player.hasMetadata(META_ARENA)) {
                player.removeMetadata(META_ARENA, this);
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    Material randomSign() {
        List<Material> mats = Arrays
            .asList(Material.ACACIA_SIGN,
                    Material.BIRCH_SIGN,
                    Material.DARK_OAK_SIGN,
                    Material.JUNGLE_SIGN,
                    Material.OAK_SIGN,
                    Material.SPRUCE_SIGN);
        return mats.get(ThreadLocalRandom.current().nextInt(mats.size()));
    }

    boolean isSign(Material mat) {
        switch (mat) {
        case ACACIA_SIGN:
        case BIRCH_SIGN:
        case DARK_OAK_SIGN:
        case JUNGLE_SIGN:
        case OAK_SIGN:
        case SPRUCE_SIGN:
            return true;
        default:
            return false;
        }
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
            for (Iterator<Placed> iter = state.placedSigns.iterator(); iter.hasNext();) {
                Placed placed = iter.next();
                if (placed.owner.equals(player.getUniqueId())) {
                    getServer().getWorld(arena.world).getBlockAt(placed.x, placed.y, placed.z).setType(Material.AIR);
                    iter.remove();
                }
            }
            state.placedSigns.add(new Placed(player.getUniqueId(), player.getName(), block.getX(), block.getY(), block.getZ()));
            saveState();
            event.setCancelled(false);
            player.sendMessage(ChatColor.GREEN + "Sign placed.");
        } else {
            if (!player.isOp()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        if (this.state.snow) return;
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(arena.world)) return;
        if (event.getSource().getType() != Material.GRASS_BLOCK
            || event.getBlock().getType() != Material.DIRT) return;
        if (!arena.grassBlocks.contains(Vec.v(block))) return;
        if (state.gameState != GameState.GROW) {
            event.setCancelled(true);
            return;
        }
        boolean removed = purgeSign(block);
        if (removed) saveState();
        block.getRelative(0, 1, 0).setType(Material.GRASS);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK, SoundCategory.MASTER, 1.0f, 1.0f);
        block.getWorld().spawnParticle(Particle.BLOCK_DUST, block.getLocation().add(0.5, 1.5, 0.5), 8, 0.2, 0.2, 0.2, 0.0, Material.GRASS_BLOCK.createBlockData());
        if (removed) checkForWinner();
    }

    void checkForWinner() {
        if (this.state.placedSigns.size() == 1) {
            announceArena(ChatColor.GREEN + state.placedSigns.get(0).ownerName + " wins the game!");
            state.winners.add(state.placedSigns.get(0).ownerName);
            setupGameState(GameState.PAUSE);
        }
    }

    void announceArena(String msg) {
        for (Player player: getServer().getWorld(arena.world).getPlayers()) {
            if (isInArena(player)) {
                player.sendMessage(msg);
            }
        }
    }
}

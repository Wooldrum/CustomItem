package com.example.ItemAtLocation;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ItemAtLocation extends JavaPlugin implements Listener, TabExecutor {

    private final Map<String, LocationData> locations = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> playersInRadius = new HashSet<>();
    private FileConfiguration locationsConfig;
    private File locationsFile;

    @Override
    public void onEnable() {
        getLogger().info(ChatColor.GOLD + "\n===========================================================\n"
                         + ChatColor.GOLD + "Thank you for using the ItemAtLocation Plugin! Report bugs at: discord.gg/invite/btsRNtnv8M\n"
                         + ChatColor.GOLD + "===========================================================");
        
        PluginCommand command = this.getCommand("itematlocation");
        if (command == null) {
            getLogger().severe("Main command 'itematlocation' not found! Make sure it is defined in the plugin.yml.");
        } else {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        createLocationsConfig();
        loadLocationsFromConfig();

        int pluginId = 23684;
        new Metrics(this, pluginId);

        getLogger().info(ChatColor.GOLD + "ItemAtLocation plugin enabled.");
    }

    @Override
    public void onDisable() {
        saveLocationsToConfig();
        getLogger().info(ChatColor.GOLD + "ItemAtLocation plugin disabled.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Location playerLocation = player.getLocation();
        boolean isInAnyRadius = false;

        for (LocationData locationData : locations.values()) {
            Location targetLocation = locationData.getLocation();
            if (targetLocation == null) {
                continue;
            }

            if (playerLocation.distanceSquared(targetLocation) <= Math.pow(locationData.getRadius(), 2)) {
                isInAnyRadius = true;

                if (cooldowns.containsKey(playerUUID)) {
                    long lastReceived = cooldowns.get(playerUUID);
                    long timeSinceLast = System.currentTimeMillis() - lastReceived;
                    if (timeSinceLast < locationData.getCooldownTime() * 1000L) {
                        if (!playersInRadius.contains(playerUUID)) {
                            long remainingTime = (locationData.getCooldownTime() * 1000L - timeSinceLast) / 1000;
                            player.sendMessage(ChatColor.GOLD + "You are still on cooldown for this spot. Please wait " + remainingTime + " seconds.");
                            playersInRadius.add(playerUUID);
                        }
                        continue;
                    }
                }

                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage(ChatColor.RED + "Your inventory is full, so you can't receive the reward.");
                    continue;
                }
                ItemStack itemStack = new ItemStack(locationData.getRewardItem(), 1);
                player.getInventory().addItem(itemStack);
                player.sendMessage(ChatColor.GOLD + "You have received: " + locationData.getRewardItem().name() + "!");

                cooldowns.put(playerUUID, System.currentTimeMillis());
                playersInRadius.add(playerUUID);
            }
        }

        if (!isInAnyRadius) {
            playersInRadius.remove(playerUUID);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.GOLD + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation <subcommand> [args]");
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "setlocation":
                if (args.length == 5) {
                    try {
                        String name = args[1];
                        double x = args[2].equals("~") ? player.getLocation().getX() : Double.parseDouble(args[2]);
                        double y = args[3].equals("~") ? player.getLocation().getY() : Double.parseDouble(args[3]);
                        double z = args[4].equals("~") ? player.getLocation().getZ() : Double.parseDouble(args[4]);
                        Location location = new Location(player.getWorld(), x, y, z);
                        locations.put(name, new LocationData(location, Material.DIAMOND, 60, 1.0));
                        player.sendMessage(ChatColor.GOLD + "Target location '" + name + "' set to: X=" + x + " Y=" + y + " Z=" + z);
                        saveLocationsToConfig();
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid coordinates. Please provide valid numbers or use '~' for relative coordinates.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation setlocation <name> <x> <y> <z>");
                }
                return true;

            case "setitem":
                if (args.length == 3) {
                    String locationName = args[1];
                    try {
                        Material rewardItem = Material.valueOf(args[2].toUpperCase());
                        if (locations.containsKey(locationName)) {
                            locations.get(locationName).setRewardItem(rewardItem);
                            player.sendMessage(ChatColor.GOLD + "Reward item for location '" + locationName + "' set to: " + rewardItem.name());
                            saveLocationsToConfig();
                        } else {
                            player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                        }
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid item type. Please provide a valid item.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation setitem <location> <item>");
                }
                return true;

            case "setcooldown":
                if (args.length == 3) {
                    String locationName = args[1];
                    try {
                        int cooldownTime = Integer.parseInt(args[2]);
                        if (locations.containsKey(locationName)) {
                            locations.get(locationName).setCooldownTime(cooldownTime);
                            player.sendMessage(ChatColor.GOLD + "Cooldown time for location '" + locationName + "' set to: " + cooldownTime + " seconds.");
                            saveLocationsToConfig();
                        } else {
                            player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid number format. Please provide a valid number.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation setcooldown <location> <seconds>");
                }
                return true;

            case "setradius":
                if (args.length == 3) {
                    String locationName = args[1];
                    try {
                        double radius = Double.parseDouble(args[2]);
                        if (locations.containsKey(locationName)) {
                            locations.get(locationName).setRadius(radius);
                            player.sendMessage(ChatColor.GOLD + "Radius for location '" + locationName + "' set to: " + radius + " blocks.");
                            saveLocationsToConfig();
                        } else {
                            player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid radius. Please provide a valid number.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation setradius <name> <radius>");
                }
                return true;

            case "removelocation":
                if (args.length > 1) {
                    String name = args[1];
                    if (locations.containsKey(name)) {
                        locations.remove(name);
                        player.sendMessage(ChatColor.GOLD + "Target location '" + name + "' has been removed.");
                        saveLocationsToConfig();
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Location '" + name + "' not found.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation removelocation <name>");
                }
                return true;

            case "listlocations":
                if (locations.isEmpty()) {
                    player.sendMessage(ChatColor.GOLD + "No locations have been set.");
                } else {
                    player.sendMessage(ChatColor.GOLD + "Configured locations:");
                    for (Map.Entry<String, LocationData> entry : locations.entrySet()) {
                        LocationData locationData = entry.getValue();
                        Location loc = locationData.getLocation();
                        player.sendMessage(ChatColor.YELLOW + entry.getKey() + " - X=" + loc.getX() + " Y=" + loc.getY() + " Z=" + loc.getZ() + " | Item=" + locationData.getRewardItem().name() + " | Cooldown=" + locationData.getCooldownTime() + "s | Radius=" + locationData.getRadius() + " blocks");
                    }
                }
                return true;

            case "teleport":
                if (args.length == 2) {
                    String locationName = args[1];
                    if (locations.containsKey(locationName)) {
                        Location targetLocation = locations.get(locationName).getLocation();
                        player.teleport(targetLocation);
                        player.sendMessage(ChatColor.GOLD + "Teleported to location '" + locationName + "'.");
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation teleport <location>");
                }
                return true;

            case "resetcooldown":
                if (args.length == 2) {
                    String locationName = args[1];
                    if (locations.containsKey(locationName)) {
                        cooldowns.remove(player.getUniqueId());
                        player.sendMessage(ChatColor.GOLD + "Cooldown for location '" + locationName + "' has been reset.");
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation resetcooldown <location>");
                }
                return true;

            case "disablelocation":
                if (args.length == 2) {
                    String locationName = args[1];
                    if (locations.containsKey(locationName)) {
                        locations.get(locationName).setEnabled(false);
                        player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' has been disabled.");
                        saveLocationsToConfig();
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation disablelocation <location>");
                }
                return true;

            case "enablelocation":
                if (args.length == 2) {
                    String locationName = args[1];
                    if (locations.containsKey(locationName)) {
                        locations.get(locationName).setEnabled(true);
                        player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' has been enabled.");
                        saveLocationsToConfig();
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Location '" + locationName + "' not found.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation enablelocation <location>");
                }
                return true;

            default:
                player.sendMessage(ChatColor.GOLD + "Unknown subcommand. Usage: /itematlocation <subcommand> [args]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("setlocation", "setitem", "setcooldown", "setradius", "removelocation", "listlocations", "teleport", "resetcooldown", "disablelocation", "enablelocation").stream()
                    .filter(subcommand -> subcommand.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("setitem") || args[0].equalsIgnoreCase("setcooldown") || args[0].equalsIgnoreCase("removelocation") || args[0].equalsIgnoreCase("setradius") || args[0].equalsIgnoreCase("teleport") || args[0].equalsIgnoreCase("resetcooldown") || args[0].equalsIgnoreCase("disablelocation") || args[0].equalsIgnoreCase("enablelocation"))) {
            return new ArrayList<>(locations.keySet()).stream()
                    .filter(name -> name.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void createLocationsConfig() {
        locationsFile = new File(getDataFolder(), "locations.yml");
        if (!locationsFile.exists()) {
            locationsFile.getParentFile().mkdirs();
            try {
                locationsFile.createNewFile();
                locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
                locationsConfig.set("_welcome", "Welcome to the ItemAtLocation configuration file!");
                locationsConfig.set("_usage", "Use this file to configure saved locations, including their coordinates, reward items, cooldown times, and radii.");
                saveLocationsToConfig();
            } catch (IOException e) {
                getLogger().severe("Could not create locations.yml!");
            }
        }
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
    }

    private void loadLocationsFromConfig() {
        for (String key : locationsConfig.getKeys(false)) {
            if (key.startsWith("_")) continue;
            String worldName = locationsConfig.getString(key + ".world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().severe("World " + worldName + " does not exist!");
                continue;
            }
            double x = locationsConfig.getDouble(key + ".x");
            double y = locationsConfig.getDouble(key + ".y");
            double z = locationsConfig.getDouble(key + ".z");
            Material rewardItem = Material.valueOf(locationsConfig.getString(key + ".rewardItem"));
            int cooldownTime = locationsConfig.getInt(key + ".cooldownTime");
            double radius = locationsConfig.getDouble(key + ".radius");
            boolean enabled = locationsConfig.getBoolean(key + ".enabled", true);

            Location location = new Location(world, x, y, z);
            locations.put(key, new LocationData(location, rewardItem, cooldownTime, radius, enabled));
        }
    }

    private void saveLocationsToConfig() {
        for (Map.Entry<String, LocationData> entry : locations.entrySet()) {
            String key = entry.getKey();
            LocationData locationData = entry.getValue();
            locationsConfig.set(key + ".world", locationData.getLocation().getWorld().getName());
            locationsConfig.set(key + ".x", locationData.getLocation().getX());
            locationsConfig.set(key + ".y", locationData.getLocation().getY());
            locationsConfig.set(key + ".z", locationData.getLocation().getZ());
            locationsConfig.set(key + ".rewardItem", locationData.getRewardItem().name());
            locationsConfig.set(key + ".cooldownTime", locationData.getCooldownTime());
            locationsConfig.set(key + ".radius", locationData.getRadius());
            locationsConfig.set(key + ".enabled", locationData.isEnabled());
        }
        try {
            locationsConfig.save(locationsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save locations.yml!");
        }
    }

    private static class LocationData {
        private final Location location;
        private Material rewardItem;
        private int cooldownTime;
        private double radius;
        private boolean enabled;

        public LocationData(Location location, Material rewardItem, int cooldownTime, double radius) {
            this(location, rewardItem, cooldownTime, radius, true);
        }

        public LocationData(Location location, Material rewardItem, int cooldownTime, double radius, boolean enabled) {
            this.location = location;
            this.rewardItem = rewardItem;
            this.cooldownTime = cooldownTime;
            this.radius = radius;
            this.enabled = enabled;
        }

        public Location getLocation() {
            return location;
        }

        public Material getRewardItem() {
            return rewardItem;
        }

        public void setRewardItem(Material rewardItem) {
            this.rewardItem = rewardItem;
        }

        public int getCooldownTime() {
            return cooldownTime;
        }

        public void setCooldownTime(int cooldownTime) {
            this.cooldownTime = cooldownTime;
        }

        public double getRadius() {
            return radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

package com.example.ItemAtLocation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> playersInRadius = new HashSet<>();
    private FileConfiguration locationsConfig;
    private File locationsFile;

    @Override
    public void onEnable() {
        // Register main command
        PluginCommand command = this.getCommand("itematlocation");
        if (command == null) {
            getLogger().severe("Main command 'itematlocation' not found! Make sure it is defined in the plugin.yml.");
        } else {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Load locations configuration
        createLocationsConfig();
        loadLocationsFromConfig();

        // Display startup message
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "=================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "Thanks for trying the ItemAtLocation Plugin! Report bugs at: https://discord.gg/btsRNtnv8M");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "=================");

        getLogger().info("ItemAtLocation plugin enabled.");
    }

    @Override
    public void onDisable() {
        saveLocationsToConfig();
        getLogger().info("ItemAtLocation plugin disabled.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        boolean isInAnyRadius = false;

        for (LocationData locationData : locations.values()) {
            Location targetLocation = locationData.getLocation();
            if (targetLocation == null) continue;

            // Check if player is within radius of the target location
            Location playerLocation = player.getLocation();
            if (playerLocation.distance(targetLocation) <= locationData.getRadius()) {
                isInAnyRadius = true;

                // Check if the player is on cooldown
                if (cooldowns.containsKey(playerUUID)) {
                    long lastReceived = cooldowns.get(playerUUID);
                    if (System.currentTimeMillis() - lastReceived < locationData.getCooldownTime() * 1000L) {
                        if (!playersInRadius.contains(playerUUID)) {
                            player.sendMessage(ChatColor.GOLD + "You are still on cooldown for this spot. Please wait " + ((locationData.getCooldownTime() * 1000L - (System.currentTimeMillis() - lastReceived)) / 1000) + " seconds.");
                            playersInRadius.add(playerUUID);
                        }
                        continue; // Still on cooldown
                    }
                }

                // Give the player the reward item
                ItemStack itemStack = new ItemStack(locationData.getRewardItem(), 1);
                player.getInventory().addItem(itemStack);
                player.sendMessage(ChatColor.GOLD + "You have received: " + locationData.getRewardItem().name() + "!");

                // Set cooldown
                cooldowns.put(playerUUID, System.currentTimeMillis());
                playersInRadius.add(playerUUID);
            }
        }

        // Remove player from radius set if they are no longer in any radius
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
                if (args.length > 1) {
                    try {
                        String locationName = args[1];
                        if (locations.containsKey(locationName)) {
                            Material rewardItem = Material.valueOf(args[2].toUpperCase());
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
                if (args.length > 2) {
                    try {
                        String locationName = args[1];
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

            case "setradius":
                if (args.length > 2) {
                    String name = args[1];
                    try {
                        double radius = Double.parseDouble(args[2]);
                        if (locations.containsKey(name)) {
                            locations.get(name).setRadius(radius);
                            player.sendMessage(ChatColor.GOLD + "Radius for location '" + name + "' set to: " + radius + " blocks.");
                            saveLocationsToConfig();
                        } else {
                            player.sendMessage(ChatColor.GOLD + "Location '" + name + "' not found.");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid radius. Please provide a valid number.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /itematlocation setradius <name> <radius>");
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
            return Arrays.asList("setlocation", "setitem", "setcooldown", "removelocation", "setradius").stream()
                    .filter(subcommand -> subcommand.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("setitem") || args[0].equalsIgnoreCase("setcooldown") || args[0].equalsIgnoreCase("removelocation") || args[0].equalsIgnoreCase("setradius"))) {
            return new ArrayList<>(locations.keySet()).stream()
                    .filter(name -> name.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setitem")) {
            return Arrays.stream(Material.values())
                    .map(Material::name)
                    .filter(name -> name.startsWith(args[2].toUpperCase()))
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
                // Add welcome message with usage cases
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
            if (key.startsWith("_")) continue; // Skip welcome/usage keys
            String worldName = locationsConfig.getString(key + ".world");
            double x = locationsConfig.getDouble(key + ".x");
            double y = locationsConfig.getDouble(key + ".y");
            double z = locationsConfig.getDouble(key + ".z");
            Material rewardItem = Material.valueOf(locationsConfig.getString(key + ".rewardItem"));
            int cooldownTime = locationsConfig.getInt(key + ".cooldownTime");
            double radius = locationsConfig.getDouble(key + ".radius");

            Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
            locations.put(key, new LocationData(location, rewardItem, cooldownTime, radius));
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
        }
        try {
            locationsConfig.save(locationsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save locations.yml!");
        }
    }

    private static class LocationData {
        private Location location;
        private Material rewardItem;
        private int cooldownTime;
        private double radius;

        public LocationData(Location location, Material rewardItem, int cooldownTime, double radius) {
            this.location = location;
            this.rewardItem = rewardItem;
            this.cooldownTime = cooldownTime;
            this.radius = radius;
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
    }
}

package com.example.ItemAtLocation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class ItemAtLocation extends JavaPlugin implements Listener, TabExecutor {

    private Location targetLocation = null;
    private Material rewardItem = Material.DIAMOND;
    private int cooldownTime = 60; // in seconds
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        // Register commands
        registerCommand("setlocation");
        registerCommand("setitem");
        registerCommand("setcooldown");
        registerCommand("removelocation");

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("ItemAtLocation plugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ItemAtLocation plugin disabled.");
    }

    private void registerCommand(String commandName) {
        PluginCommand command = this.getCommand(commandName);
        if (command == null) {
            getLogger().severe("Command '" + commandName + "' not found! Make sure it is defined in the plugin.yml.");
        } else {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (targetLocation == null) return;

        // Check if player has moved to a different block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Check if player is within 1 block radius of the target location
        Location playerLocation = player.getLocation();
        if (playerLocation.distance(targetLocation) <= 1) {
            UUID playerUUID = player.getUniqueId();

            // Check if the player is on cooldown
            if (cooldowns.containsKey(playerUUID)) {
                long lastReceived = cooldowns.get(playerUUID);
                if (System.currentTimeMillis() - lastReceived < cooldownTime * 1000L) {
                    player.sendMessage(ChatColor.GOLD + "You are still on cooldown. Please wait " + ((cooldownTime * 1000L - (System.currentTimeMillis() - lastReceived)) / 1000) + " seconds.");
                    return; // Still on cooldown
                }
            }

            // Give the player the reward item
            ItemStack itemStack = new ItemStack(rewardItem, 1);
            player.getInventory().addItem(itemStack);
            player.sendMessage(ChatColor.GOLD + "You have received: " + rewardItem.name() + "!");

            // Set cooldown
            cooldowns.put(playerUUID, System.currentTimeMillis());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.GOLD + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "setlocation":
                if (args.length == 3) {
                    try {
                        double x = args[0].equals("~") ? player.getLocation().getX() : Double.parseDouble(args[0]);
                        double y = args[1].equals("~") ? player.getLocation().getY() : Double.parseDouble(args[1]);
                        double z = args[2].equals("~") ? player.getLocation().getZ() : Double.parseDouble(args[2]);
                        targetLocation = new Location(player.getWorld(), x, y, z);
                        player.sendMessage(ChatColor.GOLD + "Target location set to: X=" + x + " Y=" + y + " Z=" + z);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid coordinates. Please provide valid numbers or use '~' for relative coordinates.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /setlocation <x> <y> <z>");
                }
                getLogger().info(player.getName() + " set a new target location at: " + targetLocation);
                return true;

            case "setitem":
                if (args.length > 0) {
                    try {
                        rewardItem = Material.valueOf(args[0].toUpperCase());
                        player.sendMessage(ChatColor.GOLD + "Reward item set to: " + rewardItem.name());
                        getLogger().info(player.getName() + " set reward item to: " + rewardItem.name());
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid item type. Please provide a valid item.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /setitem <item>");
                }
                return true;

            case "setcooldown":
                if (args.length > 0) {
                    try {
                        cooldownTime = Integer.parseInt(args[0]);
                        player.sendMessage(ChatColor.GOLD + "Cooldown time set to: " + cooldownTime + " seconds.");
                        getLogger().info(player.getName() + " set cooldown time to: " + cooldownTime + " seconds.");
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.GOLD + "Invalid number format. Please provide a valid number.");
                    }
                } else {
                    player.sendMessage(ChatColor.GOLD + "Usage: /setcooldown <seconds>");
                }
                return true;

            case "removelocation":
                targetLocation = null;
                player.sendMessage(ChatColor.GOLD + "Target location has been removed.");
                getLogger().info(player.getName() + " removed the target location.");
                return true;

            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("setitem") && args.length == 1) {
            return Arrays.stream(Material.values())
                    .map(Material::name)
                    .filter(name -> name.startsWith(args[0].toUpperCase()))
                    .collect(Collectors.toList());
        } else if (command.getName().equalsIgnoreCase("setlocation")) {
            if (args.length == 1 || args.length == 2 || args.length == 3) {
                return Arrays.asList("~", String.valueOf((int) ((Player) sender).getLocation().getX()), String.valueOf((int) ((Player) sender).getLocation().getY()), String.valueOf((int) ((Player) sender).getLocation().getZ()));
            }
        }
        return Collections.emptyList();
    }
}

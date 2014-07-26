
package com.wasteofplastic.greenhouses;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.UUID;

import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GreenhouseCmd implements CommandExecutor {
    public boolean busyFlag = true;
    private Greenhouses plugin;
    private PlayerCache players;

    /**
     * Constructor
     * 
     * @param plugin
     * @param players 
     */
    public GreenhouseCmd(Greenhouses plugin, PlayerCache players) {

	// Plugin instance
	this.plugin = plugin;
	this.players = players;
    }
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender
     * , org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] split) {
	if (!(sender instanceof Player)) {
	    return false;
	}
	final Player player = (Player) sender;
	// Check we are in the right world
	if (!player.getWorld().getName().equalsIgnoreCase(Settings.worldName)) {
	    player.sendMessage("Greenhouses only available in " + Settings.worldName + " world.");
	    return true;
	}
	// Basic permissions check to even use /greenhouse
	if (!VaultHelper.checkPerm(player, "greenhouses.player")) {
	    player.sendMessage(ChatColor.RED + Locale.errorNoPermission);
	    return true;
	}
	/*
	 * Grab data for this player - may be null or empty
	 * playerUUID is the unique ID of the player who issued the command
	 */
	final UUID playerUUID = player.getUniqueId();
	switch (split.length) {
	// /greenhouse command by itself
	case 0:
	case 1:
	    // /greenhouse <command>
	    if (split.length == 0 || split[0].equalsIgnoreCase("help")) { 
		player.sendMessage(ChatColor.GREEN + "Greenhouses " + plugin.getDescription().getVersion() + " help:");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse view: " + ChatColor.WHITE + "Toggles the red greenhouse boundary visualization on or off");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse trust <playername>: " + ChatColor.WHITE + "Gives player full access to your greenhouse");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse untrust <playername>: " + ChatColor.WHITE + "Revokes trust to your greenhouse");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse untrustall: " + ChatColor.WHITE + "Removes all trusted parties from your greenhouse");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse remove: " + ChatColor.WHITE + "Removes a greenhouse that you are standing in if you are the owner");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse info: " + ChatColor.WHITE + "Shows info on the greenhouse you are in");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse buy: " + ChatColor.WHITE + "Attempts to buy the greenhouse you are in");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse rent: " + ChatColor.WHITE + "Attempts to rent the greenhouse you are in");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse rent <price>: " + ChatColor.WHITE + "Puts the greenhouse you are in up for rent for a weekly rent");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse sell <price>: " + ChatColor.WHITE + "Puts the greenhouse you are in up for sale");
		player.sendMessage(ChatColor.YELLOW + "/greenhouse cancel: " + ChatColor.WHITE + "Cancels a For Sale, For Rent or a Lease");
		return true;
	    } else if (split[0].equalsIgnoreCase("untrustall")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d == null) {
		    player.sendMessage(ChatColor.RED + "Move to a greenhouse you own or rent first.");
		    return true;
		}
		if (d.getOwner().equals(playerUUID) || d.getRenter().equals(playerUUID)) {
		    if (d.getOwner().equals(playerUUID)) {
			if (!d.getOwnerTrusted().isEmpty()) {
			    // Tell everyone
			    for (UUID n : d.getOwnerTrustedUUID()) {
				Player p = plugin.getServer().getPlayer(n);
				if (p != null) {
				    p.sendMessage(ChatColor.RED + player.getDisplayName() + " untrusted you in a greenhouse.");
				}
			    }
			    // Blank it out
			    d.setOwnerTrusted(new ArrayList<UUID>());
			}
		    } else {
			if (!d.getRenterTrusted().isEmpty()) {
			    for (UUID n : d.getRenterTrustedUUID()) {
				Player p = plugin.getServer().getPlayer(n);
				if (p != null) {
				    p.sendMessage(ChatColor.RED + player.getDisplayName() + " untrusted you in a greenhouse.");
				}
			    }
			    // Blank it out
			    d.setRenterTrusted(new ArrayList<UUID>());
			}
		    }
		    player.sendMessage(ChatColor.GOLD + "[Greenhouse Trusted Players]");
		    player.sendMessage(ChatColor.GREEN + "[Owner's]");
		    if (d.getOwnerTrusted().isEmpty()) {
			player.sendMessage("None");
		    } else for (String name : d.getOwnerTrusted()) {
			player.sendMessage(name);
		    }
		    player.sendMessage(ChatColor.GREEN + "[Renter's]");
		    if (d.getRenterTrusted().isEmpty()) {
			player.sendMessage("None");
		    } else for (String name : d.getRenterTrusted()) {
			player.sendMessage(name);
		    }
		    return true;
		} else {
		    player.sendMessage(ChatColor.RED + "You must be the owner or renter of this greenhouse to do that.");
		    return true;
		}

	    } else if (split[0].equalsIgnoreCase("view")) {
		// Toggle the visualization setting
		if (players.getVisualize(playerUUID)) {
		    plugin.devisualize(player);
		    player.sendMessage(ChatColor.YELLOW + "Switching greenhouse boundary off");
		} else {
		    player.sendMessage(ChatColor.YELLOW + "Switching greenhouse boundary on");
		}
		players.setVisualize(playerUUID, !players.getVisualize(playerUUID));		
		return true;
	    } else if (split[0].equalsIgnoreCase("pos")) {
		// TODO: Put more checks into the setting of a greenhouse
		if (players.getInGreenhouse(playerUUID) != null) {
		    player.sendMessage(ChatColor.RED + "Move out of this greenhouse to create another.");
		    return true;
		}
		if (plugin.getPos1s().containsKey(playerUUID)) {
		    int height = Math.abs(plugin.getPos1s().get(playerUUID).getBlockX() - player.getLocation().getBlockX()) + 1;
		    int width = Math.abs(plugin.getPos1s().get(playerUUID).getBlockX() - player.getLocation().getBlockX()) + 1;
		    int blocks = height * width;

		    // Check if they have enough blocks
		    if (blocks > players.getBlockBalance(playerUUID)) {
			player.sendMessage(ChatColor.RED + "You do not have enough blocks!");
			player.sendMessage(ChatColor.RED + "Blocks available: " + players.getBlockBalance(playerUUID));
			player.sendMessage(ChatColor.RED + "Blocks required: " + blocks);
			return true;  
		    }
		    if (height < 5 || width < 5) {
			player.sendMessage(ChatColor.RED + "The minimum side distance is 5 blocks");
			return true;		    
		    }
		    // Find the corners of this greenhouse
		    Location pos1 = new Location(player.getWorld(),plugin.getPos1s().get(playerUUID).getBlockX(),0,plugin.getPos1s().get(playerUUID).getBlockZ());
		    Location pos2 = new Location(player.getWorld(),player.getLocation().getBlockX(),0,player.getLocation().getBlockZ());
		    if (!plugin.checkGreenhouseIntersection(pos1, pos2)) {
			plugin.createNewGreenhouse(pos1, pos2, player);
			players.removeBlocks(playerUUID, blocks);
			players.save(playerUUID);
			player.sendMessage(ChatColor.GOLD + "Greenhouse created!");
			player.sendMessage(ChatColor.GOLD + "You have " + players.getBlockBalance(playerUUID) + " blocks left.");
		    } else {
			player.sendMessage(ChatColor.RED + "That sized greenhouse could not be made because it overlaps another greenhouse");		    		    
		    }
		} else {
		    plugin.getPos1s().put(playerUUID, player.getLocation());
		    player.sendMessage("Setting position 1 : " + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockZ());
		}
		return true;
	    } else if (split[0].equalsIgnoreCase("setbiome")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (d.getOwner().equals(playerUUID)) {
			int minX = Math.min(d.getPos1().getBlockX(), d.getPos2().getBlockX());
			int maxX = Math.max(d.getPos1().getBlockX(), d.getPos2().getBlockX());
			int minZ = Math.min(d.getPos1().getBlockZ(), d.getPos2().getBlockZ());
			int maxZ = Math.max(d.getPos1().getBlockZ(), d.getPos2().getBlockZ());
			for (int x = minX; x<= maxX; x++) {
			    for (int z = minZ; z<= maxZ; z++) {
				Block b = player.getWorld().getHighestBlockAt(new Location(player.getWorld(),x,0,z));
				b.setBiome(Biome.SUNFLOWER_PLAINS);
				b.setType(Material.GRASS);
				BlockState bs = b.getState();
				bs.getBlock().setType(bs.getType());
				bs.getBlock().setData(bs.getBlock().getData());
			    }
			}
		    }
		}
		return true;
	    } else if (split[0].equalsIgnoreCase("remove")) {

		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (d.getOwner().equals(playerUUID)) {
			player.sendMessage(ChatColor.RED + "Removing greenhouse!");
			// Remove the greenhouse
			HashSet<GreenhouseRegion> ds = plugin.getGreenhouses();
			ds.remove(d);
			plugin.setGreenhouses(ds);
			// Find everyone who is in this greenhouse and remove them
			for (Player p : plugin.getServer().getOnlinePlayers()) {
			    if (d.intersectsGreenhouse(p.getLocation())) {
				players.setInGreenhouse(p.getUniqueId(), null);
				plugin.devisualize(p);
				if (!player.equals(p)) {
				    p.sendMessage(player.getDisplayName() + ChatColor.RED + " removed their greenhouse!");
				}
			    }
			}
			// Return blocks
			int height = Math.abs(d.getPos1().getBlockX() - d.getPos2().getBlockX()) + 1;
			int width = Math.abs(d.getPos1().getBlockX() - d.getPos2().getBlockX()) + 1;
			int blocks = height * width;
			int balance = plugin.players.addBlocks(playerUUID, blocks);
			player.sendMessage("Recovered " + blocks + " blocks. Your balance is " + balance);
			return true;
		    }
		    player.sendMessage(ChatColor.RED + "This is not your greenhouse!");
		} else {
		    player.sendMessage(ChatColor.RED + "You are not in a greenhouse!"); 
		}
		return true;
	    } else if (split[0].equalsIgnoreCase("balance")) {
		int balance = plugin.players.getBlockBalance(playerUUID);
		player.sendMessage("Your block balance is " + balance);
		return true;
	    } else if (split[0].equalsIgnoreCase("buy")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (!d.isForSale()) {
			player.sendMessage(ChatColor.RED + "This greenhouse is not for sale!");
			return true;
		    }
		    if (d.getOwner().equals(playerUUID)) {
			player.sendMessage(ChatColor.RED + "You already own this greenhouse!");
			return true;
		    } 
		    // See if the player can afford it
		    if (!VaultHelper.econ.has(player, d.getPrice())) {
			player.sendMessage(ChatColor.RED + "You cannot afford " + VaultHelper.econ.format(d.getPrice()));
			return true;
		    }
		    // It's for sale, the player can afford it and it's not the owner - sell!
		    EconomyResponse resp = VaultHelper.econ.withdrawPlayer(player, d.getPrice());
		    if (resp.transactionSuccess()) {
			// Pay the owner
			OfflinePlayer owner = plugin.getServer().getOfflinePlayer(d.getOwner());
			EconomyResponse r = VaultHelper.econ.depositPlayer(owner, d.getPrice());
			if (!r.transactionSuccess()) {
			    plugin.getLogger().severe("Could not pay " + owner.getName() + " " + d.getPrice() + " for greenhouse they sold to " + player.getName());
			}
			// Check if owner is online
			if (owner.isOnline()) {
			    plugin.devisualize((Player)owner);
			    ((Player)owner).sendMessage("You successfully sold a greenhouse for " + VaultHelper.econ.format(d.getPrice()) + " to " + player.getDisplayName());
			} else {
			    plugin.setMessage(owner.getUniqueId(), "You successfully sold a greenhouse for " + VaultHelper.econ.format(d.getPrice()) + " to " + player.getDisplayName());
			}
			Location pos1 = d.getPos1();
			Location pos2 = d.getPos2();
			player.sendMessage("You purchased the greenhouse for "+ VaultHelper.econ.format(d.getPrice()) + "!");
			// Remove the greenhouse
			HashSet<GreenhouseRegion> ds = plugin.getGreenhouses();
			ds.remove(d);
			plugin.setGreenhouses(ds);
			// Recreate the greenhouse for this player
			plugin.createNewGreenhouse(pos1, pos2, player);
			players.save(owner.getUniqueId());
			return true;
		    } else {
			player.sendMessage(ChatColor.RED + "There was an economy problem trying to purchase the greenhouse for "+ VaultHelper.econ.format(d.getPrice()) + "!");
			player.sendMessage(ChatColor.RED + resp.errorMessage);
			return true;
		    }
		}
		player.sendMessage(ChatColor.RED + "This is not your greenhouse!");
	    } else if (split[0].equalsIgnoreCase("rent")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (!d.isForRent()) {
			player.sendMessage(ChatColor.RED + "This greenhouse is not for rent!");
			return true;
		    }
		    if (d.getOwner() != null && d.getOwner().equals(playerUUID)) {
			player.sendMessage(ChatColor.RED + "You own this greenhouse!");
			return true;
		    }
		    if (d.getRenter() != null && d.getRenter().equals(playerUUID)) {
			player.sendMessage(ChatColor.RED + "You are already renting this greenhouse!");
			return true;			
		    }
		    if (d.isForRent() && d.getRenter() != null) {
			player.sendMessage(ChatColor.RED + "This greenhouse is already being leased.");
			return true;						
		    }
		    // See if the player can afford it
		    if (!VaultHelper.econ.has(player, d.getPrice())) {
			player.sendMessage(ChatColor.RED + "You cannot afford " + VaultHelper.econ.format(d.getPrice()));
			return true;
		    }
		    // It's for rent, the player can afford it and it's not the owner - rent!
		    EconomyResponse resp = VaultHelper.econ.withdrawPlayer(player, d.getPrice());
		    if (resp.transactionSuccess()) {
			// Check if owner is online
			Player owner = plugin.getServer().getPlayer(d.getOwner());
			if (owner != null) {
			    plugin.devisualize(owner);
			    owner.sendMessage("You successfully rented a greenhouse for " + VaultHelper.econ.format(d.getPrice()) + " to " + player.getDisplayName());
			} else {
			    plugin.setMessage(d.getOwner(), "You successfully rented a greenhouse for " + VaultHelper.econ.format(d.getPrice()) + " to " + player.getDisplayName());
			}
			// It will stay for rent until the landlord cancels the lease
			//d.setForRent(false);
			d.setRenter(playerUUID);
			Calendar currentDate = Calendar.getInstance();
			// Only work in days
			currentDate.set(Calendar.HOUR_OF_DAY, 0);            // set hour to midnight
			currentDate.set(Calendar.MINUTE, 0);                 // set minute in hour
			currentDate.set(Calendar.SECOND, 0);                 // set second in minute
			currentDate.set(Calendar.MILLISECOND, 0);            // set millisecond in second
			d.setLastPayment(currentDate.getTime());
			player.sendMessage("You rented the greenhouse for "+ VaultHelper.econ.format(d.getPrice()) + " 1 week!");
			d.setEnterMessage("Welcome to " + player.getDisplayName() + "'s rented greenhouse!");
			d.setFarewellMessage("Now leaving " + player.getDisplayName() + "'s rented greenhouse.");
			players.save(d.getOwner());
			return true;
		    } else {
			player.sendMessage(ChatColor.RED + "There was an economy problem trying to rent the greenhouse for "+ VaultHelper.econ.format(d.getPrice()) + "!");
			player.sendMessage(ChatColor.RED + resp.errorMessage);
			return true;
		    }
		}
		player.sendMessage(ChatColor.RED + "This is not your greenhouse!");
	    } else if (split[0].equalsIgnoreCase("cancel")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (d.getOwner().equals(playerUUID)) {
			// If no one has rented the greenhouse yet
			if (d.getRenter() == null) {
			    player.sendMessage(ChatColor.GOLD + "Greenhouse is no longer for sale or rent.");
			    d.setForSale(false);
			    d.setForRent(false);
			    d.setPrice(0D);
			    return true;
			} else {
			    player.sendMessage(ChatColor.GOLD + "Greenhouse is currently leased by " + players.getName(d.getRenter()) + ".");
			    player.sendMessage(ChatColor.GOLD + "Lease will not renew and will terminate in " + plugin.daysToEndOfLease(d) + " days.");
			    player.sendMessage(ChatColor.GOLD + "You can put it up for rent again after that date.");
			    if (plugin.getServer().getPlayer(d.getRenter()) != null) {
				plugin.getServer().getPlayer(d.getRenter()).sendMessage( players.getName(d.getOwner()) + " ended a lease you have on a greenhouse. It will end in " + plugin.daysToEndOfLease(d) + " days.");
			    } else {
				plugin.setMessage(d.getRenter(), players.getName(d.getOwner()) + " ended a lease you have on a greenhouse!");
			    }

			    d.setForSale(false);
			    d.setForRent(false);
			    d.setPrice(0D);
			    return true;

			}
		    } else if (d.getRenter() != null && d.getRenter().equals(player.getUniqueId())) {
			// Renter wanting to cancel the lease
			player.sendMessage(ChatColor.GOLD + "Lease renewal cancelled. Lease term finishes in " + plugin.daysToEndOfLease(d) + " days.");
			if (plugin.getServer().getPlayer(d.getOwner()) != null) {
			    plugin.getServer().getPlayer(d.getOwner()).sendMessage( player.getDisplayName() + " canceled a lease with you. It will end in " + plugin.daysToEndOfLease(d) + " days.");
			} else {
			    plugin.setMessage(d.getOwner(), player.getDisplayName() + " canceled a lease with you. It will end in " + plugin.daysToEndOfLease(d) + " days.");
			}
			d.setForSale(false);
			d.setForRent(false);
			d.setPrice(0D);
			return true;
		    } else {
			player.sendMessage(ChatColor.RED + "This is not your greenhouse!");
		    }
		} else {
		    player.sendMessage(ChatColor.RED + "You are not in a greenhouse!"); 
		}
		return true;


	    } else if (split[0].equalsIgnoreCase("trust") || split[0].equalsIgnoreCase("info")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d == null) {
		    player.sendMessage(ChatColor.RED + "Move to a greenhouse first to see info.");
		    return true;
		}
		player.sendMessage(ChatColor.GOLD + "[Greenhouse Info]");
		if (d.getOwner() != null) {
		    Player owner = plugin.getServer().getPlayer(d.getOwner());
		    if (owner != null) {
			player.sendMessage(ChatColor.YELLOW + "Owner: " + owner.getDisplayName() + " (" + owner.getName() + ")");
		    } else {
			player.sendMessage(ChatColor.YELLOW + "Owner: " + players.getName(d.getOwner()));
		    }
		    player.sendMessage(ChatColor.GREEN + "[Owner's trusted players]");
		    if (d.getOwnerTrusted().isEmpty()) {
			player.sendMessage("None");
		    } else for (String name : d.getOwnerTrusted()) {
			player.sendMessage(name);
		    }
		}
		if (d.getRenter() != null) {
		    if (d.isForRent()) {
			player.sendMessage(ChatColor.YELLOW + "Next rent of " + VaultHelper.econ.format(d.getPrice()) + " due in " + plugin.daysToEndOfLease(d) + " days.");
		    } else {
			player.sendMessage(ChatColor.RED + "Lease will end in " + plugin.daysToEndOfLease(d) + " days!");
		    }
		    Player renter = plugin.getServer().getPlayer(d.getRenter());
		    if (renter != null) {
			player.sendMessage(ChatColor.YELLOW + "Renter: " + renter.getDisplayName() + " (" + renter.getName() + ")");
		    } else {
			player.sendMessage(ChatColor.YELLOW + "Renter: " + players.getName(d.getRenter()));
		    }
		    player.sendMessage(ChatColor.GREEN + "[Renter's trusted players]");
		    if (d.getRenterTrusted().isEmpty()) {
			player.sendMessage("None");
		    } else for (String name : d.getRenterTrusted()) {
			player.sendMessage(name);
		    }
		} else {
		    if (d.isForRent()) {
			player.sendMessage(ChatColor.YELLOW + "This greenhouse can be leased for " + VaultHelper.econ.format(d.getPrice()));
		    }
		}
		return true;

	    }
	    break;
	case 2:
	    if (split[0].equalsIgnoreCase("untrust")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d == null) {
		    player.sendMessage(ChatColor.RED + "Move to a greenhouse you own or rent first.");
		    return true;
		}
		if (d.getOwner().equals(playerUUID) || d.getRenter().equals(playerUUID)) {
		    // Check that we know this person
		    UUID trusted = players.getUUID(split[1]);
		    if (trusted == null) {
			player.sendMessage(ChatColor.RED + "Unknown player.");
			return true;			
		    }

		    if (d.getOwner().equals(playerUUID)) {
			if (d.getOwnerTrusted().isEmpty()) {
			    player.sendMessage(ChatColor.RED + "No one is trusted in this greenhouse.");
			} else {
			    // Remove trusted player
			    d.removeOwnerTrusted(trusted);
			    Player p = plugin.getServer().getPlayer(trusted);
			    if (p != null) {
				p.sendMessage(ChatColor.RED + player.getDisplayName() + " untrusted you in a greenhouse.");
			    }


			}
		    } else {
			if (d.getRenterTrusted().isEmpty()) {
			    player.sendMessage(ChatColor.RED + "No one is trusted in this greenhouse.");
			} else {
			    Player p = plugin.getServer().getPlayer(trusted);
			    if (p != null) {
				p.sendMessage(ChatColor.RED + player.getDisplayName() + " untrusted you in a greenhouse.");
			    }
			    // Blank it out
			    d.removeRenterTrusted(trusted);
			}
		    }
		    players.save(d.getOwner());
		    player.sendMessage(ChatColor.GOLD + "[Greenhouse Trusted Players]");
		    player.sendMessage(ChatColor.GREEN + "[Owner's]");
		    if (d.getOwnerTrusted().isEmpty()) {
			player.sendMessage("None");
		    } else for (String name : d.getOwnerTrusted()) {
			player.sendMessage(name);
		    }
		    player.sendMessage(ChatColor.GREEN + "[Renter's]");
		    if (d.getRenterTrusted().isEmpty()) {
			player.sendMessage("None");
		    } else for (String name : d.getRenterTrusted()) {
			player.sendMessage(name);
		    }	
		    return true;
		} else {
		    player.sendMessage(ChatColor.RED + "You must be the owner or renter of this greenhouse to do that.");
		    return true;
		}

	    } else if (split[0].equalsIgnoreCase("trust")) {
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d == null) {
		    player.sendMessage(ChatColor.RED + "Move to a greenhouse you own or rent first.");
		    return true;
		}
		if ((d.getOwner() != null && d.getOwner().equals(playerUUID)) || (d.getRenter() != null && d.getRenter().equals(playerUUID))) {
		    // Check that we know this person
		    UUID trusted = players.getUUID(split[1]);
		    if (trusted != null) {
			/*
			 * TODO: ADD IN AFTER TESTING!
			if (d.getOwner() != null && d.getOwner().equals(trusted)) {
			    player.sendMessage(ChatColor.RED + "That player is the owner and so trusted already.");
				return true;
			}
			if (d.getRenter() != null && d.getRenter().equals(trusted)) {
			    player.sendMessage(ChatColor.RED + "That player is the renter and so trusted already.");
				return true;
			}*/			
			// This is a known player, name is OK
			if (d.getOwner().equals(playerUUID)) {
			    if (!d.addOwnerTrusted(trusted)) {
				player.sendMessage(ChatColor.RED + "That player is already trusted.");
				return true;
			    }
			} else {
			    if (!d.addRenterTrusted(trusted)) {
				player.sendMessage(ChatColor.RED + "That player is already trusted.");
				return true;
			    } 			    
			}
			Player p = plugin.getServer().getPlayer(trusted);
			if (p != null) {
			    p.sendMessage(ChatColor.RED + player.getDisplayName() + " trusts you in a greenhouse.");
			}
			players.save(d.getOwner());
			player.sendMessage(ChatColor.GOLD + "[Greenhouse Info]");
			player.sendMessage(ChatColor.GREEN + "[Owner's trusted players]");
			if (d.getOwnerTrusted().isEmpty()) {
			    player.sendMessage("None");
			} else for (String name : d.getOwnerTrusted()) {
			    player.sendMessage(name);
			}
			player.sendMessage(ChatColor.GREEN + "[Renter's trusted players]");
			if (d.getRenterTrusted().isEmpty()) {
			    player.sendMessage("None");
			} else for (String name : d.getRenterTrusted()) {
			    player.sendMessage(name);
			}
			return true;
		    } else {
			player.sendMessage(ChatColor.RED + "Unknown player.");
			return true;
		    }
		} else {
		    player.sendMessage(ChatColor.RED + "You must be the owner or renter to add them to this greenhouse.");
		    return true;
		}

	    } else if (split[0].equalsIgnoreCase("claim")) {
		// TODO: Put more checks into the setting of a greenhouse
		if (players.getInGreenhouse(playerUUID) != null) {
		    player.sendMessage(ChatColor.RED + "Move out of this greenhouse first.");
		    return true;
		}
		int blocks = 0;
		try {
		    blocks = Integer.parseInt(split[1]);
		} catch (Exception e) {
		    player.sendMessage(ChatColor.RED + "/greenhouse claim <number of blocks radius>");
		    return true;		    
		}
		// Check if they have enough blocks
		int blocksRequired = (blocks*2+1)*(blocks*2+1);
		if (blocksRequired > players.getBlockBalance(playerUUID)) {
		    player.sendMessage(ChatColor.RED + "You do not have enough blocks!");
		    player.sendMessage(ChatColor.RED + "Blocks available: " + players.getBlockBalance(playerUUID));
		    player.sendMessage(ChatColor.RED + "Blocks required: " + blocksRequired);
		    return true;  
		}
		if (blocks < 2) {
		    player.sendMessage(ChatColor.RED + "The minimum radius is 2 blocks");
		    return true;		    
		}
		// Find the corners of this greenhouse
		Location pos1 = new Location(player.getWorld(),player.getLocation().getBlockX()-blocks,0,player.getLocation().getBlockZ()-blocks);
		Location pos2 = new Location(player.getWorld(),player.getLocation().getBlockX()+blocks,0,player.getLocation().getBlockZ()+blocks);
		if (!plugin.checkGreenhouseIntersection(pos1, pos2)) {
		    plugin.createNewGreenhouse(pos1, pos2, player);
		    players.removeBlocks(playerUUID, blocksRequired);
		    player.sendMessage(ChatColor.GOLD + "Greenhouse created!");
		    player.sendMessage(ChatColor.GOLD + "You have " + players.getBlockBalance(playerUUID) + " blocks left.");
		    players.save(playerUUID);
		} else {
		    player.sendMessage(ChatColor.RED + "That sized greenhouse could not be made because it overlaps another greenhouse");		    		    
		}
		return true;
	    } else if (split[0].equalsIgnoreCase("sell")) { 
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (d.getOwner().equals(playerUUID)) {
			// Check to see if it is being rented right now
			if (d.getRenter() != null) {
			    player.sendMessage(ChatColor.RED + "The greenhouse is being rented at this time. Wait until the lease expires.");
			    return true;
			}
			double price = 0D;
			try {
			    price = Double.parseDouble(split[1]);
			} catch (Exception e) {
			    player.sendMessage(ChatColor.RED+"The price is invalid (must be >= "+ VaultHelper.econ.format(1D)+")");
			    return true;
			}
			if (price <1D) {
			    player.sendMessage(ChatColor.RED+"The price is invalid (must be >= "+ VaultHelper.econ.format(1D)+")");
			    return true;  
			}
			player.sendMessage(ChatColor.GOLD + "Putting greenhouse up for sale for " + VaultHelper.econ.format(price));
			d.setForSale(true);
			d.setPrice(price);
			d.setForRent(false);
			return true;
		    }
		    player.sendMessage(ChatColor.RED + "This is not your greenhouse!");
		} else {
		    player.sendMessage(ChatColor.RED + "You are not in a greenhouse!"); 
		}
		return true;

	    } else if (split[0].equalsIgnoreCase("rent")) { 
		GreenhouseRegion d = players.getInGreenhouse(playerUUID);
		if (d != null) {
		    if (d.getOwner().equals(playerUUID)) {
			// Check to see if it is being rented right now
			if (d.getRenter() != null) {
			    player.sendMessage(ChatColor.RED+"The greenhouse is currently rented!");
			    player.sendMessage(ChatColor.RED+"To end the renter's lease at the next due date, use /d cancel.");
			    return true;
			}
			double price = 0D;
			try {
			    price = Double.parseDouble(split[1]);
			} catch (Exception e) {
			    player.sendMessage(ChatColor.RED+"The rent is invalid (must be >= "+ VaultHelper.econ.format(1D)+")");
			    return true;
			}
			if (price <1D) {
			    player.sendMessage(ChatColor.RED+"The rent is invalid (must be >= "+ VaultHelper.econ.format(1D)+")");
			    return true;  
			}
			player.sendMessage(ChatColor.GOLD + "Putting greenhouse up for rent for " + VaultHelper.econ.format(price));
			d.setForRent(true);
			d.setForSale(false);
			d.setPrice(price);
			return true;
		    }
		    player.sendMessage(ChatColor.RED + "This is not your greenhouse!");
		} else {
		    player.sendMessage(ChatColor.RED + "You are not in a greenhouse!"); 
		}
		return true;
	    }

	}
	return false;
    }
}
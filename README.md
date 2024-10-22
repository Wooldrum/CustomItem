# ItemAtLocation

A custom item plugin used by the Realm of Elderna Minecraft Server

I do not recommend you use this plugin, as it is very not good, but if there is no other option, then feel free to use it.

## Commands

The commands are as follows (by default only usable by players with administrator permissions):

### /itematlocation setlocation
Sets a target location to which players need to move in order to receive an item.

**Usage:** `/itematlocation setlocation <name> <x> <y> <z>` (can also use `~` for relative coordinates).

### /itematlocation setitem
Sets the type of item that will be given to players at the specified location.

**Usage:** `/itematlocation setitem <location> <item>`

### /itematlocation setcooldown
Sets the cooldown time in seconds for how often a player can receive the specified item at the specified location.

**Usage:** `/itematlocation setcooldown <location> <seconds>`

### /itematlocation setradius
Sets the radius in blocks within which players will receive the item at the specified location.

**Usage:** `/itematlocation setradius <location> <radius>`

### /itematlocation removelocation
Removes the previously set target location so that no item is given.

**Usage:** `/itematlocation removelocation <name>`

### /itematlocation listlocations
Lists all configured locations, including their coordinates, item, cooldown, and radius.

**Usage:** `/itematlocation listlocations`

### /itematlocation teleport
Teleports the player to the specified location.

**Usage:** `/itematlocation teleport <location>`

### /itematlocation resetcooldown
Resets the cooldown for the specified location, allowing players to receive the reward item again immediately.

**Usage:** `/itematlocation resetcooldown <location>`

### /itematlocation disablelocation
Disables the specified location, preventing players from receiving the reward.

**Usage:** `/itematlocation disablelocation <location>`

### /itematlocation enablelocation
Enables the specified location, allowing players to receive the item.

**Usage:** `/itematlocation enablelocation <location>`

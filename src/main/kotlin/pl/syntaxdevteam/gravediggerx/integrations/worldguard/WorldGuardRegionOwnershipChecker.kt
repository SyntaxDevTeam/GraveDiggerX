package pl.syntaxdevteam.gravediggerx.integrations.worldguard

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.Flags
import org.bukkit.Location
import org.bukkit.entity.Player
import pl.syntaxdevteam.gravediggerx.integrations.RegionOwnershipChecker

class WorldGuardRegionOwnershipChecker : RegionOwnershipChecker {

    override fun canPlaceGrave(player: Player, location: Location): Boolean {
        val world = location.world ?: return false
        val worldEditWorld = BukkitAdapter.adapt(world)
        val regionManager = WorldGuard.getInstance().platform.regionContainer.get(worldEditWorld) ?: return true

        val position = BlockVector3.at(location.blockX, location.blockY, location.blockZ)
        val regions = regionManager.getApplicableRegions(position)
        if (regions.regions.isEmpty()) {
            return true
        }

        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val weLocation = BukkitAdapter.adapt(location)

        for (region in regions.regions) {
            val hasExplicitOwnership = region.owners.size() > 0 || region.members.size() > 0
            if (hasExplicitOwnership) {
                val isRegionOwnerOrMember = region.isOwner(localPlayer) || region.isMember(localPlayer)
                if (!isRegionOwnerOrMember) {
                    return false
                }
            } else {
                val canBuildState = query.testState(weLocation, localPlayer, Flags.BUILD)
                if (canBuildState == false) {
                    return false
                }
            }
        }

        return true
    }
}
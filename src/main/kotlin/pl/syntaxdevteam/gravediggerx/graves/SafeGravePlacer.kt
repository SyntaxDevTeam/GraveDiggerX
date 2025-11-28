package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Location
import org.bukkit.Material

/**
 * Safe grave placement helper.
 * Finds a nearby safe location for a grave to avoid lava/water, steep drops, and crowding.
 */
object SafeGravePlacer {

    /**
     * Attempts to find a safe location near [base]. Returns null if none found within [radius].
     *
     * Safety rules:
     * - Target block must be replaceable (air or non-solid, except water/lava).
     * - Block below must be solid ground (not air, not water/lava).
     * - No lava/water at target or below.
     * - No steep drop (continuous air below greater than [maxDrop] blocks; fixed at 2).
     * - Keep distance from existing graves using [hasNearbyGrave] with min distance 2 blocks.
     */
    fun findSafeLocationNear(
        base: Location,
        radius: Int,
        maxVerticalScan: Int,
        hasNearbyGrave: (target: Location, minDistanceBlocks: Int) -> Boolean
    ): Location? {
        val world = base.world ?: return null
        val baseX = base.blockX
        val baseY = base.blockY
        val baseZ = base.blockZ

        adjustYToGround(Location(world, baseX.toDouble(), baseY.toDouble(), baseZ.toDouble()), maxVerticalScan)?.let { cand ->
            if (isSafeSpot(cand, hasNearbyGrave)) return cand
        }

        for (r in 1..radius) {
            for (dx in -r..r) {
                val x = baseX + dx
                val z = baseZ - r
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave)) return cand
                }
            }
            for (dz in (-r + 1)..r) {
                val x = baseX + r
                val z = baseZ + dz
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave)) return cand
                }
            }
            for (dx in (r - 1) downTo -r) {
                val x = baseX + dx
                val z = baseZ + r
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave)) return cand
                }
            }
            for (dz in (r - 1) downTo (-r + 1)) {
                val x = baseX - r
                val z = baseZ + dz
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave)) return cand
                }
            }
        }
        return null
    }

    fun findSafeLocationNether(
        base: Location,
        radius: Int,
        hasNearbyGrave: (target: Location, minDistanceBlocks: Int) -> Boolean
    ): Location? {
        val world = base.world ?: return null
        val baseX = base.blockX
        val baseY = base.blockY
        val baseZ = base.blockZ

        for (y in baseY downTo world.minHeight) {
            val loc = Location(world, baseX.toDouble(), y.toDouble(), baseZ.toDouble())
            if (isSafeSpotNether(loc, hasNearbyGrave)) return loc
        }
        for (y in baseY + 1 until world.maxHeight) {
            val loc = Location(world, baseX.toDouble(), y.toDouble(), baseZ.toDouble())
            if (isSafeSpotNether(loc, hasNearbyGrave)) return loc
        }


        for (r in 1..radius) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (dx != r && dx != -r && dz != r && dz != -r) continue

                    val x = baseX + dx
                    val z = baseZ + dz

                    for (y in baseY downTo world.minHeight) {
                        val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                        if (isSafeSpotNether(loc, hasNearbyGrave)) return loc
                    }
                    for (y in baseY + 1 until world.maxHeight) {
                        val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                        if (isSafeSpotNether(loc, hasNearbyGrave)) return loc
                    }
                }
            }
        }

        return null
    }

    private fun isSafeSpotNether(target: Location, hasNearbyGrave: (Location, Int) -> Boolean): Boolean {
        val world = target.world ?: return false
        val block = world.getBlockAt(target)
        val below = block.getRelative(0, -1, 0)
        val above = block.getRelative(0, 1, 0)

        if (!isReplaceable(block.type) || !isReplaceable(above.type)) return false
        if (!isSolidGround(below.type)) return false
        if (isLavaOrLiquid(block.type) || isLavaOrLiquid(below.type)) return false
        if (hasNearbyGrave(target, 2)) return false
        val below2 = block.getRelative(0, -2, 0)
        if (isLavaOrLiquid(below2.type)) return false

        return true
    }

    private fun adjustYToGround(loc: Location, maxVerticalScan: Int): Location? {
        val world = loc.world ?: return null
        val x = loc.blockX
        val y = loc.blockY
        val z = loc.blockZ

        for (dy in 0..maxVerticalScan) {
            val cy = y - dy
            if (cy < world.minHeight) break
            val candidate = Location(world, x + 0.0, cy + 0.0, z + 0.0)
            if (isPlaceableOn(candidate)) return candidate
        }
        for (dy in 1..maxVerticalScan) {
            val cy = y + dy
            if (cy > world.maxHeight - 1) break
            val candidate = Location(world, x + 0.0, cy + 0.0, z + 0.0)
            if (isPlaceableOn(candidate)) return candidate
        }
        return null
    }

    private fun isPlaceableOn(target: Location): Boolean {
        val world = target.world ?: return false
        val block = world.getBlockAt(target)
        val below = block.getRelative(0, -1, 0)
        return isReplaceable(block.type) && isSolidGround(below.type) && !isLavaOrLiquid(block.type) && !isLavaOrLiquid(below.type)
    }

    private fun isSafeSpot(target: Location, hasNearbyGrave: (Location, Int) -> Boolean): Boolean {
        val world = target.world ?: return false
        val block = world.getBlockAt(target)
        val below = block.getRelative(0, -1, 0)
        if (!isReplaceable(block.type)) return false
        if (!isSolidGround(below.type)) return false
        if (isLavaOrLiquid(block.type) || isLavaOrLiquid(below.type)) return false
        if (hasSteepDrop(target, 2)) return false
        if (hasNearbyGrave(target, 2)) return false
        return true
    }

    private fun isReplaceable(type: Material): Boolean {
        return type.isAir || (!type.isSolid && type != Material.WATER && type != Material.LAVA)
    }

    private fun isSolidGround(type: Material): Boolean {
        if (type.isAir) return false
        if (type == Material.LAVA || type == Material.WATER) return false
        return type.isSolid
    }

    private fun isLavaOrLiquid(type: Material): Boolean {
        return type == Material.LAVA || type == Material.WATER
    }

    private fun hasSteepDrop(target: Location, maxDrop: Int): Boolean {
        val world = target.world ?: return true
        val x = target.blockX
        val z = target.blockZ
        var depth = 0
        for (i in 1..(maxDrop + 1)) {
            val by = target.blockY - i
            if (by < world.minHeight) break
            val b = world.getBlockAt(x, by, z)
            if (!b.type.isSolid) {
                depth++
                continue
            }
            break
        }
        return depth > maxDrop
    }
}
package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Location
import org.bukkit.Material

/**
 * Pomocnik do bezpiecznego umieszczania grobów.
 *
 * Znajduje pobliską, bezpieczną lokalizację dla grobu z uwzględnieniem:
 * - miejsce docelowe musi być wymienialne (powietrze lub blok nie-statyczny; wykluczone są woda i lawa),
 * - blok poniżej musi być solidnym gruntem (nie powietrze i nie płyny),
 * - brak lawy/wody na miejscu docelowym i poniżej,
 * - brak stromego urwiska (ciągłe powietrze poniżej większe niż dopuszczalny spadek; domyślnie 2),
 * - unikanie istniejących grobów (wywoływana jest przekazana funkcja `hasNearbyGrave` z minimalnym dystansem 2).
 */
object SafeGravePlacer {

    /**
     * Przeszukuje obszar wokół [base] w promieniu [radius], próbując znaleźć najbliższe bezpieczne miejsce
     * w normalnym świecie (Overworld).
     *
     * Skanowanie poziome wykonane jest w spirali od środka na zewnątrz. Dla każdej pozycji wykonywany jest
     * pionowy skan w dół, a następnie w górę do [maxVerticalScan] bloków za pomocą [adjustYToGround].
     *
     * @param base punkt startowy
     * @param radius maksymalny promień przeszukiwania (poziomo)
     * @param maxVerticalScan maksymalny dystans pionowy do przeszukania (w dół oraz w górę) przy dopasowaniu wysokości
     * @param hasNearbyGrave funkcja sprawdzająca obecność istniejącego grobu w zadanym promieniu; powinna zwracać `true` gdy grób jest zbyt blisko
     * @return pierwsza znaleziona bezpieczna `Location` lub `null` jeśli żadna nie została znaleziona
     */
    fun findSafeLocationNear(
        base: Location,
        radius: Int,
        maxVerticalScan: Int,
        hasNearbyGrave: (target: Location, minDistanceBlocks: Int) -> Boolean,
        isAllowedLocation: (target: Location) -> Boolean = { true }
    ): Location? {
        val world = base.world ?: return null
        val baseX = base.blockX
        val baseY = base.blockY
        val baseZ = base.blockZ

        adjustYToGround(Location(world, baseX.toDouble(), baseY.toDouble(), baseZ.toDouble()), maxVerticalScan)?.let { cand ->
            if (isSafeSpot(cand, hasNearbyGrave, isAllowedLocation)) return cand
        }

        for (r in 1..radius) {
            for (dx in -r..r) {
                val x = baseX + dx
                val z = baseZ - r
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave, isAllowedLocation)) return cand
                }
            }
            for (dz in (-r + 1)..r) {
                val x = baseX + r
                val z = baseZ + dz
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave, isAllowedLocation)) return cand
                }
            }
            for (dx in (r - 1) downTo -r) {
                val x = baseX + dx
                val z = baseZ + r
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave, isAllowedLocation)) return cand
                }
            }
            for (dz in (r - 1) downTo (-r + 1)) {
                val x = baseX - r
                val z = baseZ + dz
                adjustYToGround(Location(world, x + 0.0, baseY + 0.0, z + 0.0), maxVerticalScan)?.let { cand ->
                    if (isSafeSpot(cand, hasNearbyGrave, isAllowedLocation)) return cand
                }
            }
        }
        return null
    }

    /**
     * Przeszukuje obszar wokół [base] w Netherze (obszar wertykalny).
     *
     * Dla Nether-a wykonywane jest najpierw skanowanie w dół z [`base.blockY`] aż do [`world.minHeight`],
     * potem w górę aż do [`world.maxHeight`]. Następnie ta sama procedura jest wykonywana dla kolejnych pozycji
     * w obrębie kwadratowego pierścienia do [radius].
     *
     * @param base punkt startowy
     * @param radius maksymalny promień przeszukiwania (poziomo)
     * @param hasNearbyGrave funkcja sprawdzająca obecność istniejącego grobu w zadanym promieniu; powinna zwracać `true` gdy grób jest zbyt blisko
     * @return pierwsza znaleziona bezpieczna `Location` w Netherze lub `null` jeśli żadna nie została znaleziona
     */

    //TODO: Sprawdzić czy ta metoda w ogóle jest potrzebna skoro nie jest używana
    @Suppress("unused")
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

    /**
     * Sprawdza bezpieczeństwo pozycji w Netherze.
     *
     * W Netherze dodatkowo sprawdzane jest, czy blok nad celem jest wymienialny (aby grob mógł się zmieścić).
     * Pozostałe warunki: miejsce i blok poniżej są wymienialne/solidne odpowiednio, brak płynów/lawy oraz brak pobliskiego grobu.
     */
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
        return !isLavaOrLiquid(below2.type)
    }

    /**
     * Dla pozycji [loc] szuka najbliższej wysokości, na której można postawić grób.
     *
     * Skanuje w pierwszej kolejności w dół (0..maxVerticalScan), potem w górę (1..maxVerticalScan).
     * Zwraca `Location` wyrównaną do bloku (y jako int) lub `null` gdy brak odpowiedniej wysokości w zakresie.
     */
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

    /**
     * Sprawdza podstawowe warunki "postawienia" na danej lokalizacji:
     * - blok docelowy jest wymienialny,
     * - blok poniżej jest solidnym gruntem,
     * - ani cel ani blok poniżej nie są płynami (woda/lawa).
     */
    private fun isPlaceableOn(target: Location): Boolean {
        val world = target.world ?: return false
        val block = world.getBlockAt(target)
        val below = block.getRelative(0, -1, 0)
        return isReplaceable(block.type) && isSolidGround(below.type) && !isLavaOrLiquid(block.type) && !isLavaOrLiquid(below.type)
    }

    /**
     * Sprawdza pełną "bezpieczeństwo" pozycji w Overworld:
     * - miejsce wymienialne,
     * - podstawa solidna,
     * - brak płynów,
     * - brak stromego urwiska (max drop 2),
     * - brak pobliskiego grobu (min distance 2).
     */
    private fun isSafeSpot(
        target: Location,
        hasNearbyGrave: (Location, Int) -> Boolean,
        isAllowedLocation: (Location) -> Boolean
    ): Boolean {
        val world = target.world ?: return false
        val block = world.getBlockAt(target)
        val below = block.getRelative(0, -1, 0)
        if (!isReplaceable(block.type)) return false
        if (!isSolidGround(below.type)) return false
        if (isLavaOrLiquid(block.type) || isLavaOrLiquid(below.type)) return false
        if (hasSteepDrop(target, 2)) return false
        if (hasNearbyGrave(target, 2)) return false
        if (!isAllowedLocation(target)) return false
        return true
    }

    /**
     * Czy typ bloku jest wymienialny (powietrze lub nie-solidny, ale nie woda ani lawa).
     */
    private fun isReplaceable(type: Material): Boolean {
        return type.isAir || (!type.isSolid && type != Material.WATER && type != Material.LAVA)
    }

    /**
     * Czy typ bloku jest uznawany za solidny grunt (nie powietrze, nie płyny, i isSolid).
     */
    private fun isSolidGround(type: Material): Boolean {
        if (type.isAir) return false
        if (type == Material.LAVA || type == Material.WATER) return false
        return type.isSolid
    }

    /**
     * Czy typ bloku to lawa lub woda.
     */
    private fun isLavaOrLiquid(type: Material): Boolean {
        return type == Material.LAVA || type == Material.WATER
    }

    /**
     * Sprawdza czy poniżej [target] występuje ciągłe powietrze większe niż [maxDrop] (oznacza to strome urwisko).
     *
     * Zwraca `true` jeśli wykryto spadek większy niż [maxDrop], `false` w przeciwnym razie.
     */
    private fun hasSteepDrop(target: Location, @Suppress("SameParameterValue") maxDrop: Int): Boolean {
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
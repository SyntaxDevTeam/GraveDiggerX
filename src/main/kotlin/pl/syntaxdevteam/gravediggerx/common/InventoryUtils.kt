package pl.syntaxdevteam.gravediggerx.common

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun Player.addItemOrDrop(item: ItemStack) {
    if (item.type == Material.AIR) return

    val leftovers = inventory.addItem(item).values
    if (leftovers.isNotEmpty()) {
        leftovers.forEach { world.dropItemNaturally(location, it) }
    }
}

fun Player.equipSafely(current: ItemStack?, replacement: ItemStack, apply: (ItemStack) -> Unit) {
    if (replacement.type == Material.AIR) return

    current?.takeIf { it.type != Material.AIR }?.let { addItemOrDrop(it) }
    apply(replacement)
}

package pl.syntaxdevteam.gravediggerx.graves.collection

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.gravediggerx.common.addItemOrDrop
import pl.syntaxdevteam.gravediggerx.graves.Grave

object GraveItemTransfer {
    fun transferTo(player: Player, grave: Grave) {
        grave.items.filterKeys { it in 0..35 }.values.forEach(player::addItemOrDrop)
        equipOrAdd(player, grave.armorContents["helmet"], player.inventory.helmet) { player.inventory.setHelmet(it) }
        equipOrAdd(player, grave.armorContents["chestplate"], player.inventory.chestplate) { player.inventory.setChestplate(it) }
        equipOrAdd(player, grave.armorContents["leggings"], player.inventory.leggings) { player.inventory.setLeggings(it) }
        equipOrAdd(player, grave.armorContents["boots"], player.inventory.boots) { player.inventory.setBoots(it) }
        equipOrAdd(player, grave.armorContents["offhand"], player.inventory.itemInOffHand) { player.inventory.setItemInOffHand(it) }
        if (grave.storedXp > 0) player.giveExp(grave.storedXp)
    }

    private fun equipOrAdd(player: Player, stored: ItemStack?, current: ItemStack, equip: (ItemStack) -> Unit) {
        if (stored == null || stored.type == Material.AIR) return
        if (current.type == Material.AIR) equip(stored) else player.addItemOrDrop(stored)
    }
}

package com.sneakyposes

import com.sneakyposes.commands.SitCommand
import com.sneakyposes.listeners.SitBlockClickListener
import com.sneakyposes.util.PoseData
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import com.sneakyposes.util.SitClickRules
import java.util.UUID
import java.util.function.Consumer
import java.util.logging.Logger
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Slab
import org.bukkit.command.CommandMap
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.PluginManager
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

class SitRotationTest {
    private lateinit var player: Player
    private lateinit var seat: ArmorStand
    private lateinit var tick: Runnable
    private lateinit var seatWorld: World

    @BeforeEach
    fun startPluginSynchronization() {
        val plugin = mock(SneakyPoses::class.java)
        val server = mock(Server::class.java)
        val scheduler = mock(BukkitScheduler::class.java)
        val commands = mock(CommandMap::class.java)
        val plugins = mock(PluginManager::class.java)
        `when`(plugin.server).thenReturn(server)
        `when`(plugin.config).thenReturn(YamlConfiguration())
        `when`(plugin.logger).thenReturn(Logger.getAnonymousLogger())
        `when`(server.scheduler).thenReturn(scheduler)
        `when`(server.commandMap).thenReturn(commands)
        `when`(server.pluginManager).thenReturn(plugins)
        `when`(server.worlds).thenReturn(emptyList())
        `when`(commands.knownCommands).thenReturn(mutableMapOf())
        doAnswer { invocation ->
            tick = invocation.getArgument(1)
            null
        }.`when`(scheduler).runTaskTimer(eq(plugin), any(Runnable::class.java), eq(1L), eq(1L))
        doCallRealMethod().`when`(plugin).onEnable()
        plugin.onEnable()

        player = mock(Player::class.java)
        seat = mock(ArmorStand::class.java)
        `when`(player.uniqueId).thenReturn(UUID.randomUUID())
        `when`(seat.uniqueId).thenReturn(UUID.randomUUID())
        `when`(server.getPlayer(player.uniqueId)).thenReturn(player)
        `when`(player.vehicle).thenReturn(seat)
        `when`(player.location).thenReturn(Location(null, 1.0, 64.0, 2.0, 125f, 45f))
    }

    @AfterEach
    fun removePose() {
        if (::player.isInitialized) PoseManager.removePose(player)
    }

    private fun allowSeatSpawn() {
        seatWorld = mock(World::class.java)
        `when`(player.location).thenReturn(Location(seatWorld, 1.0, 64.0, 2.0, 125f, 45f))
        `when`(player.hasPermission("sneakyposes.sit")).thenReturn(true)
        doReturn(seat).`when`(seatWorld).spawn(
            any(Location::class.java), eq(ArmorStand::class.java), any<Consumer<ArmorStand>>()
        )
    }

    @Test
    fun `right click slab seat follows yaw on the scheduled tick`() {
        allowSeatSpawn()
        val config = YamlConfiguration()
        config.set("sit.click-blocks", listOf(mapOf("material" to "OAK_SLAB", "y-offset" to 0.3)))
        SitClickRules.reload(config)
        val inventory = mock(PlayerInventory::class.java)
        val hand = mock(ItemStack::class.java)
        `when`(player.inventory).thenReturn(inventory)
        `when`(inventory.itemInMainHand).thenReturn(hand)
        // Material.isAir consults Paper's runtime block registry; stub only that boundary.
        val air = mock(Material::class.java)
        `when`(air.isAir).thenReturn(true)
        `when`(hand.type).thenReturn(air)
        val block = mock(Block::class.java)
        val above = mock(Block::class.java)
        val slab = mock(Slab::class.java)
        `when`(block.type).thenReturn(Material.OAK_SLAB)
        val world = player.location.world
        `when`(block.world).thenReturn(world)
        `when`(block.getRelative(BlockFace.UP)).thenReturn(above)
        `when`(above.type).thenReturn(air)
        `when`(block.blockData).thenReturn(slab)
        `when`(slab.type).thenReturn(Slab.Type.BOTTOM)
        val event = PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND)

        SitBlockClickListener().onRightClickBlock(event)
        org.junit.jupiter.api.Assertions.assertEquals(PoseType.SIT, PoseManager.getPose(player)?.type)
        verify(seat).addPassenger(player)
        tick.run()
        verify(seat).setRotation(125f, 0f)
    }

    @Test
    fun `sit command seat follows yaw on the scheduled tick`() {
        allowSeatSpawn()
        SitCommand().execute(player, "sit", emptyArray())
        verify(seat).addPassenger(player)
        tick.run()
        verify(seat).setRotation(125f, 0f)
    }

    @Test
    fun `foreign vehicle is not rotated`() {
        PoseManager.setPose(player, PoseData(PoseType.SIT, player.location, setOf(UUID.randomUUID())))
        tick.run()
        verify(seat, never()).setRotation(anyFloat(), anyFloat())
    }

    @Test
    fun `dismounted player is ignored`() {
        PoseManager.setPose(player, PoseData(PoseType.SIT, player.location, setOf(seat.uniqueId)))
        `when`(player.vehicle).thenReturn(null)
        tick.run()
        verify(seat, never()).setRotation(anyFloat(), anyFloat())
    }

    @Test
    fun `sleep camera vehicle is not rotated`() {
        PoseManager.setPose(player, PoseData(PoseType.SLEEP, player.location, setOf(seat.uniqueId)))
        tick.run()
        verify(seat, never()).setRotation(anyFloat(), anyFloat())
    }

    @Test
    fun `removed pose no longer rotates seat`() {
        PoseManager.setPose(player, PoseData(PoseType.SIT, player.location, setOf(seat.uniqueId)))
        PoseManager.removePose(player)
        tick.run()
        verify(seat, never()).setRotation(anyFloat(), anyFloat())
    }

    @Test
    fun `scheduled synchronization follows seated player yaw each tick`() {
        PoseManager.setPose(player, PoseData(PoseType.SIT, player.location, setOf(seat.uniqueId)))

        tick.run()
        verify(seat).setRotation(125f, 0f)

        `when`(player.location).thenReturn(Location(null, 1.0, 64.0, 2.0, -170f, -80f))
        tick.run()
        verify(seat).setRotation(-170f, 0f)
        verify(seat, never()).teleport(any(Location::class.java))
    }
}

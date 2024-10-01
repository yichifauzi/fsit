package dev.rvbsm.fsit.client

import com.mojang.serialization.Codec
import dev.rvbsm.fsit.FSitMod
import dev.rvbsm.fsit.client.command.command
import dev.rvbsm.fsit.client.config.RestrictionList
import dev.rvbsm.fsit.client.network.ClientConnectionListener
import dev.rvbsm.fsit.client.network.FSitClientNetworking
import dev.rvbsm.fsit.client.option.FSitKeyBindings
import dev.rvbsm.fsit.client.option.KeyBindingMode
import dev.rvbsm.fsit.compat.CustomPayload
import dev.rvbsm.fsit.network.packet.ConfigUpdateC2SPayload
import dev.rvbsm.fsit.util.literal
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.option.SimpleOption
import net.minecraft.command.argument.GameProfileArgumentType
import org.slf4j.LoggerFactory

object FSitModClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger(FSitModClient::class.java)

    @JvmStatic
    val isServerFSitCompatible get() = ClientPlayNetworking.canSend(ConfigUpdateC2SPayload.packetId)

    @JvmStatic
    val sitMode = SimpleOption(
        "key.fsit.sit",
        SimpleOption.emptyTooltip(),
        SimpleOption.enumValueText(),
        SimpleOption.PotentialValuesBasedCallbacks(
            KeyBindingMode.entries,
            Codec.INT.xmap(KeyBindingMode::byId, KeyBindingMode::getId),
        ),
        KeyBindingMode.Hybrid,
    ) {}

    @JvmStatic
    val crawlMode = SimpleOption(
        "key.fsit.crawl",
        SimpleOption.emptyTooltip(),
        SimpleOption.enumValueText(),
        SimpleOption.PotentialValuesBasedCallbacks(
            KeyBindingMode.entries,
            Codec.INT.xmap(KeyBindingMode::byId, KeyBindingMode::getId),
        ),
        KeyBindingMode.Hybrid,
    ) {}

    override fun onInitializeClient() {
        RestrictionList.load()

        FSitClientNetworking.register()
        FSitKeyBindings.register()

        ClientPlayConnectionEvents.JOIN.register(ClientConnectionListener)
        registerClientCommands()
    }

    fun saveConfig() = FSitMod.saveConfig().also {
        trySend(ConfigUpdateC2SPayload(FSitMod.config))
    }

    fun <T> trySend(payload: T, orAction: () -> Unit = {}) where T : CustomPayload {
        if (ClientPlayNetworking.canSend(payload.id)) {
            ClientPlayNetworking.send(payload)
        } else orAction()
    }

    // todo: mm spaghetti
    private fun registerClientCommands() {
        command("${FSitMod.MOD_ID}:client") {
            setOf("allow" to RestrictionList::addOrThrow, "restrict" to RestrictionList::removeOrThrow).forEach { cmd ->
                literal(cmd.first) {
                    argument("player", { source.playerNames }) { playerName ->
                        executes {
                            val entry = source.client.networkHandler?.getPlayerListEntry(playerName())
                                ?: throw GameProfileArgumentType.UNKNOWN_PLAYER_EXCEPTION.create()

                            if (cmd.second(entry.profile.id)) {
                                // todo: improve this piece of
                                source.sendFeedback("Successfully ${cmd.first.replaceFirstChar { it.uppercase() }}ed ${entry.displayName}".literal())
                            }
                        }
                    }
                }
            }
        }
    }
}

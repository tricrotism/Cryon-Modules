package com.tricrotism.cryon.visibility.vanish

import com.tricrotism.cryon.common.data.Database
import com.tricrotism.cryon.common.net.Messenger
import com.tricrotism.cryon.common.net.MessengerSubscription
import com.tricrotism.cryon.paper.api.scheduler.Schedulers
import org.slf4j.Logger
import java.util.UUID

/**
 * The vanish backend: durable persistence (SQL) and cross-server propagation (Redis), each used **only
 * when that infrastructure is connected** — both are nullable and the store no-ops the parts it lacks.
 * The module only creates a store when at least one is present.
 *
 * - **SQL** (source of truth) — loaded into the [Vanish] set on start (fixes a server booting *after* a
 *   toggle), and written on every local toggle, so vanish survives restarts.
 * - **Redis** — publishes local toggles and applies inbound ones live, so vanish is consistent across
 *   servers while they're up.
 *
 * Only the originating server writes SQL / publishes (via [Vanish.onLocalChange]); peers just apply the
 * in-memory change. Inbound Redis messages and SQL loads land off the main thread, so application hops
 * to it.
 */
class VanishStore(
    private val vanish: Vanish,
    private val messenger: Messenger?,
    private val database: Database?,
    private val logger: Logger,
) {
    private val nodeId = UUID.randomUUID().toString()
    private var subscription: MessengerSubscription? = null

    fun start() {
        database?.let { db ->
            db.update("CREATE TABLE IF NOT EXISTS cryon_vanished (uuid VARCHAR(36) PRIMARY KEY)")
                .thenCompose { db.query("SELECT uuid FROM cryon_vanished") { it.getString(1) } }
                .thenAccept { rows -> Schedulers.global { rows.forEach(::applyLoaded) } }
                .exceptionally { logger.error("Failed to load vanished players from SQL", it); null }
        }
        subscription = messenger?.subscribe(CHANNEL, ::onMessage)
        vanish.onLocalChange(::onLocalChange)
    }

    fun stop() {
        vanish.onLocalChange(null)
        subscription?.unsubscribe()
        subscription = null
    }

    /** A toggle that happened on this server: persist it and broadcast it. */
    private fun onLocalChange(uuid: UUID, vanished: Boolean) {
        messenger?.publish(CHANNEL, "$nodeId:$uuid:${if (vanished) 1 else 0}")
        database?.let { db ->
            val write = if (vanished) {
                db.update("INSERT INTO cryon_vanished (uuid) VALUES (?) ON CONFLICT DO NOTHING", uuid.toString())
            } else {
                db.update("DELETE FROM cryon_vanished WHERE uuid = ?", uuid.toString())
            }
            write.exceptionally { logger.error("Failed to persist vanish for {}", uuid, it); null }
        }
    }

    private fun onMessage(payload: String) {
        val parts = payload.split(':')
        if (parts.size != 3 || parts[0] == nodeId) return // ignore malformed and our own echo
        val uuid = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return
        val vanished = parts[2] == "1"
        Schedulers.global { vanish.applyNetwork(uuid, vanished) }
    }

    private fun applyLoaded(raw: String) {
        val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return
        vanish.applyNetwork(uuid, true)
    }

    private companion object {
        const val CHANNEL = "cryon:vanish"
    }
}

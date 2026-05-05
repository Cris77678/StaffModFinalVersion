package me.drex.staffmod.punishment;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ExpirationTask implements Runnable {

    private final MinecraftServer server;

    public ExpirationTask(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        // ── Jugadores ONLINE: mute, jail, ban ────────────────────────────
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData pd = DataStore.get(player.getUUID());
            if (pd == null) continue;

            // Mute expirado online
            if (pd.muted && pd.muteExpiry != -1 && now >= pd.muteExpiry) {
                pd.muted = false;
                pd.muteExpiry = -1;
                DataStore.saveAsync();
                server.execute(() ->
                    player.sendSystemMessage(Component.literal(
                        "§a[sᴛᴀꜰꜰ] Tu silencio ha expirado. Ya puedes hablar de nuevo.")));
            }

            // Jail expirado online
            if (pd.jailed && pd.jailExpiry != -1 && now >= pd.jailExpiry) {
                pd.jailed        = false;
                pd.jailName      = "";
                pd.jailExpiry    = -1;   // BUG FIX: limpiar el timestamp sucio
                pd.pendingUnjail = false;
                DataStore.saveAsync();
                server.execute(() -> {
                    var overworld = server.overworld();
                    var spawn = overworld.getSharedSpawnPos();
                    player.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(),
                        player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal(
                        "§a[sᴛᴀꜰꜰ] Has cumplido tu tiempo en prisión. Eres libre."));
                });
            }

            // Ban expirado online
            if (pd.banned && pd.banExpiry != -1 && now >= pd.banExpiry) {
                pd.banned = false;
                pd.banExpiry = -1;
                DataStore.saveAsync();
            }
        }

        // ── BUG #5 FIX: Jugadores OFFLINE — detectar jails expirados ─────
        // ExpirationTask antes solo procesaba jugadores online.
        // Si alguien estaba en cárcel y se desconectó, nadie marcaba
        // pendingUnjail=true. Al reconectarse, applyOnJoin() lo mandaba
        // de vuelta a la cárcel en lugar de liberarlo.
        // Ahora iteramos TODOS los PlayerData y marcamos pendingUnjail para offline.
        for (PlayerData pd : DataStore.allPlayers()) {
            // Saltar jugadores que están online (ya procesados arriba)
            boolean isOnline = server.getPlayerList().getPlayer(pd.uuid) != null;
            if (isOnline) continue;

            // Mute expirado offline — solo limpiar estado, sin mensaje (reconectarán normal)
            if (pd.muted && pd.muteExpiry != -1 && now >= pd.muteExpiry) {
                pd.muted = false;
                pd.muteExpiry = -1;
                DataStore.saveAsync();
            }

            // Jail expirado offline — marcar pendingUnjail para que applyOnJoin() lo libere
            if (pd.jailed && pd.jailExpiry != -1 && now >= pd.jailExpiry) {
                pd.jailed        = false;
                pd.jailName      = "";
                pd.jailExpiry    = -1;
                pd.pendingUnjail = true;  // applyOnJoin() lo teletransportará al spawn
                DataStore.saveAsync();
            }

            // Ban expirado offline — limpiar para que pueda reconectarse
            if (pd.banned && pd.banExpiry != -1 && now >= pd.banExpiry) {
                pd.banned = false;
                pd.banExpiry = -1;
                DataStore.saveAsync();
            }
        }
    }
}

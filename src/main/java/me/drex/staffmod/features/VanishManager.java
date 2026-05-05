package me.drex.staffmod.features;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.logging.AuditLogManager;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    // BUG #8 FIX: El set de vanished era solo en memoria → se perdía en reinicio.
    // Ahora se persiste en DataStore (toggles.json) igual que staffChatToggle.
    // Este set en memoria se inicializa al cargar toggles en DataStore.load().
    private static final Set<UUID> vanished = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ── Acceso al set (para que DataStore pueda sincronizarlo) ──
    public static Set<UUID> getVanishedSet() {
        return vanished;
    }

    public static boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public static void toggleVanish(ServerPlayer staff) {
        UUID uuid = staff.getUUID();
        boolean isNowVanished = !vanished.contains(uuid);

        if (isNowVanished) {
            vanished.add(uuid);
            // Persistir inmediatamente en toggles.json
            DataStore.saveAsync();
            staff.setInvisible(true);
            hideFromOthers(staff);
            staff.sendSystemMessage(Component.literal("§a[ᴠᴀɴɪsʜ] Estás invisible y oculto del tabulador."));
            AuditLogManager.log(staff.getName().getString(), "VANISH_ON", "-", "Activó vanish");
        } else {
            vanished.remove(uuid);
            DataStore.saveAsync();
            staff.setInvisible(false);
            showToOthers(staff);
            staff.sendSystemMessage(Component.literal("§c[ᴠᴀɴɪsʜ] Vuelves a ser visible."));
            AuditLogManager.log(staff.getName().getString(), "VANISH_OFF", "-", "Desactivó vanish");
        }
    }

    private static void hideFromOthers(ServerPlayer staff) {
        var packet = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(staff.getUUID()));
        for (ServerPlayer p : staff.getServer().getPlayerList().getPlayers()) {
            if (!PermissionUtil.has(p, "staffmod.use") && !p.getUUID().equals(staff.getUUID())) {
                p.connection.send(packet);
            }
        }
    }

    private static void showToOthers(ServerPlayer staff) {
        var packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
            Collections.singletonList(staff));
        for (ServerPlayer p : staff.getServer().getPlayerList().getPlayers()) {
            if (!PermissionUtil.has(p, "staffmod.use") && !p.getUUID().equals(staff.getUUID())) {
                p.connection.send(packet);
            }
        }
    }

    /** Restaurar vanish al conectarse (funciona ahora porque el set persiste entre reinicios). */
    public static void applyVanishOnJoin(ServerPlayer staff) {
        if (!vanished.contains(staff.getUUID())) return;
        staff.setInvisible(true);
        hideFromOthers(staff);
        staff.sendSystemMessage(Component.literal("§7[ᴠᴀɴɪsʜ] Vanish restaurado automáticamente."));
    }
}

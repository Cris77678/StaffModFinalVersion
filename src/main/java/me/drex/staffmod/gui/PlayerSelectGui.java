package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;

public class PlayerSelectGui extends SimpleGui {

    private final ServerPlayer staff;
    private final StaffAction action;
    final SimpleGui parent;

    public PlayerSelectGui(ServerPlayer staff, StaffAction action, SimpleGui parent) {
        // BUG #1 FIX: playerCount-1 porque el staff se excluye del listado
        super(resolveSize(staff.getServer().getPlayerList().getPlayerCount() - 1), staff, false);
        this.staff  = staff;
        this.action = action;
        this.parent = parent;
        setTitle(Component.literal("§8» §6Selecciona jugador §7[" + action.name() + "]"));
        build();
    }

    private static MenuType<?> resolveSize(int count) {
        if (count <= 8)  return MenuType.GENERIC_9x1;
        if (count <= 17) return MenuType.GENERIC_9x2;
        if (count <= 26) return MenuType.GENERIC_9x3;
        if (count <= 35) return MenuType.GENERIC_9x4;
        if (count <= 44) return MenuType.GENERIC_9x5;
        return MenuType.GENERIC_9x6;
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        List<ServerPlayer> online = staff.getServer().getPlayerList().getPlayers();
        int slot = 0;

        for (ServerPlayer target : online) {
            if (slot >= getSize() - 1) break;
            if (target.getUUID().equals(staff.getUUID())) continue;

            boolean isProtected = PermissionUtil.isProtected(target);
            PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());

            GuiElementBuilder btn = isProtected
                ? new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                    .setName(Component.literal("§c" + target.getName().getString() + " §7[Protegido]"))
                : buildTargetSlot(target, pd);

            final ServerPlayer finalTarget = target;
            btn.setCallback((idx, type, clickAction, gui) -> {
                if (isProtected) {
                    staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] No puedes actuar sobre un administrador."));
                    return;
                }
                if (clickAction == ClickType.QUICK_MOVE) {
                    switch (action) {
                        case MUTE   -> ActionExecutor.mute(staff, finalTarget, "5m", "Mute rápido");
                        case BAN    -> ActionExecutor.ban(staff, finalTarget, "1d", "Ban rápido");
                        case WARN   -> ActionExecutor.warn(staff, finalTarget, "Comportamiento inadecuado");
                        case FREEZE -> ActionExecutor.freeze(staff, finalTarget);
                        default     -> {}
                    }
                    this.close();
                    new PlayerSelectGui(staff, action, parent).open();
                    return;
                }
                handleAction(finalTarget);
            });

            setSlot(slot++, btn.build());
        }

        setSlot(getSize() - 1, new GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("§7◄ Volver"))
            .setCallback((i, t, a, g) -> parent.open())
            .build());
    }

    private GuiElementBuilder buildTargetSlot(ServerPlayer target, PlayerData pd) {
        boolean muted  = pd.isMuteActive();
        boolean jailed = pd.isJailActive();
        boolean frozen = pd.frozen;
        boolean banned = pd.isBanActive();

        var icon = muted  ? Items.YELLOW_STAINED_GLASS_PANE
                 : jailed ? Items.ORANGE_STAINED_GLASS_PANE
                 : frozen ? Items.LIGHT_BLUE_STAINED_GLASS_PANE
                 : banned ? Items.RED_STAINED_GLASS_PANE
                 : Items.LIME_STAINED_GLASS_PANE;

        return new GuiElementBuilder(icon)
            .setName(Component.literal("§f§l" + target.getName().getString()))
            .addLoreLine(Component.literal("§7Ping: §f" + target.connection.latency() + "ms"))
            .addLoreLine(Component.literal("§7Estado: "
                + (muted  ? "§eMuteado " : "")
                + (jailed ? "§6Jaileado " : "")
                + (frozen ? "§bCongelado " : "")
                + (banned ? "§cBaneado " : "")
                + (!muted && !jailed && !frozen && !banned ? "§aNormal" : "")))
            .addLoreLine(Component.literal(" "))
            .addLoreLine(Component.literal("§eClick: §f" + action.name()))
            .addLoreLine(Component.literal("§bShift+Click: §fAcción rápida"));
    }

    private void handleAction(ServerPlayer target) {
        switch (action) {
            // BUG #2 FIX: KICK pide razón antes de ejecutar
            case KICK      -> new KickReasonGui(staff, target, this).open();
            case FREEZE    -> ActionExecutor.freeze(staff, target);
            case TELEPORT  -> ActionExecutor.teleport(staff, target);
            case KILL      -> ActionExecutor.kill(staff, target);
            case MUTE      -> new DurationReasonGui(staff, target, StaffAction.MUTE, this).open();
            case UNMUTE    -> { ActionExecutor.unmute(staff, target); parent.open(); }
            case JAIL      -> new JailSelectGui(staff, target, this).open();
            case UNJAIL    -> { ActionExecutor.unjail(staff, target); parent.open(); }
            case BAN       -> new DurationReasonGui(staff, target, StaffAction.BAN, this).open();
            case UNBAN     -> { ActionExecutor.unban(staff, target); parent.open(); }
            case WARN      -> new DurationReasonGui(staff, target, StaffAction.WARN, this).open();
            case SPY       -> ActionExecutor.spy(staff, target);
            case POKESPY   -> new CobblemonInspectorGui(staff, target).open();
        }
    }
}

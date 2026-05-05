package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.Kit;
import me.drex.staffmod.features.KitManager;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;

public class KitListGui extends PaginatedGui<Kit> {

    private final ServerPlayer staff;

    public KitListGui(ServerPlayer staff, SimpleGui parent) {
        super(staff, parent, Component.literal("§8❖ §eᴋɪᴛs ᴅᴇ sᴛᴀꜰꜰ §8❖"),
            new ArrayList<>(KitManager.getAllKits()));
        this.staff = staff;
        build();
    }

    @Override
    protected GuiElementBuilder buildItem(Kit kit) {
        var itemRes  = ResourceLocation.tryParse(kit.displayIconId != null ? kit.displayIconId : "minecraft:chest");
        var iconItem = BuiltInRegistries.ITEM.getOptional(itemRes).orElse(Items.CHEST);

        boolean hasPerm   = PermissionUtil.has(staff, kit.permissionNode);
        boolean onCooldown = KitManager.isOnCooldown(staff, kit);
        long remaining     = KitManager.getRemainingCooldown(staff, kit);

        GuiElementBuilder builder = new GuiElementBuilder(iconItem)
            .setName(Component.literal("§6§l" + kit.displayName))
            .addLoreLine(Component.literal("§8Permiso: " + kit.permissionNode))
            .addLoreLine(Component.literal(" "));

        if (!hasPerm) {
            builder.addLoreLine(Component.literal("§c§lBLOQUEADO"))
                   .addLoreLine(Component.literal("§cNo posees el rango necesario."));
        } else if (onCooldown) {
            long hours = remaining / 3_600_000L;
            long mins  = (remaining % 3_600_000L) / 60_000L;
            builder.addLoreLine(Component.literal("§e§lEN ESPERA"))
                   .addLoreLine(Component.literal("§eDisponible en: §f" + hours + "h " + mins + "m"));
        } else {
            builder.addLoreLine(Component.literal("§a§lDISPONIBLE"))
                   .addLoreLine(Component.literal("§aClick para reclamar."));
        }

        builder.setCallback((idx, type, action, gui) -> {
            if (!hasPerm) {
                staff.sendSystemMessage(Component.literal("§cNo tienes permiso para este kit."));
                return;
            }
            if (onCooldown) {
                staff.sendSystemMessage(Component.literal("§cEspera a que termine tu cooldown."));
                return;
            }
            var itemsToGive = KitManager.deserializeItems(
                kit.base64Inventory, 36, staff.serverLevel().registryAccess());
            for (ItemStack is : itemsToGive) {
                if (!is.isEmpty()) staff.getInventory().placeItemBackInInventory(is.copy());
            }
            KitManager.setCooldown(staff, kit);
            staff.sendSystemMessage(Component.literal("§a¡Reclamaste el kit §f" + kit.displayName + "§a!"));
            this.close();
            new KitListGui(staff, parent).open();
        });

        return builder;
    }

    @Override
    protected void build() {
        super.build();
        if (data.isEmpty()) {
            setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cNo hay kits configurados."))
                .addLoreLine(Component.literal("§7Usa /staffkit create para crear uno."))
                .build());
        }
    }
}

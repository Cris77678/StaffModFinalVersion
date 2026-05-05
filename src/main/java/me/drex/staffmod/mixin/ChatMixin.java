package me.drex.staffmod.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lógica de chat manejada por ServerMessageEvents en StaffMod.java.
 * Este archivo se mantiene para satisfacer el fabric.mod.json.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatMixin {
}

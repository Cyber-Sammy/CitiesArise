package com.cybersammy.citiesarise.mixin;

import com.cybersammy.citiesarise.minecraft.worldgen.MovementChunkReadiness;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge setPosRaw requires a FULL chunk synchronously; keep movement on the ready side of that boundary. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerMovementReadinessMixin {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true)
    private void citiesarise$readyPlayer(ServerboundMovePlayerPacket packet,CallbackInfo ci) {
        if(!player.serverLevel().getServer().isSameThread() || !packet.hasPosition() || !Float.isFinite(packet.getYRot(0)) || !Float.isFinite(packet.getXRot(0))
                || awaitingPositionFromClient!=null || player.isPassenger()
                || player.wonGame || player.isChangingDimension() || player.isSleeping())return;
        if(MovementChunkReadiness.hold(player,player,packet.getX(player.getX()),packet.getY(player.getY()),packet.getZ(player.getZ()))) {
            player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());ci.cancel();
        }
    }
    @Inject(method="handleMoveVehicle",at=@At("HEAD"),cancellable=true)
    private void citiesarise$readyVehicle(ServerboundMoveVehiclePacket packet,CallbackInfo ci) {
        if(!player.serverLevel().getServer().isSameThread() || !Float.isFinite(packet.getYRot()) || !Float.isFinite(packet.getXRot()))return;
        var root=player.getRootVehicle();if(root==player || root.getControllingPassenger()!=player)return;
        if(MovementChunkReadiness.hold(player,root,packet.getX(),packet.getY(),packet.getZ())) {
            player.connection.send(new ClientboundMoveVehiclePacket(root));ci.cancel();
        }
    }
}

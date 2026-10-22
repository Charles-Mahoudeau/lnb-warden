package fr.bde_teknantes.lnb.warden.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.bde_teknantes.lnb.warden.client.ClientRealNames;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws the player's real name under their name tag, and pushes the name tag up a line, like a below-name score. */
@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {
    @WrapOperation(
            method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
                    // 0 draws the below-name score, 1 the name.
                    ordinal = 1
            )
    )
    private void lnbwarden$renderRealName(
            PlayerRenderer renderer, Entity entity, Component displayName, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick, Operation<Void> original,
            AbstractClientPlayer player
    ) {
        Component realName = ClientRealNames.get(player.getUUID());
        if (realName != null) {
            original.call(renderer, entity, realName, poseStack, bufferSource, packedLight, partialTick);
            poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
        }
        original.call(renderer, entity, displayName, poseStack, bufferSource, packedLight, partialTick);
    }
}

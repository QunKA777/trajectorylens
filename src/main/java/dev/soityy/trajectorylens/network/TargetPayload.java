package dev.soityy.trajectorylens.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client instruction for the trajectory overlay.
 * value: "toggle" | "clear" | "target:<item id>"
 */
public record TargetPayload(String value) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TargetPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("trajectorylens", "target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TargetPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, TargetPayload::value, TargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

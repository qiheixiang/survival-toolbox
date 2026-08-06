package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.util.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 自适应数据同步包（服务端 → 客户端）
 * <p>
 * 1.21.1 中通过 {@code CustomData.getUnsafe()} 原地修改 NBT 不会触发装备重同步，
 * 客户端读到的护盾/层数/适应标记永远是旧值（护盾条不刷新、客户端拦不住火）。
 * 因此仿照 {@link SyncBlacklistPacket} 的做法，服务端定期把每件自适应盔甲的数据
 * 主动推送给客户端，客户端直接写回自己盔甲的 CustomData，HUD 与客户端 Mixin 即读到最新数据。
 * </p>
 */
public class SyncShieldDataPacket implements CustomPacketPayload {

    public static final Type<SyncShieldDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "sync_shield"));

    public record ArmorData(int slot, float layers, float shieldCurrent, float shieldMax,
                            boolean fireAdapted, boolean nightAdapted, boolean fogAdapted) {}

    public static final StreamCodec<FriendlyByteBuf, SyncShieldDataPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeVarInt(msg.dataList.size());
                for (ArmorData d : msg.dataList) {
                    buf.writeVarInt(d.slot());
                    buf.writeFloat(d.layers());
                    buf.writeFloat(d.shieldCurrent());
                    buf.writeFloat(d.shieldMax());
                    buf.writeBoolean(d.fireAdapted());
                    buf.writeBoolean(d.nightAdapted());
                    buf.writeBoolean(d.fogAdapted());
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<ArmorData> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(new ArmorData(
                            buf.readVarInt(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean()
                    ));
                }
                return new SyncShieldDataPacket(list);
            }
    );

    private final List<ArmorData> dataList;

    public SyncShieldDataPacket(List<ArmorData> dataList) {
        this.dataList = dataList;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncShieldDataPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) return;

            // 按槽位收集当前客户端盔甲（顺序与服务端一致：脚→腿→胸→头）
            ItemStack[] armors = new ItemStack[4];
            int idx = 0;
            for (ItemStack armor : player.getArmorSlots()) {
                if (idx >= 4) break;
                armors[idx] = armor;
                idx++;
            }

            for (ArmorData d : msg.dataList) {
                if (d.slot() < 0 || d.slot() >= 4) continue;
                ItemStack armor = armors[d.slot()];
                if (armor == null || armor.isEmpty()) continue;

                CompoundTag root = ItemNbt.getOrCreateTag(armor).copy();
                root.putFloat("adapt_layers", d.layers());
                root.putFloat("adapt_shield_current", d.shieldCurrent());
                root.putFloat("adapt_shield_max", d.shieldMax());
                CompoundTag adaptData = root.getCompound("adapt_data");
                adaptData.putBoolean("fire_adapted", d.fireAdapted());
                adaptData.putBoolean("night_adapted", d.nightAdapted());
                adaptData.putBoolean("fog_adapted", d.fogAdapted());
                root.put("adapt_data", adaptData);
                ItemNbt.setTag(armor, root);
            }
        });
    }
}

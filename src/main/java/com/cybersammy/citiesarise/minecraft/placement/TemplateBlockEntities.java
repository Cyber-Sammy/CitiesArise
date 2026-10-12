package com.cybersammy.citiesarise.minecraft.placement;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft-owned payload codec and placement; no NBT objects escape into planning data. */
public final class TemplateBlockEntities {
    public static final String KEY = "minecraft:block_entity";
    private TemplateBlockEntities() { }

    public static Map<String,String> fromString(String text) {
        return encode(decode(Map.of(KEY,text)));
    }

    public static Map<String,String> encode(CompoundTag tag) {
        var copy = tag.copy();
        copy.remove("x"); copy.remove("y"); copy.remove("z");
        return com.cybersammy.citiesarise.core.content.ProviderData.copy(Map.of(KEY,copy.toString()));
    }

    public static CompoundTag decode(Map<String,String> data) {
        com.cybersammy.citiesarise.core.content.ProviderData.copy(data);
        if (data.keySet().stream().anyMatch(key -> !KEY.equals(key)))
            throw new IllegalArgumentException("Unsupported Minecraft provider data");
        if (!data.containsKey(KEY)) return new CompoundTag();
        String text=data.get(KEY);
        int depth=0; char quote=0; boolean escaped=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quote!=0) {
                if(escaped) escaped=false;
                else if(c=='\\') escaped=true;
                else if(c==quote) quote=0;
            } else if(c=='\'' || c=='"') quote=c;
            else if(c=='{' || c=='[') {
                if(++depth>64) throw new IllegalArgumentException("Block entity data exceeds nesting budget");
            } else if(c=='}' || c==']') depth--;
        }
        try { return TagParser.parseTag(text); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Invalid block entity data",ex);
        }
    }

    public static void validate(BlockState state, Map<String,String> data) {
        if(data.isEmpty()) return;
        validatedTag(state,data);
    }

    private static CompoundTag validatedTag(BlockState state, Map<String,String> data) {
        var tag = decode(data);
        if (data.isEmpty()) return tag;
        if (!(state.getBlock() instanceof EntityBlock factory))
            throw new IllegalArgumentException("Block entity data requires an entity block");
        var entity = factory.newBlockEntity(BlockPos.ZERO,state);
        if (entity == null || (tag.contains("id") && !tag.getString("id").equals(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString())))
            throw new IllegalArgumentException("Block entity type does not match its material");
        return tag;
    }

    public static boolean place(WorldGenLevel level, BlockPos pos, BlockState state, Map<String,String> data) {
        if (data.isEmpty() && !state.hasBlockEntity() && !level.getBlockState(pos).hasBlockEntity())
            return level.setBlock(pos,state,2);
        var tag=validatedTag(state,data);
        if(tag.contains("LootTable") && !tag.contains("LootTableSeed"))
            tag.putLong("LootTableSeed",net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(level.getSeed() ^ pos.asLong()));
        return write(level,pos,state,tag,2);
    }

    public static boolean restore(WorldGenLevel level, BlockPos pos, BlockState state, CompoundTag original) {
        return write(level,pos,state,original,3);
    }

    private static boolean write(WorldGenLevel level, BlockPos pos, BlockState state, CompoundTag original,int flags) {
        var tag=original==null?new CompoundTag():original.copy();
        if (state.hasBlockEntity() || level.getBlockState(pos).hasBlockEntity()) {
            Clearable.tryClear(level.getBlockEntity(pos));
            level.setBlock(pos,Blocks.BARRIER.defaultBlockState(),20);
        }
        boolean changed = level.setBlock(pos,state,flags);
        if (state.hasBlockEntity()) {
            var entity = level.getBlockEntity(pos);
            if (entity == null) throw new IllegalStateException("Missing placed block entity at " + pos);
            tag.putInt("x",pos.getX()); tag.putInt("y",pos.getY()); tag.putInt("z",pos.getZ());
            entity.loadWithComponents(tag,level.registryAccess());
            entity.setChanged();
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                server.sendBlockUpdated(pos,state,state,2);
        }
        return changed;
    }
}

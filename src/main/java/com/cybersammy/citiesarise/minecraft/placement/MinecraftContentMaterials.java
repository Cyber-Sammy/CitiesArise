package com.cybersammy.citiesarise.minecraft.placement;

import java.util.LinkedHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;

public final class MinecraftContentMaterials {
    private static final LinkedHashMap<String,BlockState> CACHE=new LinkedHashMap<>(64,0.75f,true);
    private MinecraftContentMaterials() { }
    public static synchronized BlockState resolve(String value,int rotation) {
        BlockState state=CACHE.get(value);
        if(state==null) {
            try {
                var parsed=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),value,false);
                state=parsed.blockState();
                if(state.hasBlockEntity()) throw new IllegalArgumentException("Block entity content is not supported: "+value);
            } catch(com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new IllegalArgumentException("Invalid content block state: "+value,exception);
            }
            if(CACHE.size()>=4096) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(value,state);
        }
        return state.rotate(switch(rotation) { case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE; });
    }
}

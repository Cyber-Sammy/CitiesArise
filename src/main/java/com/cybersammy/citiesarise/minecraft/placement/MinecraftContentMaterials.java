package com.cybersammy.citiesarise.minecraft.placement;

import java.util.LinkedHashMap;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.WallSide;
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
            } catch(com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new IllegalArgumentException("Invalid content block state: "+value,exception);
            }
            if(CACHE.size()>=4096) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(value,state);
        }
        return state.rotate(switch(rotation) { case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE; });
    }
    static BlockState fenceConnections(BlockState state,String mask) {
        if(mask==null) return state;
        int bits=ParcelBoundaryData.connections(mask);
        boolean n=(bits&1)!=0,e=(bits&2)!=0,s=(bits&4)!=0,w=(bits&8)!=0;
        if(state.getBlock() instanceof CrossCollisionBlock) {
            state=state.setValue(CrossCollisionBlock.NORTH,n).setValue(CrossCollisionBlock.SOUTH,s)
                    .setValue(CrossCollisionBlock.WEST,w).setValue(CrossCollisionBlock.EAST,e);
        } else if(state.getBlock() instanceof WallBlock) {
            state=state.setValue(WallBlock.NORTH_WALL,n?WallSide.LOW:WallSide.NONE)
                    .setValue(WallBlock.SOUTH_WALL,s?WallSide.LOW:WallSide.NONE)
                    .setValue(WallBlock.WEST_WALL,w?WallSide.LOW:WallSide.NONE)
                    .setValue(WallBlock.EAST_WALL,e?WallSide.LOW:WallSide.NONE)
                    .setValue(WallBlock.UP,(!n && !e && !s && !w) || n!=s || e!=w);
        }
        return state;
    }
}

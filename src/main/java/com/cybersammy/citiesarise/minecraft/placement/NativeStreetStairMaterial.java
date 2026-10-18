package com.cybersammy.citiesarise.minecraft.placement;

/** Loaded only when native stair materialization is requested. Pure plan conversion stays JVM-testable. */
final class NativeStreetStairMaterial {
    private NativeStreetStairMaterial() { }
    static String orient(String material,int dx,int dz) {
        var probe=new DebugBlockPlacementOperation(new com.cybersammy.citiesarise.core.geometry.GridPoint(0,0),0,
                DebugPlacementRole.CONTENT_BLOCK,new com.cybersammy.citiesarise.core.model.PlanElementId("cities_arise:street-stair"),java.util.OptionalInt.of(0),material,0);
        var state=new VanillaDebugBlockMaterialProvider().blockState(probe);
        if(!(state.getBlock() instanceof net.minecraft.world.level.block.StairBlock))throw new IllegalArgumentException("Street stepMaterial must be a stair block");
        var facing=dx>0?net.minecraft.core.Direction.EAST:dx<0?net.minecraft.core.Direction.WEST:dz>0?net.minecraft.core.Direction.SOUTH:net.minecraft.core.Direction.NORTH;
        var oriented=state.setValue(net.minecraft.world.level.block.StairBlock.FACING,facing)
                .setValue(net.minecraft.world.level.block.StairBlock.HALF,net.minecraft.world.level.block.state.properties.Half.BOTTOM)
                .setValue(net.minecraft.world.level.block.StairBlock.SHAPE,net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT);
        return net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(oriented);
    }
}

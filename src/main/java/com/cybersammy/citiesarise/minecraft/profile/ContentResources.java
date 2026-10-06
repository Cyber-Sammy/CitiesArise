package com.cybersammy.citiesarise.minecraft.profile;

import java.io.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@FunctionalInterface
public interface ContentResources {
    InputStream open(String id,String directory,String extension) throws IOException;
    default void validateMaterial(String material) {
        if(material==null || material.length()>512 || !material.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_=,]+\\])?")) {
            throw new IllegalArgumentException("Invalid material state: "+material);
        }
    }
    default void validateTraits(String material,boolean passable,boolean supportive,boolean climbable) { validateMaterial(material); }
    default void validateWalkingSurface(String material) { validateMaterial(material); }
    default void validateBridgeSurface(String material, boolean halfStep) { validateMaterial(material); }
    static ContentResources classpath() {
        return (id,directory,extension) -> {
            String[] parts=id.split(":",2);
            if(parts.length!=2 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains("..")) throw new IllegalArgumentException("Invalid resource id: "+id);
            InputStream stream=ContentResources.class.getResourceAsStream("/data/"+parts[0]+"/"+directory+"/"+parts[1]+extension);
            if(stream==null) throw new FileNotFoundException(id); return stream;
        };
    }
    static ContentResources of(ResourceManager manager) {
        return new ContentResources() {
            @Override public InputStream open(String id,String directory,String extension) throws IOException {
                ResourceLocation key=ResourceLocation.parse(id);
                return manager.getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(key.getNamespace(),directory+"/"+key.getPath()+extension)).open();
            }
            @Override public void validateMaterial(String material) {
                ContentResources.super.validateMaterial(material);
                com.cybersammy.citiesarise.minecraft.placement.MinecraftContentMaterials.resolve(material,0);
            }
            @Override public void validateTraits(String material,boolean passable,boolean supportive,boolean climbable) {
                validateMaterial(material);
                var state=com.cybersammy.citiesarise.minecraft.placement.MinecraftContentMaterials.resolve(material,0);
                var world=net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
                var origin=net.minecraft.core.BlockPos.ZERO;
                if(passable && ((!climbable && !state.getCollisionShape(world,origin).isEmpty()) || !state.getFluidState().isEmpty()))
                    throw new IllegalArgumentException("Material declared passable has collision/fluid: "+material);
                if(supportive && !state.isFaceSturdy(world,origin,net.minecraft.core.Direction.UP))
                    throw new IllegalArgumentException("Material does not support the upper face: "+material);
                if(climbable && !state.is(net.minecraft.tags.BlockTags.CLIMBABLE))
                    throw new IllegalArgumentException("Material is not climbable: "+material);
            }
            @Override public void validateBridgeSurface(String material, boolean halfStep) {
                validateMaterial(material);
                var state=com.cybersammy.citiesarise.minecraft.placement.MinecraftContentMaterials.resolve(material,0);
                var shape=state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO);
                var expected=net.minecraft.world.phys.shapes.Shapes.box(0,0,0,1,halfStep?0.5:1,1);
                if(state.hasBlockEntity() || !state.getFluidState().isEmpty()
                        || net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,expected,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME))
                    throw new IllegalArgumentException("Graded bridge requires a dry full-footprint "
                            +(halfStep?"bottom half-step: ":"full-height deck: ")+material);
            }
            @Override public void validateWalkingSurface(String material) {
                validateMaterial(material);
                var state=com.cybersammy.citiesarise.minecraft.placement.MinecraftContentMaterials.resolve(material,0);
                var shape=state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO);
                if(!state.getFluidState().isEmpty() || shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y)>1)
                    throw new IllegalArgumentException("Invalid walking surface: "+material);
            }
        };
    }
}

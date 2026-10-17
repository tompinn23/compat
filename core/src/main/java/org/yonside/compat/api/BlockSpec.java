package org.yonside.compat.api;

/**
 * A block's fixed properties: {@code BlockSpec.of(BlockMaterial.METAL).hardness(3f).light(7)}.
 *
 * @param hardness how long it takes to mine; stone is 1.5
 * @param light    the light level it gives off, 0 to 15
 */
public record BlockSpec(BlockMaterial material, float hardness, int light) {

    public static BlockSpec of(BlockMaterial material) {
        return new BlockSpec(material, 1f, 0);
    }

    public BlockSpec hardness(float hardness) {
        return new BlockSpec(material, hardness, light);
    }

    public BlockSpec light(int light) {
        if (light < 0 || light > 15) {
            throw new IllegalArgumentException("light must be 0 to 15, was " + light);
        }
        return new BlockSpec(material, hardness, light);
    }
}

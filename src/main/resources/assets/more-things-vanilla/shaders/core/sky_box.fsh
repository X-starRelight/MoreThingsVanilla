#version 150

#ifdef RESTORE_SKY_DEPTH
#moj_import <minecraft:projection.glsl>
uniform sampler2D SceneDepth;
#endif

void main() {
#ifdef RESTORE_SKY_DEPTH
    // The snapshot contains both the depth mask and any closer terrain/entities.
    // Restore sky only where this exact surface survived the opaque passes.
    float scene = texelFetch(SceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (scene != gl_FragCoord.z) discard;
    // Perspective matrices have positive m22 for reverse-Z, negative for forward-Z.
    gl_FragDepth = ProjMat[2][2] > 0.0 ? 0.0 : 1.0;
#endif
}

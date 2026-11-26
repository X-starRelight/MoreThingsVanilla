#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;

in vec3 skyDirection;

out vec4 fragColor;

void main() {
    vec3 dir = normalize(skyDirection);
    vec3 absDir = abs(dir);

    vec2 uv;
    vec4 skyColor;

    // 六个采样器分别对应 26.2 SkyRenderer 的六次 90° 捕获：
    // +X, -X, +Y, -Y, +Z, -Z。
    if (absDir.x >= absDir.y && absDir.x >= absDir.z) {
        if (dir.x > 0.0) {
            uv = vec2(dir.z, dir.y) / dir.x;
            skyColor = texture(Sampler0, uv * 0.5 + 0.5);
        } else {
            uv = vec2(-dir.z, dir.y) / -dir.x;
            skyColor = texture(Sampler1, uv * 0.5 + 0.5);
        }
    } else if (absDir.y >= absDir.x && absDir.y >= absDir.z) {
        if (dir.y > 0.0) {
            uv = vec2(dir.x, dir.z) / dir.y;
            skyColor = texture(Sampler2, uv * 0.5 + 0.5);
        } else {
            uv = vec2(dir.x, -dir.z) / -dir.y;
            skyColor = texture(Sampler3, uv * 0.5 + 0.5);
        }
    } else {
        if (dir.z > 0.0) {
            uv = vec2(-dir.x, dir.y) / dir.z;
            skyColor = texture(Sampler4, uv * 0.5 + 0.5);
        } else {
            uv = vec2(dir.x, dir.y) / -dir.z;
            skyColor = texture(Sampler5, uv * 0.5 + 0.5);
        }
    }

    fragColor = vec4(skyColor.rgb, 1.0);
}

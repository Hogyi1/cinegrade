#version 330

// multiple of 16
layout(std140, binding = 0) uniform BasicBlock {
    float exposure; // offset 0 - 4 bytes
    float temp; // offset 4 - 4 bytes
    float tint; // offset 8 - 4 bytes
    float contrast; // offset 12 - 4 bytes

    float highlights; // offset 16 - 4 bytes
    float shadows; // offset 20 - 4 bytes
    float whites; // offset 24 - 4 bytes
    float blacks; // offset 28 - 4 bytes

    float vibrance; // offset 32 - 4 bytes
    float saturation; // offset 36 - 4 bytes
    float pad1;
    float pad2;
};

// View transform, not part of the edit: x = zoom (uv scale, 1 = whole image), yz = pan offset in [0,1] uv space
uniform vec3 uvZoom_OffsetX_OffsetY;

in vec2 vs_out_tex;
out vec4 fs_out_col;

uniform sampler2D texImage;

void main()
{
    vec2 uv = uvZoom_OffsetX_OffsetY.yz + vs_out_tex * uvZoom_OffsetX_OffsetY.x;
    vec4 color = texture(texImage, uv);
    // exposure is in EV (stops): 0 = unchanged, +1 = twice as bright
    fs_out_col = vec4(color.rgb * exp2(exposure), 1.0);
}

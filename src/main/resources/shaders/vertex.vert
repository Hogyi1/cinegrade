#version 330

// VBO-ból érkező változók
layout( location = 0 ) in vec3 vs_in_position;
layout( location = 1 ) in vec2 vs_in_tex;

// a pipeline-ban tovább adandó értékek
out vec2 vs_out_tex;

void main()
{
    gl_Position  = vec4( vs_in_position, 1.0 );
    vs_out_tex = vs_in_tex;
}
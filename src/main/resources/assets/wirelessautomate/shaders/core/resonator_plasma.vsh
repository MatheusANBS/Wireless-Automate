#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 quad;
out vec4 params;
flat out ivec2 extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    quad = UV0;
    params = Color;
    extra = UV2;
}

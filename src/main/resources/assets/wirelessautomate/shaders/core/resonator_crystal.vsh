#version 150

// O contrato dos vértices é o do CrystalGem: Position e Normal já na pose (relativos à câmera, nos eixos do
// mundo), a cor do tier e a sintonia em Color, os cantos da faceta em UV0, x e z do cristal em UV1 e a altura
// relativa e o parâmetro em UV2. Os inteiros viram float aqui, para o ruído interpolar preso ao cristal.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 rel;
out vec3 normal;
out vec2 facet;
out vec3 crystal;
flat out vec4 tint;
flat out int param;

const float POS_SCALE = 10000.0;
const float BOTTOM = -0.46;
const float TOP = 0.58;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rel = Position;
    normal = Normal;
    facet = UV0;
    float h = float(UV2.x) / POS_SCALE;
    crystal = vec3(float(UV1.x) / POS_SCALE - 1.0, h, float(UV1.y) / POS_SCALE - 1.0);
    tint = Color;
    param = UV2.y;
}

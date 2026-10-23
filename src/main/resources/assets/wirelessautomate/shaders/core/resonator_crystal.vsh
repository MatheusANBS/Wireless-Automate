#version 150

// O contrato dos vértices é o do CrystalGem: Position e Normal já na pose (relativos à câmera, nos eixos do
// mundo), a cor da faixa e o clarão do fim da recarga em Color, 1 no UV0.x no casco do contorno, x e z do cristal em UV1 e a altura
// relativa e o parâmetro em UV2. Os inteiros viram float aqui, para o ruído interpolar preso ao cristal. O casco
// infla pela direção do contorno (no Normal) com largura constante na tela: a distância vezes tan(FOV/2), que é
// 1 / ProjMat[1][1], em OUTLINE_PX pixels da altura da janela. A luz vem de cima e da esquerda da vista, como nos
// sprites, e vai para os eixos do mundo pela rotação da câmera no ModelViewMat.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;

out vec3 normal;
out vec3 crystal;
flat out vec3 light;
flat out int hull;
flat out vec4 tint;
flat out int param;

const float POS_SCALE = 10000.0;
const float BOTTOM = -0.46;
const float TOP = 0.58;
const float OUTLINE_PX = 2.0;

void main() {
    hull = UV0.x > 0.5 ? 1 : 0;
    vec3 pos = Position;
    if (hull == 1) {
        pos += normalize(Normal) * (OUTLINE_PX * 2.0 / ScreenSize.y) * length(Position) / ProjMat[1][1];
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    normal = Normal;
    light = normalize(vec3(-0.65, 0.65, 0.4) * mat3(ModelViewMat));
    float h = float(UV2.x) / POS_SCALE;
    crystal = vec3(float(UV1.x) / POS_SCALE - 1.0, h, float(UV1.y) / POS_SCALE - 1.0);
    tint = Color;
    param = UV2.y;
}

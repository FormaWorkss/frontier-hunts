#version 150

// [1.1.2] Frontier weapon icons in inventory slots: Minecraft's two item lights, but brighter and with more ambient,
// so the dark guns and bows read on the dark hotbar. The normal goes on to the fragment shader for the sheen.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 viewNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec3 n = normalize(Normal);
    float light0 = max(0.0, dot(Light0_Direction, n));
    float light1 = max(0.0, dot(Light1_Direction, n));
    float light = min(1.0, (light0 + light1) * 0.5 + 0.62);
    vertexColor = vec4(Color.rgb * light, Color.a);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    viewNormal = n;
}

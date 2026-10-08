#version 150

// [1.1.2] Frontier weapon icons: the darks are lifted (blued steel and black stocks become readable gunmetal and
// dark grey, light colours stay as they are), and a soft sheen runs along every surface that turns away from you, so
// the weapon's shape reads from its own lighting - no plate, no outline.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    color.rgb = pow(max(color.rgb, vec3(0.0)), vec3(0.68));
    float rim = pow(1.0 - clamp(abs(normalize(viewNormal).z), 0.0, 1.0), 3.0);
    color.rgb = min(vec3(1.0), color.rgb + rim * 0.16);
    fragColor = color;
}

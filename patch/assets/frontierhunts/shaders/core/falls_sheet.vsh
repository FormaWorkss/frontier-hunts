#version 150
// Frontier Hunts falling-water sheet. Original shader.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 tint;
out vec2 flow;        // x: lateral world coordinate (blocks), y: distance fallen from the lip (blocks)
out float height;     // total drop of this sheet (blocks)
out float edge;       // 0 inside the curtain, 1 at a free edge
flat out int mode;    // 0 body, 1 veil, 2 surface run-in
out float distanceToEye;
out vec2 light;
out vec3 worldNormal;
out vec3 viewDir;
void main(){
    vec4 view=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*view;
    tint=Color;
    flow=UV0;
    height=float(UV1.x&1023);
    mode=(UV1.x>>12)&3;
    edge=float(UV1.y)/1000.0;
    distanceToEye=length(view.xyz);
    light=vec2(float(UV2.x),float(UV2.y))/240.0;
    worldNormal=Normal;
    viewDir=normalize(-Position);
}

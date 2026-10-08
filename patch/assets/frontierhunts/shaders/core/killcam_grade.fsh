#version 150
// Kill cam colour grade on the finished frame: desaturate, cool X-ray tint, darken, vignette, impact flash.
uniform sampler2D Sampler0;
uniform float Desat;
uniform float Dark;
uniform float TintAmount;
uniform vec3 Tint;
uniform float Vignette;
uniform float Flash;
uniform float Contrast;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec3 c=texture(Sampler0,texCoord).rgb;
    float l=dot(c,vec3(.2126,.7152,.0722));
    c=mix(c,vec3(l),Desat);
    c=(c-.5)*Contrast+.5;
    c=mix(c,l*Tint*1.2,TintAmount);
    vec2 q=texCoord-.5;
    q.x*=1.3;
    float vig=smoothstep(.9,.25,length(q));
    c*=mix(1.0,vig,Vignette);
    c*=1.0-Dark;
    c+=vec3(Flash);
    fragColor=vec4(clamp(c,0.0,1.0),1.0);
}

#version 150
// Kill cam effects. UV encodes the shape: y>20 solid (bullet mesh, lit on the CPU),
// y offset by 10 = hard-edged drop/flake, otherwise a soft, slightly broken-up mist puff.
in vec2 shape;
in vec4 tint;
out vec4 fragColor;
void main(){
    if(shape.y>20.0){fragColor=tint;return;}
    vec2 uv=shape;
    bool hard=uv.y>5.0;
    if(hard)uv.y-=10.0;
    float r2=dot(uv,uv);
    if(r2>=1.0)discard;
    float a;
    if(hard){
        a=1.0-smoothstep(.55,1.0,r2);
    }else{
        float breakup=.78+.22*sin(uv.x*7.3+uv.y*3.1)*sin(uv.y*6.7-uv.x*2.3);
        a=exp(-r2*3.0)*(1.0-r2)*breakup;
    }
    a*=tint.a;
    if(a<.004)discard;
    fragColor=vec4(tint.rgb,a);
}

#version 150
// Frontier Hunts falling-water sheet. Original procedural material:
// water leaves the lip glassy and dark, accelerates under gravity, tears into white aerated
// streaks and ragged fingers, and dissolves into spray at the foot. Advection follows the
// ballistic travel time from the lip, so streaks stretch as they speed up.
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float DayLight;
uniform float FallTime;
uniform vec3 SunDir;
uniform vec3 SkyTint;
// x: brightness, y: opacity. Under a shader pack this overlay skips its tone mapping, so it is dimmed.
uniform vec2 Grade;
in vec4 tint;
in vec2 flow;
in float height;
in float edge;
flat in int mode;
in float distanceToEye;
in vec2 light;
in vec3 worldNormal;
in vec3 viewDir;
out vec4 fragColor;

float hash(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
float vnoise(vec2 p){
    vec2 i=floor(p),f=fract(p);
    vec2 u=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),u.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x),u.y);
}
float fbm(vec2 p){float a=0.0,w=.5;for(int i=0;i<4;i++){a+=vnoise(p)*w;p=p*2.03+vec2(17.1,3.7);w*=.5;}return a;}
// Seconds since the water left the lip, for a lip speed v0 (m/s) under 9.81 m/s^2.
float travel(float d,float v0){return (sqrt(v0*v0+19.62*max(d,0.0))-v0)/9.81;}

void main(){
    float d=flow.y,across=flow.x;
    float H=max(height,1.0);
    vec3 water=tint.rgb;
    float t=FallTime;
    if(mode==2){
        // Run-in to the lip: surface streaks racing towards the brink (flow.y = blocks to the edge).
        float toEdge=max(d,0.0);
        float speed=1.4+2.2/(toEdge+0.6);
        float phase=toEdge/speed*1.6+t*1.9;
        float streak=fbm(vec2(across*2.6,phase*1.8));
        float fade=1.0-smoothstep(0.4,3.6,toEdge);
        float a=smoothstep(.52,.78,streak)*fade*.55;
        vec3 c=mix(water*1.3,vec3(.93,.97,1.0),.65);
        float lit=max(.12,max(light.x,light.y*DayLight));
        fragColor=vec4(mix(c*lit*Grade.x,FogColor.rgb,smoothstep(FogStart,FogEnd,distanceToEye)*FogColor.a),a*tint.a*Grade.y);
        if(fragColor.a<.01)discard;
        return;
    }
    bool veil=mode==1;
    float v0=veil?2.2:1.5;
    float tt=travel(d,v0);
    float phase=tt-t*(veil?1.1:1.0);
    // Domain-warped lateral coordinate: threads sway and braid as they fall.
    float sway=(fbm(vec2(across*.35+5.0,phase*.9))-.5)*(1.2+d*.04);
    float ax=across+sway;
    // Fine threads, heavy falling packets and broad pulses, all advected with the water.
    float threads=fbm(vec2(ax*(veil?2.0:4.2),phase*(veil?5.0:9.0)));
    float packets=fbm(vec2(ax*1.6+13.0,phase*3.4));
    float pulses=fbm(vec2(ax*.55+11.0,phase*1.4));
    float tear=fbm(vec2(ax*1.1+37.0,phase*2.3+d*.04));
    // Aeration: glassy for the first moments, then white; packets are white first.
    float aer=smoothstep(0.0,1.2+H*.05,d);
    aer=clamp(aer*(.55+.55*pulses)+smoothstep(.55,.8,packets)*.5*smoothstep(0.0,1.0,d),0.0,1.0);
    // Never fully white: a fall stays a streaked blue-white sheet you can half see through.
    aer*=.88;
    // Contrast: bright crests of foam and darker, clearer channels between them.
    float crest=smoothstep(.42,.78,threads*.7+packets*.45);
    float channel=1.0-smoothstep(.28,.5,threads);
    // Ragged free edges; lower down the sheet parts into fingers with spray trails.
    float fray=edge*(.62+.55*tear)+(d/H)*.2;
    float body=1.0-smoothstep(.50,.92,fray+(threads-.5)*.45);
    float fingers=smoothstep(.20,.50,tear+.38-(d/H)*.32);
    float trails=edge*smoothstep(.62,.9,fbm(vec2(ax*7.0,phase*14.0)))*smoothstep(1.0,4.0,d);
    if(veil){
        // The veil is spray and mist leaving the sheet: soft billows, strongest low down and
        // along the free edges, never a flat pane.
        float billow=fbm(vec2(ax*.8+3.0,phase*1.6))*.65+fbm(vec2(ax*2.3,phase*3.1+7.0))*.45;
        float low=smoothstep(H*.25,H*.9,d);
        body=smoothstep(.55,.95,billow)*(.25+.75*max(low,edge*.8))*(1.0-smoothstep(.0,.35,-d));
        fingers=1.0;crest=.9;channel=0.0;trails=0.0;
    }
    float alpha=max(body*mix(1.0,fingers,smoothstep(2.0,6.0,d)),trails*.6);
    // Light, clear water body: the water colour lifted toward a cool sky-lit cyan.
    vec3 deep=mix(water*1.25,vec3(.42,.68,.76),.35);
    vec3 glass=mix(deep,vec3(.70,.86,.92)*(.78+.22*SkyTint),.55);
    // Aerated water is bright but never paper white: it keeps a little of the water colour.
    vec3 foam=mix(mix(vec3(.78,.85,.89),vec3(.94,.96,.97),crest),water*1.55,.14);
    // Vertical streaking: white where the threads crest, the clear body between them.
    vec3 col=mix(glass,foam,clamp(aer*(.58+1.05*crest),0.0,1.0));
    col=mix(col,col*.78,channel*aer*.5);
    // Sunlit crests sparkle: fine glints riding the white threads.
    float glint=smoothstep(.80,.97,threads*.65+packets*.5)*aer;
    col+=glint*vec3(.22,.24,.26)*(.4+.6*DayLight);
    alpha*=mix(.58,.97,aer)*mix(1.0,.62,channel*(1.0-crest));
    // The foot: a boiling white band where the sheet meets the pool, then it dissolves.
    float foot=smoothstep(H-3.4,H-.4,d);
    col=mix(col,vec3(.92,.95,.97),foot*.80);
    alpha=mix(alpha,max(alpha,.85*smoothstep(.3,.7,packets)),foot*.8);
    // The last half metre dissolves into the boil rather than ending on a line.
    alpha*=1.0-smoothstep(H-1.1,H+.35,d);
    alpha*=smoothstep(-.05,.3,d);
    // Light: foam scatters, glass reflects the sky and glints in the sun.
    float lit=max(.10,max(light.x,light.y*DayLight));
    vec3 n=normalize(worldNormal+vec3(0.0,0.0,0.0));
    float facing=abs(dot(n,normalize(SunDir)));
    float spec=pow(max(0.0,dot(reflect(-viewDir,n),normalize(SunDir))),40.0)*(1.0-aer*.75)*smoothstep(.45,.8,threads);
    col=col*lit*(.84+.16*facing)+spec*1.35*DayLight*light.y*vec3(1.0,.97,.9);
    col=mix(col,SkyTint*lit,(1.0-aer)*.15);
    if(veil)col=mix(col,vec3(.95,.97,.99)*lit,.6);
    alpha*=tint.a*ColorModulator.a*Grade.y;
    col*=Grade.x;
    float fog=smoothstep(FogStart,max(FogStart+.01,FogEnd),distanceToEye);
    fragColor=vec4(mix(col*ColorModulator.rgb,FogColor.rgb,fog*FogColor.a),alpha);
    if(fragColor.a<.008)discard;
}

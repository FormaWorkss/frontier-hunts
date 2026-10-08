"""Derives the per-species torso / head geometry for vital/WildlifeVitals from the generated classic box models
(WildlifeModels.java) scaled by WildlifeSpecies.classicScale. Output: Java table rows (blocks, body frame:
fwd = towards the head, up from the ground)."""
import re, sys
root = sys.argv[1] if len(sys.argv) > 1 else '.'
src = open(root + '/src/com/formaworks/frontierhunts/wildlife2026/client/WildlifeModels.java').read()
spc = open(root + '/src/com/formaworks/frontierhunts/wildlife2026/WildlifeSpecies.java').read()
scale = {m.group(1): float(m.group(2)) for m in re.finditer(r'\("(\w+)",[^)]*?, (\d\.\d+)F\)', spc)}
meta = {m.group(1): [float(x.replace('F', '')) for x in m.group(2).split(',')] for m in re.finditer(r'case "(\w+)" -> new float\[\]\{([^}]*)\}', src)}
for m in re.finditer(r'private static LayerDefinition (\w+)\(\) \{(.*?)return LayerDefinition', src, re.S):
    name, body = m.group(1), m.group(2)
    parts = {}
    for pm in re.finditer(r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("(\w+)", CubeListBuilder\.create\(\)(.*?), PartPose\.offsetAndRotation\(([^)]*)\)\);', body):
        pose = [float(x.replace('F', '')) for x in pm.group(5).split(',')]
        boxes = [[float(x.replace('F', '')) for x in b.split(',')] for b in re.findall(r'addBox\(([^)]*)\)', pm.group(4))]
        parts[pm.group(3)] = (pm.group(2), pose, boxes)
    def origin(p):
        par, pose, _ = parts[p]
        o = pose[:3]
        if par in parts:
            po = origin(par)
            o = [o[0] + po[0], o[1] + po[1], o[2] + po[2]]
        return o
    k = scale[name] / 16.0
    bo = origin('body')
    bx = parts['body'][2]
    vmax = max(b[3] * b[4] * b[5] for b in bx)
    sel = [b for b in bx if b[3] * b[4] * b[5] >= 0.3 * vmax]
    x1 = max(b[0] + b[3] for b in sel); 
    y0 = min(b[1] for b in sel) + bo[1]; y1 = max(b[1] + b[4] for b in sel) + bo[1]
    z0 = min(b[2] for b in sel) + bo[2]; z1 = max(b[2] + b[5] for b in sel) + bo[2]
    hw = max(abs(min(b[0] for b in sel)), x1) * k
    top = (24 - y0) * k; bot = (24 - y1) * k
    front = -z0 * k; rear = z1 * k
    legs = [p for p in parts if p.startswith('leg_f')] or [p for p in parts if p.startswith('leg_')]
    leg = -parts[legs[0]][1][2] * k
    ho = origin('head'); hb = parts['head'][2][0]
    hc = [ho[0] + hb[0] + hb[3] / 2, ho[1] + hb[1] + hb[4] / 2, ho[2] + hb[2] + hb[5] / 2]
    hr = max(hb[3], hb[4], hb[5]) / 2 * k
    drop = meta[name][1] * k
    print(f'         case {name.upper()} -> new Torso({hw:.3f}, {bot:.3f}, {top:.3f}, {front:.3f}, {rear:.3f}, {leg:.3f}, {-hc[2]*k:.3f}, {(24-hc[1])*k:.3f}, {hr:.3f}, {drop:.3f});')

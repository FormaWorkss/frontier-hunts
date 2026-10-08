#!/usr/bin/env python3
"""Generate recipe-book unlock advancements for every recipe-book recipe (crafting / furnace / smoker / campfire /
stonecutter / smithing) of frontierhunts + frontierstructures that no advancement unlocks yet.

usage: python3 tools/recipes/gen_unlocks.py <built jar> [repo_root]

Writes data/<ns>/advancement/recipes/unlock/<recipe path>.json into <repo>/patch (frontierhunts) or
<repo>/fs/resources (frontierstructures). Same shape as vanilla datagen: unlocked by having the recipe or by picking up
one of its key ingredients (the mod materials of the recipe; else its first non-trivial ingredient). Idempotent:
recipes that already have an unlock (in the jar or in an earlier run) are skipped. Re-run after adding recipes, rebuild.
"""
import json, os, sys, zipfile
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import recipe_audit as ra  # noqa: E402

TRIVIAL = {'minecraft:stick', 'minecraft:string', '#minecraft:planks', '#minecraft:logs', '#minecraft:wooden_slabs',
           '#c:rods/wooden', 'minecraft:iron_nugget', '#minecraft:wool'}


def ingredient_keys(ing):
    """-> list of predicate strings for one ingredient ('ns:item' or '#ns:tag')."""
    if isinstance(ing, list):
        out = []
        for x in ing:
            out += ingredient_keys(x)
        return out
    if isinstance(ing, dict):
        if 'item' in ing:
            return [ra.rid(ing['item'])]
        if 'tag' in ing:
            return ['#' + ra.rid(ing['tag'])]
        if ing.get('type') == 'neoforge:components':
            its = ing.get('items')
            return [its] if isinstance(its, str) else list(its or [])
    return []


def recipe_ingredients(j):
    t = ra.rid(j.get('type', ''))
    if t == 'minecraft:crafting_shaped':
        key = j.get('key', {})
        seen, out = set(), []
        for row in j.get('pattern', []):
            for c in row:
                if c != ' ' and c in key and c not in seen:
                    seen.add(c); out.append(key[c])
        return out
    if t == 'minecraft:crafting_shapeless':
        return j.get('ingredients', [])
    if t == 'minecraft:smithing_transform':
        return [j.get('base'), j.get('addition'), j.get('template')]
    return [j.get('ingredient')]


def pick(j):
    alts = [ingredient_keys(i) for i in recipe_ingredients(j)]
    alts = [a for a in alts if a]
    mod = [a for a in alts if any(k.lstrip('#').split(':')[0] in ra.NAMESPACES for k in a)]
    chosen = mod[:2] or [a for a in alts if not set(a) <= TRIVIAL][:1] or alts[:1]
    return chosen


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    jar = sys.argv[1]
    root = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '..', '..'))
    a = ra.Audit(jar, ra.DEFAULT_VANILLA)
    a.scan_recipes()
    a.scan_advancements()
    z = zipfile.ZipFile(jar)
    written = 0
    for r in a.no_unlock:
        ns, path = r.split(':', 1)
        j = json.loads(z.read(f'data/{ns}/recipe/{path}.json'))
        crit = {'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': r}}}
        for n, keys in enumerate(pick(j)):
            items = keys[0] if len(keys) == 1 and keys[0].startswith('#') else [k for k in keys if not k.startswith('#')]
            if not items:
                continue
            crit[f'has_material_{n}'] = {'trigger': 'minecraft:inventory_changed',
                                          'conditions': {'items': [{'items': items}]}}
        adv = {'parent': 'minecraft:recipes/root', 'criteria': crit, 'requirements': [sorted(crit)],
               'rewards': {'recipes': [r]}}
        base = os.path.join(root, 'patch' if ns == ra.FH else os.path.join('fs', 'resources'))
        out = os.path.join(base, 'data', ns, 'advancement', 'recipes', 'unlock', path + '.json')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, 'w') as f:
            f.write(json.dumps(adv, indent=1) + '\n')
        written += 1
    print(f'{written} unlock advancements written ({len(a.recipe_ids)} recipes)')


if __name__ == '__main__':
    main()

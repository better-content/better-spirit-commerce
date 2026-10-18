#!/usr/bin/env python3
"""Reproduce campfire assets from Minecraft 1.20.1's client-extra archive (GraphicsMagick required).

Usage: python3 -B scripts/generate_spirit_campfire_assets.py /path/to/client-extra.jar
Only authors this repository's campfire resources; no runtime download or generated pack dependency.
"""
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/better_spirit_commerce'
KINDS = ('sacred', 'wicked', 'arcane', 'aerial', 'aqueous', 'earthen', 'infernal', 'tempo')


def put(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def main(archive):
    retained = ROOT / 'build/campfire-asset-inputs'
    retained.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        template = json.loads(z.read('assets/minecraft/models/block/template_campfire.json'))
        for name, output in [('block/campfire_fire', 'block/spirit_campfire_fire'),
                             ('block/campfire_log_lit', 'block/spirit_campfire_log_lit'),
                             ('particle/flame', 'particle/spirit_flame')]:
            source = retained / (name.replace('/', '_') + '.png')
            source.write_bytes(z.read('assets/minecraft/textures/' + name + '.png'))
            target = ASSETS / ('textures/' + output + '.png')
            target.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(['gm', 'convert', str(source), '-colorspace', 'GRAY', str(target)], check=True)
            mcmeta = 'assets/minecraft/textures/' + name + '.png.mcmeta'
            if mcmeta in z.namelist():
                target.with_suffix('.png.mcmeta').write_bytes(z.read(mcmeta))
        template['textures']['fire'] = 'better_spirit_commerce:block/spirit_campfire_fire'
        template['textures']['lit_log'] = 'better_spirit_commerce:block/spirit_campfire_log_lit'
        for element in template['elements']:
            for face in element['faces'].values():
                if face['texture'] in ('#fire', '#lit_log'):
                    face['tintindex'] = 0
        put(ASSETS / 'models/block/spirit_campfire.json', template)
        smoke = json.loads(z.read('assets/minecraft/particles/campfire_cosy_smoke.json'))
    lang = json.loads((ASSETS / 'lang/en_us.json').read_text())
    for kind in KINDS:
        name = kind + '_campfire'
        variants = {}
        for facing, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            for lit in ('true', 'false'):
                model = 'better_spirit_commerce:block/spirit_campfire' if lit == 'true' else 'minecraft:block/campfire_off'
                variants[f'facing={facing},lit={lit}'] = {'model': model, 'y': rotation}
        put(ASSETS / f'blockstates/{name}.json', {'variants': variants})
        put(ASSETS / f'models/item/{name}.json', {'parent': 'better_spirit_commerce:block/spirit_campfire'})
        put(ASSETS / f'particles/{kind}_flame.json', {'textures': ['better_spirit_commerce:spirit_flame']})
        put(ASSETS / f'particles/{kind}_smoke.json', smoke)
        put(RES / f'data/better_spirit_commerce/loot_tables/blocks/{name}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
                {'type': 'minecraft:item', 'name': 'better_spirit_commerce:' + name}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        lang['block.better_spirit_commerce.' + name] = kind.title() + ' Spirit Campfire'
    put(ASSETS / 'lang/en_us.json', lang)
    blocks = ['better_spirit_commerce:' + k + '_campfire' for k in KINDS]
    put(RES / 'data/minecraft/tags/blocks/campfires.json', {'replace': False, 'values': blocks})
    put(RES / 'data/minecraft/tags/blocks/mineable/axe.json', {'replace': False, 'values': blocks})
    put(RES / 'data/better_spirit_commerce/tags/blocks/spirit_campfires.json', {'replace': False, 'values': blocks})


if __name__ == '__main__':
    main(sys.argv[1])

#!/usr/bin/env python3
"""Strict diagnostics for this declared Forge dev fixture, not the full-pack LogPolicy."""
import collections
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path.home() / '.gradle/caches'
FORGE_VERSION = '1.20.1-47.4.13'
MAPPED = CACHE / 'forge_gradle/minecraft_user_repo/net/minecraftforge/forge' / (FORGE_VERSION + '_mapped_official_1.20.1') / ('forge-' + FORGE_VERSION + '_mapped_official_1.20.1.jar')
LANGUAGES = {
    'javafmllanguage': 'cf01e2179f72bd2829c65f5226d7dd0cfb6c686',
    'lowcodelanguage': 'e3f96129ea14eba84316f2d69816253e228e6e4c',
    'mclanguage': '85796906112d9b621a7b2996f8bfda49da991ef0',
    'fmlcore': 'f77e3021d1eb31d61f8d1b92d18ed4fb3c2806e5',
}
TARGETS = {
    'slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity': ('tconstruct', 'tconstruct.PartBuilderBlockEntityMixin', '167ed1a4c24560cc35b4e7b4d370aa92b98212dd0dc8f7999921575117bb1e9a'),
    'slimeknights.tconstruct.tables.block.entity.inventory.PartBuilderContainerWrapper': ('tconstruct', 'tconstruct.PartBuilderContainerWrapperMixin', '35878ff70f15800b6de1b5ae07c58f06d886f7d316823a24e88ed331f929d426'),
    'slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu$PatternSlot': ('tconstruct', 'tconstruct.PartBuilderPatternSlotPlacementMixin', 'e9e0006c03c87c0ae29372640b3e58bb06f169fac7269e5a750d333383226f27'),
    'com.ferreusveritas.dynamictrees.compat.season.NormalSeasonManager': ('dynamictrees', 'dynamictrees.NormalSeasonManagerMixin', '5805ee0fe52f752c26c2ec48e9a1f0828d837fe706daa0d08241050e7dc36f13'),
}
ATTRIBUTE = 'dev.shadowsoffire.attributeslib.client.AttributesLibClient'
SLEEPING = 'github.cosmicdan.sleepingoverhaul.server.ServerState'
RECORD = re.compile(r'^\[(\d{2}[A-Z][a-z]{2}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\] \[([^\]\n]+)/([A-Z]+)\] \[([^\]\n]+)\]: (.*)$')


def module_file(name, digest):
    return CACHE / 'modules-2/files-2.1/net.minecraftforge' / name / FORGE_VERSION / digest / (name + '-' + FORGE_VERSION + '.jar')


def check_hash(data, expected, algorithm='sha256'):
    if hashlib.new(algorithm, data).hexdigest() != expected:
        raise ValueError('Native diagnostic input hash changed')


def validate_inputs():
    """Exact native descriptors/classes, not prior log strings, authorize the limited cases."""
    for name, digest in LANGUAGES.items():
        file = module_file(name, digest)
        # Gradle's cache directory renders leading-zero SHA-1 as an unpadded integer.
        check_hash(file.read_bytes(), digest.zfill(40), 'sha1')
        with zipfile.ZipFile(file) as jar:
            if 'META-INF/mods.toml' in jar.namelist():
                raise ValueError('Language/core artifact acquired mods.toml')
    with zipfile.ZipFile(MAPPED) as jar:
        check_hash(jar.read('net/minecraftforge/common/ForgeConfigSpec.class'), '633184c06812ebeb2fc6ee3f101da946657b5d46c7c8cfb22d63130cd491db3d')
        check_hash(jar.read('net/minecraft/server/packs/VanillaPackResourcesBuilder.class'), 'cf63fa701dbde417c8ccd241325e0eaa0c03bbfa4c56c509a36f729e6ace9485')
    loader = module_file('fmlloader', 'b27b57b2f641bfe6ecdaf206ee54880d26bb9c10')
    check_hash(loader.read_bytes(), 'b27b57b2f641bfe6ecdaf206ee54880d26bb9c10', 'sha1')
    with zipfile.ZipFile(loader) as jar:
        if b'is missing mods.toml file' not in jar.read('net/minecraftforge/fml/loading/moddiscovery/ModFileParser.class'):
            raise ValueError('Native metadata diagnostic changed')
    provider = Path(os.environ.get('BC_CUSTOM_MOD_JAR_DIR', str(ROOT.parent / 'better-compat-fixes/build/libs')))
    with zipfile.ZipFile(provider / 'better-compat-fixes-0.1.9.jar') as jar:
        descriptor = jar.read('better_compat_fixes.mixins.json')
        check_hash(descriptor, '0fb334f27fd59f2260d71b66b725e2ae6b1c6b0d444f59dcc2772565fc1e85aa')
        config = json.loads(descriptor)
        if config['required'] is not False:
            raise ValueError('Optional mixin configuration became required')
        for _, mixin, digest in TARGETS.values():
            if mixin not in config['mixins']:
                raise ValueError('Optional mixin no longer declared')
            check_hash(jar.read('com/bettercontent/bettercompatfixes/mixin/' + mixin.replace('.', '/') + '.class'), digest)
    celestial = Path(os.environ['BC_CUSTOM_MOD_JAR_DIR']) / 'better-celestial-events-0.1.0.jar' if 'BC_CUSTOM_MOD_JAR_DIR' in os.environ else ROOT.parent / 'better-celestial-events/build/libs/better-celestial-events-0.1.0.jar'
    with zipfile.ZipFile(celestial) as jar:
        descriptor = jar.read('better_celestial_events.mixins.json')
        check_hash(descriptor, '99f22292a3e93963902bb53ce18a09633b6f205cfe3fc98feb994cd39030216f')
        if 'SleepingOverhaulMixin' not in json.loads(descriptor)['mixins']:
            raise ValueError('Sleeping Overhaul mixin no longer declared')
        cls = jar.read('com/bettercontent/celestial/mixin/SleepingOverhaulMixin.class')
        check_hash(cls, '169988da4c9f630fb9f33221ff584777eaf92edb9851bc5734bacca8234796ba')
        if b'org/spongepowered/asm/mixin/Pseudo' not in cls or SLEEPING.encode() not in cls:
            raise ValueError('Sleeping Overhaul mixin is no longer the exact absent-safe @Pseudo target')
    lodestone = CACHE / 'modules-2/files-2.1/curse.maven/lodestone-616457/6213794/e6346060ae70cbf103b231d8a1536bcd044c84a8/lodestone-616457-6213794.jar'
    check_hash(lodestone.read_bytes(), 'e6346060ae70cbf103b231d8a1536bcd044c84a8', 'sha1')
    with zipfile.ZipFile(lodestone) as jar:
        cls = jar.read('team/lodestar/lodestone/mixin/common/attributeslib/AttributeLibClientMixin.class')
        check_hash(cls, 'e6fa7b392a31ab6e772f0b2bfde34bb7ca32d95c9884f8065ccf121940e544c2')
        if b'org/spongepowered/asm/mixin/Pseudo' not in cls:
            raise ValueError('Absent AttributesLib target is no longer @Pseudo')


def classify(thread, logger, message, metadata, config_context):
    if thread == 'main' and logger == 'net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING':
        for name, digest in LANGUAGES.items():
            if message == f'Mod file {module_file(name, digest)} is missing mods.toml file':
                return 'native-language-metadata', config_context
    if thread == 'main' and logger == 'mixin/':
        for target, (mod, mixin, _) in TARGETS.items():
            if mod in metadata['mods']:
                continue
            missing = f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'
            target_line = f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}'
            if message in (missing, target_line):
                return 'absent-optional-native-target', config_context
        if ('better_celestial_events' in metadata['mods'] and 'sleepingoverhaul' not in metadata['mods']
                and metadata.get('sleeping_server_state_present') is False
                and message == f'Error loading class: {SLEEPING.replace(".", "/")} (java.lang.ClassNotFoundException: {SLEEPING})'):
            return 'absent-pseudo-sleeping-target', config_context
        if 'attributeslib' not in metadata['mods'] and message == f'Error loading class: {ATTRIBUTE.replace(".", "/")} (java.lang.ClassNotFoundException: {ATTRIBUTE})':
            return 'absent-pseudo-client-target', config_context
    if thread == 'main' and logger == 'net.minecraft.server.packs.VanillaPackResourcesBuilder/':
        if re.fullmatch(r"Assets URL 'union:" + re.escape(str(MAPPED)) + r"%23[1-9][0-9]*!/(assets|data)/\.mcassetsroot' uses unexpected schema", message):
            return 'native-union-assets', config_context
    if thread == 'main' and logger == 'net.minecraftforge.fml.loading.FMLConfig/CORE':
        if message == f'Configuration file {ROOT}/run-gametest/config/fml.toml is not correct. Correcting':
            return 'native-loader-first-defaults', config_context
    if logger == 'net.minecraftforge.common.ForgeConfigSpec/CORE' and (thread in ('main', 'Server thread') or re.fullmatch(r'modloading-worker-[0-9]+', thread)):
        corrections = metadata['corrections']
        if message in corrections:
            return 'registered-native-first-defaults', message
        if config_context is not None and message in corrections.get(config_context, []):
            return 'registered-native-first-defaults', config_context
    return None, config_context


def inspect(lines, metadata):
    if metadata.get('dist') != 'DEDICATED_SERVER':
        raise ValueError('Diagnostics apply only to the dedicated GameTest dev profile')
    failures, counts, seen = [], collections.Counter(), set()
    context, previous = None, None
    for number, raw in enumerate(lines, 1):
        line = raw.rstrip('\n')
        match = RECORD.fullmatch(line)
        if not match:
            if '/WARN]' in line or '/ERROR]' in line or '/FATAL]' in line or previous in ('WARN', 'ERROR', 'FATAL') and line.strip():
                failures.append((number, line))
            continue
        stamp, thread, level, logger, message = match.groups()
        try:
            datetime.datetime.strptime(stamp, '%d%b%Y %H:%M:%S.%f')
        except ValueError:
            failures.append((number, line))
            continue
        previous = level
        if level in ('TRACE', 'DEBUG', 'INFO'):
            continue
        if level not in ('WARN', 'ERROR', 'FATAL'):
            failures.append((number, line))
            continue
        category, context = classify(thread, logger, message, metadata, context) if level == 'WARN' else (None, context)
        identity = (logger, message, context)
        if category is None or identity in seen:
            failures.append((number, line))
        else:
            counts[category] += 1
            seen.add(identity)
    return failures, dict(counts)


def main(log):
    validate_inputs()
    run = log.parent.parent
    metadata = json.loads((run / 'native-diagnostic-inputs.json').read_text())
    if metadata['nonce'] != (run / 'native-diagnostic-nonce').read_text().strip():
        raise ValueError('Native diagnostic metadata is not from this invocation')
    failures, counts = inspect(log.read_text().splitlines(keepends=True), metadata)
    print('Native fixture classified diagnostics:', json.dumps(counts, sort_keys=True))
    for number, line in failures:
        print(f'UNCLASSIFIED {number}: {line}', file=sys.stderr)
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        sys.exit(main(Path(sys.argv[1])))
    except Exception as error:
        print('Native diagnostic input failure:', error, file=sys.stderr)
        sys.exit(1)

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
# Minecraft 1.20.1 library manifest; Gradle drops the leading zero in its cache directory.
NETTY_SHA1 = '22d148e85c3f5ebdacc0ce1f5aabb1d420f73f3'
NETTY = CACHE / 'modules-2/files-2.1/io.netty/netty-common/4.1.82.Final' / NETTY_SHA1 / 'netty-common-4.1.82.Final.jar'
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
STARTUP_LOGGER = 'better_spirit_commerce.native_startup/'
STARTUP_MARKER = re.compile(r'SPIRIT_NATIVE_STARTUP_BOUNDARY nonce=([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}) phase=(START_1|END_1|START_2)')
STARTUP_PHASES = ('START_1', 'END_1', 'START_2')
TIMING_LOGGER = 'better_spirit_commerce.native_timing/'
TIMING = re.compile(r'SPIRIT_NATIVE_TICK_TIMING nonce=([0-9a-f-]{36}) index=([1-3]) phase=(START|END) tick_count=(0|[1-9][0-9]*) mono_ns=(0|[1-9][0-9]*) mono_ms=(0|[1-9][0-9]*) sample_end_ns=(0|[1-9][0-9]*) next_tick_ms=(0|[1-9][0-9]*)')
TIMING_PHASES = tuple((index, phase) for index in range(1, 4) for phase in ('START', 'END'))
LAG = re.compile(r"Can't keep up! Is the server overloaded\? Running ([1-9][0-9]*)ms or ([1-9][0-9]*) ticks behind")
ATTRIBUTE = 'dev.shadowsoffire.attributeslib.client.AttributesLibClient'
SLEEPING = 'github.cosmicdan.sleepingoverhaul.server.ServerState'
RECORD = re.compile(r'^\[(\d{2}[A-Z][a-z]{2}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\] \[([^\]\n]+)/([A-Z]+)\] \[([^\]\n]+)\]: (.*)$')


def module_file(name, digest):
    return CACHE / 'modules-2/files-2.1/net.minecraftforge' / name / FORGE_VERSION / digest / (name + '-' + FORGE_VERSION + '.jar')


def check_hash(data, expected, algorithm='sha256', *, context='native diagnostic input'):
    actual = hashlib.new(algorithm, data).hexdigest()
    if actual != expected:
        raise ValueError(f'{context}: {algorithm} mismatch; expected {expected}, got {actual}')


def read_entry(jar, name):
    context = f'{jar.filename or "<in-memory archive>"}!/{name}'
    if jar.namelist().count(name) != 1:
        raise ValueError(f'{context}: expected exactly one archive entry')
    return jar.read(name)


def check_entry_hash(jar, name, expected):
    check_hash(read_entry(jar, name), expected,
               context=f'{jar.filename or "<in-memory archive>"}!/{name}')


def validate_compat_inputs(jar, targets=None, client_targets=()):
    """Pin the optional-target contract, not unrelated custom mixin inventory.

    Class-byte pins retain the target annotations and executable mapping contract.
    Side placement, package and loader policy must still match the inspected source.
    """
    targets = TARGETS if targets is None else targets
    name = 'better_compat_fixes.mixins.json'
    context = f'{jar.filename or "<in-memory archive>"}!/{name}'
    try:
        config = json.loads(read_entry(jar, name))
    except (ValueError, UnicodeError) as error:
        raise ValueError(f'{context}: invalid descriptor: {error}') from error
    if not isinstance(config, dict):
        raise ValueError(f'{context}: descriptor must be an object')
    expected_fields = {'required': False, 'minVersion': '0.8',
                       'package': 'com.bettercontent.bettercompatfixes.mixin',
                       'plugin': 'com.bettercontent.bettercompatfixes.mixin.BetterContentMixinPlugin',
                       'compatibilityLevel': 'JAVA_17'}
    for key, expected in expected_fields.items():
        if config.get(key) != expected or (key == 'required' and config[key] is not False):
            raise ValueError(f'{context}: {key} must remain {expected!r}, got {config.get(key)!r}')
    injectors = config.get('injectors')
    if not isinstance(injectors, dict) or type(injectors.get('defaultRequire')) is not int or injectors['defaultRequire'] != 0:
        raise ValueError(f'{context}: injectors.defaultRequire must remain 0')
    if config.get('refmap') is not None:
        raise ValueError(f'{context}: unexpected refmap changes the inspected mapping contract')
    sides = {side: config.get(side, []) for side in ('mixins', 'client', 'server')}
    for side, entries in sides.items():
        if not isinstance(entries, list) or any(not isinstance(entry, str) for entry in entries):
            raise ValueError(f'{context}: {side} must be an array of mixin names')
    for _, mixin, digest in targets.values():
        expected_side = 'client' if mixin in client_targets else 'mixins'
        placements = [side for side, entries in sides.items() for entry in entries if entry == mixin]
        if placements != [expected_side]:
            raise ValueError(f'{context}: {mixin} must occur exactly once in {expected_side}; got {placements}')
        entry = config['package'].replace('.', '/') + '/' + mixin.replace('.', '/') + '.class'
        check_entry_hash(jar, entry, digest)


def validate_inputs():
    """Exact native descriptors/classes, not prior log strings, authorize the limited cases."""
    check_hash(NETTY.read_bytes(), NETTY_SHA1.zfill(40), 'sha1', context=str(NETTY))
    for name, digest in LANGUAGES.items():
        file = module_file(name, digest)
        # Gradle's cache directory renders leading-zero SHA-1 as an unpadded integer.
        check_hash(file.read_bytes(), digest.zfill(40), 'sha1')
        with zipfile.ZipFile(file) as jar:
            if 'META-INF/mods.toml' in jar.namelist():
                raise ValueError('Language/core artifact acquired mods.toml')
    with zipfile.ZipFile(MAPPED) as jar:
        check_hash(jar.read('net/minecraft/server/MinecraftServer.class'), 'c8746828ef5885a6e0088d09431f154f11744580ac25927a67221689f649b356')
        check_hash(jar.read('net/minecraft/gametest/framework/GameTestServer.class'), 'f3d8dfa04059f50cf78b6ef48cd39003a1cfa7465182292bd7ad95618cd01985')
        check_hash(jar.read('net/minecraft/Util.class'), 'd5edddee1f571c90a6d98321c25c3342f71c170708004557a5696b6deecbc832')
        check_hash(jar.read('net/minecraftforge/common/ForgeConfigSpec.class'), '633184c06812ebeb2fc6ee3f101da946657b5d46c7c8cfb22d63130cd491db3d')
        check_hash(jar.read('net/minecraft/server/packs/VanillaPackResourcesBuilder.class'), 'cf63fa701dbde417c8ccd241325e0eaa0c03bbfa4c56c509a36f729e6ace9485')
    loader = module_file('fmlloader', 'b27b57b2f641bfe6ecdaf206ee54880d26bb9c10')
    check_hash(loader.read_bytes(), 'b27b57b2f641bfe6ecdaf206ee54880d26bb9c10', 'sha1')
    with zipfile.ZipFile(loader) as jar:
        if b'is missing mods.toml file' not in jar.read('net/minecraftforge/fml/loading/moddiscovery/ModFileParser.class'):
            raise ValueError('Native metadata diagnostic changed')
    provider = Path(os.environ.get('BC_CUSTOM_MOD_JAR_DIR', str(ROOT.parent / 'better-compat-fixes/build/libs')))
    with zipfile.ZipFile(provider / 'better-compat-fixes-0.1.9.jar') as jar:
        validate_compat_inputs(jar)
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
        run_directory = Path(metadata.get('_run_directory', ROOT / 'run-gametest'))
        if message == f'Configuration file {run_directory}/config/fml.toml is not correct. Correcting':
            return 'native-loader-first-defaults', config_context
    if logger == 'net.minecraftforge.common.ForgeConfigSpec/CORE' and (thread in ('main', 'Server thread') or re.fullmatch(r'modloading-worker-[0-9]+', thread)):
        corrections = metadata['corrections']
        if message in corrections:
            return 'registered-native-first-defaults', message
        if config_context is not None and message in corrections.get(config_context, []):
            return 'registered-native-first-defaults', config_context
    return None, config_context


def performance_record(number, line, message, thread=None, logger=None, level=None, stamp=None):
    lag = LAG.fullmatch(message)
    milliseconds = ticks = None
    try:
        if lag:
            milliseconds, ticks = map(int, lag.groups())
    except ValueError:
        pass
    return {'source_line': number, 'source_record': line, 'message': message,
            'duration_ms': milliseconds, 'ticks': ticks, 'thread': thread,
            'logger': logger, 'severity': level, 'timestamp': stamp,
            'accepted': False, '_candidate': False}


def delayed_initial_debt(notice, timing, markers):
    """No clock reset or stall repair: prove a single carried first-test setup debt."""
    if len(timing) != 6 or notice['duration_ms'] is None or notice['ticks'] is None:
        return None
    start1, end1, start2, end2, start3, end3 = timing
    duration, ticks = notice['duration_ms'], notice['ticks']
    setup = start2['mono_ms'] - end1['mono_ms']
    # Lower bound prevents sampling latency/rounding from hiding residual >=2000ms.
    setup_lower = (start2['mono_ns'] - end1['sample_end_ns']) // 1_000_000
    residual, residual_upper = duration - setup, duration - setup_lower
    instant = end2['next_tick_ms'] + duration
    ordered = sorted(timing + markers + [notice], key=lambda item: item['source_line'])
    wall_times = [datetime.datetime.strptime(item['timestamp'], '%d%b%Y %H:%M:%S.%f') for item in ordered]
    schedule = (end1['next_tick_ms'] == start1['next_tick_ms']
                and start2['next_tick_ms'] == end1['next_tick_ms'] + 50
                and end2['next_tick_ms'] == start2['next_tick_ms']
                and start3['next_tick_ms'] == end2['next_tick_ms'] + 50 + ticks * 50
                and end3['next_tick_ms'] == start3['next_tick_ms'])
    eligible = (end2['source_line'] < notice['source_line'] < start3['source_line']
            and duration > 2000 and setup_lower > 0 and 0 <= residual <= residual_upper < 2000
            and end2['mono_ms'] <= instant <= start3['mono_ms'] and schedule
            and markers[0]['source_line'] < start1['source_line'] < markers[1]['source_line']
            < end1['source_line'] < markers[2]['source_line'] < start2['source_line']
            and all(left <= right for left, right in zip(wall_times, wall_times[1:])))
    return dict(kind='delayed-initial-setup-debt', eligible=eligible, schedule_matches=schedule, setup_interval_ms=setup,
                setup_interval_lower_ms=setup_lower, nonsetup_residual_ms=residual,
                nonsetup_residual_upper_ms=residual_upper, warning_mono_ms=instant,
                schedule_proof=timing)


def _inspect_evidence(lines, metadata, diagnostics=None):
    """Retain strict causal attribution checks as evidence, not a correctness verdict."""
    if metadata.get('dist') != 'DEDICATED_SERVER':
        raise ValueError('Diagnostics apply only to the dedicated GameTest dev profile')
    diagnostics = diagnostics if diagnostics is not None else {}
    failures, counts = [], collections.Counter()
    context, previous = None, None
    markers, notices, timing = [], [], []
    timing_invalid = False
    boundary_invalid = False
    nonce = metadata.get('nonce')
    for number, raw in enumerate(lines, 1):
        line = raw.rstrip('\n')
        match = RECORD.fullmatch(line)
        if not match:
            if "Can't keep up!" in line:
                notices.append(performance_record(number, line, line[line.index("Can't keep up!"):]))
                failures.append((number, line))
            if '/WARN]' in line or '/ERROR]' in line or '/FATAL]' in line or previous in ('WARN', 'ERROR', 'FATAL') and line.strip():
                failures.append((number, line))
            if 'SPIRIT_NATIVE_TICK_TIMING' in line:
                timing_invalid = True
                failures.append((number, line))
            if 'SPIRIT_NATIVE_STARTUP_BOUNDARY' in line:
                boundary_invalid = True
                if (number, line) not in failures:
                    failures.append((number, line))
            continue
        stamp, thread, level, logger, message = match.groups()
        try:
            timestamp = datetime.datetime.strptime(stamp, '%d%b%Y %H:%M:%S.%f')
        except ValueError:
            if message.startswith("Can't keep up!"):
                notices.append(performance_record(number, line, message, thread, logger, level, stamp))
            failures.append((number, line))
            continue
        previous = level
        if 'SPIRIT_NATIVE_TICK_TIMING' in message or logger == TIMING_LOGGER:
            sample = TIMING.fullmatch(message)
            valid = sample is not None and thread == 'Server thread' and level == 'INFO' and logger == TIMING_LOGGER
            if valid:
                token, index, phase, counter, nanos, millis, sample_end, next_tick = sample.groups()
                index, counter, nanos, millis, sample_end, next_tick = map(int, (index, counter, nanos, millis, sample_end, next_tick))
                valid = (token == nonce and len(timing) < 6 and (index, phase) == TIMING_PHASES[len(timing)]
                         and counter == index - (phase == 'START')
                         and nanos <= sample_end and nanos // 1_000_000 <= millis <= sample_end // 1_000_000
                         and (not timing or (nanos >= timing[-1]['sample_end_ns']
                              and millis >= timing[-1]['mono_ms'] and timestamp >= datetime.datetime.strptime(timing[-1]['timestamp'], '%d%b%Y %H:%M:%S.%f'))))
            if valid:
                timing.append(dict(index=index, phase=phase, tick_count=counter, mono_ns=nanos,
                                   mono_ms=millis, sample_end_ns=sample_end, next_tick_ms=next_tick,
                                   timestamp=stamp, source_line=number))
            else:
                timing_invalid = True
                failures.append((number, line))
            continue
        if 'SPIRIT_NATIVE_STARTUP_BOUNDARY' in message or logger == STARTUP_LOGGER:
            marker = STARTUP_MARKER.fullmatch(message)
            valid = (marker is not None and marker.group(1) == nonce
                     and thread == 'Server thread' and level == 'INFO' and logger == STARTUP_LOGGER
                     and len(markers) < 3 and marker.group(2) == STARTUP_PHASES[len(markers)]
                     and (not markers or timestamp >= datetime.datetime.strptime(
                         markers[-1]['timestamp'], '%d%b%Y %H:%M:%S.%f')))
            if valid:
                markers.append({'phase': marker.group(2), 'source_line': number,
                                'message': message, 'timestamp': stamp})
            else:
                boundary_invalid = True
                failures.append((number, line))
            continue
        if message.startswith("Can't keep up!"):
            notice = performance_record(number, line, message, thread, logger, level, stamp)
            milliseconds, ticks = notice['duration_ms'], notice['ticks']
            notice['_identity'] = (milliseconds is not None and ticks is not None
                         and milliseconds > 0 and ticks > 0 and ticks == milliseconds // 50
                         and thread == 'Server thread' and level == 'WARN'
                         and logger == 'net.minecraft.server.MinecraftServer/')
            notice['_candidate'] = (notice['_identity'] and len(markers) == 2 and not boundary_invalid
                         and timestamp >= datetime.datetime.strptime(markers[1]['timestamp'], '%d%b%Y %H:%M:%S.%f'))
            notices.append(notice)
            continue
        if level in ('TRACE', 'DEBUG', 'INFO'):
            continue
        if level not in ('WARN', 'ERROR', 'FATAL'):
            failures.append((number, line))
            continue
        category, context = classify(thread, logger, message, metadata, context) if level == 'WARN' else (None, context)
        if category is None:
            failures.append((number, line))
        else:
            counts[category] += 1
    boundary_valid = not boundary_invalid and len(markers) == 3
    if not boundary_valid:
        failures.append((0, 'Missing, reordered, duplicate, or invalid nonce-bound native startup markers'))
    timing_valid = not timing_invalid and len(timing) == 6
    if timing and not timing_valid:
        failures.append((0, 'Incomplete or invalid first-three-tick telemetry'))
    for notice in notices:
        identity = notice.pop('_identity', False)
        accepted = notice.pop('_candidate') and boundary_valid and len(notices) == 1
        if accepted:
            accepted = datetime.datetime.strptime(notice['timestamp'], '%d%b%Y %H:%M:%S.%f') <= datetime.datetime.strptime(
                markers[2]['timestamp'], '%d%b%Y %H:%M:%S.%f')
        if accepted:
            notice['proof'] = {'kind': 'original-first-startup-window'}
        elif identity and boundary_valid and timing_valid and len(notices) == 1:
            proof = delayed_initial_debt(notice, timing, markers)
            accepted = proof is not None and proof['eligible']
            if proof is not None:
                notice['proof'] = proof
        notice['accepted'] = accepted
        if accepted:
            counts['native-first-startup-performance' if notice['proof']['kind'] == 'original-first-startup-window'
                   else 'native-delayed-initial-debt-performance'] += 1
        else:
            failure = (notice['source_line'], notice['source_record'])
            if failure not in failures:
                failures.append(failure)
    diagnostics.update({
        'schema': 'better_spirit_commerce.native_diagnostics.v1',
        'nonce': nonce, 'startup_boundary_valid': boundary_valid, 'startup_markers': markers,
        'performance_diagnostics': notices, 'tick_timing_valid': timing_valid, 'tick_timing': timing, 'classified_counts': dict(counts),
        'failures': [{'source_line': number, 'record': line} for number, line in failures],
        'passed': not failures,
    })
    return failures, dict(counts)


# Match failed operations, not the occurrence or volume of advisory records.
FAILED_OPERATION = re.compile(
    r'OutOfMemoryError|AssertionError|ReportedException:|ThreadingDetector:|'
    r'fatal error has been detected|crash report|Exception in server tick loop|'
    r'Failed to complete lifecycle event|Errors in currently selected datapacks|'
    r'Error loading KubeJS script|KubeJS errors found \[[1-9][0-9]*]|'
    r'(?:failed to|error (?:while|when)) (?:load|register|parse|execute|save|read|write|start|join)|'
    r'Registry Object not present:|(?:registry|registries|script|mixin)[^\n]*(?:failed|failure|corrupt)|'
    r'corrupt(?:ed|ion)?[^\n]*(?:chunk|world|save|data)|(?:chunk|world|save|data)[^\n]*corrupt(?:ed|ion)?|'
    r'^\s*(?:Caused by: |Suppressed: |Exception in thread "[^"]+" )?'
    r'(?:[a-zA-Z_$][\w$]*\.)*(?:\w*Exception|\w*Error):', re.I)


def operation_failed(line):
    match = RECORD.fullmatch(line)
    if match:
        _, _, level, _, message = match.groups()
        return level in ('ERROR', 'FATAL') or bool(FAILED_OPERATION.search(message))
    return bool(re.search(r'/(?:ERROR|FATAL)\]|\[(?:ERROR|FATAL)\]', line) or FAILED_OPERATION.search(line))


# Forge catches FileNotFoundException for optional JarJar metadata and returns
# Optional.empty; its other exception branch logs ERROR and is never classified.
OPTIONAL_JARJAR = re.compile(
    r'Failed to load resource META-INF/jarjar/metadata\.json from [^,\r\n]*, '
    r'it does not contain dependency information\.')
STACK_FRAME = re.compile(r'\s+at [\w.$/<>]+\([^()\r\n]*\)(?: ~\[[^\]\r\n]*\])?')
NETTY_PROBES = {
    'direct buffer constructor: unavailable': (
        'java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled',
        'io.netty.util.internal.ReflectionUtil.trySetAccessible'),
    'jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable': (
        'java.lang.IllegalAccessException: class io.netty.util.internal.PlatformDependent0$7 '
        '(in module io.netty.common) cannot access class jdk.internal.misc.Unsafe '
        '(in module java.base) because module java.base does not export jdk.internal.misc to module io.netty.common',
        'io.netty.util.internal.PlatformDependent0$7.run'),
}


def optional_probe_lines(group):
    """Only an exact DEBUG record and caught native stack establish probe provenance."""
    number, line = group[0]
    match = RECORD.fullmatch(line)
    if not match:
        return set(), None
    stamp, thread, level, logger, message = match.groups()
    try:
        datetime.datetime.strptime(stamp, '%d%b%Y %H:%M:%S.%f')
    except ValueError:
        return set(), None
    if thread != 'main' or level != 'DEBUG':
        return set(), None
    if (logger == 'net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator/'
            and OPTIONAL_JARJAR.fullmatch(message)
            and not FAILED_OPERATION.search(message.removeprefix('Failed to load resource '))):
        return {number}, 'native-optional-jarjar-probe'
    probe = NETTY_PROBES.get(message) if logger == 'io.netty.util.internal.PlatformDependent0/' else None
    if probe is None:
        return set(), None
    continuation = list(group[1:])
    while continuation and not continuation[-1][1].strip():
        continuation.pop()
    if len(continuation) < 2 or continuation[0][1] != probe[0]:
        return set(), None
    frames = [text for _, text in continuation[1:]]
    if not all(STACK_FRAME.fullmatch(text) for text in frames) or not any(
            re.match(r'\s+at ' + re.escape(probe[1]) + r'\(', text) for text in frames):
        return set(), None
    return {continuation[0][0]}, 'native-netty-reflection-probe'


def scan_operations(lines):
    failures, counts, group = [], collections.Counter(), []
    def flush():
        if not group:
            return
        diagnostic, category = optional_probe_lines(group)
        if category:
            counts[category] += 1
        failures.extend((number, text) for number, text in group
                        if number not in diagnostic and operation_failed(text))
    for number, raw in enumerate(lines, 1):
        line = raw.rstrip('\n')
        if RECORD.fullmatch(line):
            flush()
            group = []
        group.append((number, line))
    flush()
    return failures, counts


def inspect(lines, metadata, diagnostics=None):
    """Correctness is independent of WARN classification and timing-proof eligibility.

    There is no workload performance budget in this isolated parser. Native lag notices
    and their nonce/clock attribution remain diagnostics; the caller's test assertions,
    input hashes and execution deadline still gate the declared fixture.
    """
    lines = list(lines)
    diagnostics = diagnostics if diagnostics is not None else {}
    evidence_findings, counts = _inspect_evidence(lines, metadata, diagnostics)
    failures, probe_counts = scan_operations(lines)
    counts.update(probe_counts)
    diagnostics['classified_counts'] = dict(counts)
    diagnostics['attribution_findings'] = [{'source_line': number, 'record': line}
                                          for number, line in evidence_findings]
    diagnostics['failures'] = [{'source_line': number, 'record': line} for number, line in failures]
    diagnostics['passed'] = not failures
    return failures, counts


def main(log):
    run = log.parent.parent
    diagnostics = {'schema': 'better_spirit_commerce.native_diagnostics.v1',
                   'source_log': str(log.resolve()), 'passed': False,
                   'performance_diagnostics': []}
    try:
        validate_inputs()
        metadata = json.loads((run / 'native-diagnostic-inputs.json').read_text())
        # Derive trusted classification context from the actual audited log, not metadata input.
        metadata['_run_directory'] = str(run.resolve())
        if metadata['nonce'] != (run / 'native-diagnostic-nonce').read_text().strip():
            raise ValueError('Native diagnostic metadata is not from this invocation')
        failures, counts = inspect(log.read_text().splitlines(keepends=True), metadata, diagnostics)
        print('Native fixture classified diagnostics:', json.dumps(counts, sort_keys=True))
        for notice in diagnostics['performance_diagnostics']:
            print('Native fixture performance diagnostic:', json.dumps(notice, sort_keys=True))
        for number, line in failures:
            print(f'UNCLASSIFIED {number}: {line}', file=sys.stderr)
        return 1 if failures else 0
    except Exception as error:
        diagnostics['input_error'] = str(error)
        raise
    finally:
        (run / 'native-diagnostics.json').write_text(json.dumps(diagnostics, indent=2) + '\n')


if __name__ == '__main__':
    try:
        sys.exit(main(Path(sys.argv[1])))
    except Exception as error:
        print('Native diagnostic input failure:', error, file=sys.stderr)
        sys.exit(1)

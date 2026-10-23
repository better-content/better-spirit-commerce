import datetime
import hashlib
import io
import zipfile
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import check_native_diagnostics as d


def record(message, logger='mixin/', level='WARN', thread='main'):
    return f'[17Oct2026 11:31:27.496] [{thread}/{level}] [{logger}]: {message}\n'


class NativeDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.metadata = {'dist': 'DEDICATED_SERVER', 'mods': {'forge': '47.4.13'},
                         'corrections': {}, 'nonce': '018c0123-4567-489a-abcd-0123456789ab'}

    def test_loader_defaults_are_bound_to_actual_uuid_run_not_other_paths(self):
        run = d.ROOT / 'build/gametest/018c0123-4567-489a-abcd-0123456789ab'
        metadata = dict(self.metadata, _run_directory=str(run))
        logger = 'net.minecraftforge.fml.loading.FMLConfig/CORE'
        current = f'Configuration file {run}/config/fml.toml is not correct. Correcting'
        self.assertEqual('native-loader-first-defaults', d.classify('main', logger, current, metadata, None)[0])
        for other in (d.ROOT / 'run-gametest', run.parent / 'other'):
            message = f'Configuration file {other}/config/fml.toml is not correct. Correcting'
            self.assertIsNone(d.classify('main', logger, message, metadata, None)[0])

    def test_main_derives_run_context_instead_of_trusting_forged_metadata(self):
        guard = d.ROOT / 'build/native-diagnostic-guard'; guard.mkdir(parents=True, exist_ok=True)
        run = Path(tempfile.mkdtemp(prefix='context-', dir=guard)); (run / 'logs').mkdir()
        log = run / 'logs/latest.log'; log.write_text('')
        metadata = dict(self.metadata, _run_directory='forged')
        source = run / 'native-diagnostic-inputs.json'; source.write_text(json.dumps(metadata))
        (run / 'native-diagnostic-nonce').write_text(metadata['nonce'])
        with patch.object(d, 'validate_inputs'), patch.object(d, 'inspect', return_value=([], {})) as inspect:
            self.assertEqual(0, d.main(log))
            self.assertEqual(str(run.resolve()), inspect.call_args.args[1]['_run_directory'])
        self.assertEqual('forged', json.loads(source.read_text())['_run_directory'])

    def marker(self, phase):
        return record(f"SPIRIT_NATIVE_STARTUP_BOUNDARY nonce={self.metadata['nonce']} phase={phase}",
                      d.STARTUP_LOGGER, 'INFO', 'Server thread')

    def delayed(self, duration=2187, setup=1871):
        # Independent wall/monotonic samples preserve the native attribution oracle.
        start1, end1 = 100008, 100093
        start2 = end1 + setup
        warning = 100100 + duration
        end2, start3 = warning - 7, warning + 1
        def stamp(line, mono):
            wall = datetime.datetime(2026, 10, 17, 11, 31, 0) + datetime.timedelta(milliseconds=mono - 100000)
            return line.replace('17Oct2026 11:31:27.496', wall.strftime('%d%b%Y %H:%M:%S.%f')[:-3])
        def timing(index, phase, mono, next_tick):
            counter = index - (phase == 'START')
            return stamp(record(f"SPIRIT_NATIVE_TICK_TIMING nonce={self.metadata['nonce']} index={index} phase={phase} tick_count={counter} mono_ns={mono * 1000000} mono_ms={mono} sample_end_ns={mono * 1000000} next_tick_ms={next_tick}",
                                d.TIMING_LOGGER, 'INFO', 'Server thread'), mono)
        lag = stamp(record(f"Can't keep up! Is the server overloaded? Running {duration}ms or {duration // 50} ticks behind",
                           'net.minecraft.server.MinecraftServer/', thread='Server thread'), warning)
        next3 = 100100 + 50 + duration // 50 * 50
        return [stamp(self.marker('START_1'), start1), timing(1, 'START', start1, 100050),
                stamp(self.marker('END_1'), end1), timing(1, 'END', end1, 100050),
                stamp(self.marker('START_2'), start2), timing(2, 'START', start2, 100100),
                timing(2, 'END', end2, 100100), lag,
                timing(3, 'START', start3, next3), timing(3, 'END', start3 + 100, next3)]

    def test_current_pinned_native_inputs_authorize_the_exact_cases(self):
        # Full repository gate retains direct native-input validation; synthetic tests
        # below do not claim these archives were rebuilt or native execution passed.
        d.validate_inputs()

    def test_exact_compatibility_categories_are_observations_not_quotas(self):
        lines = []
        for target, (_, mixin, _) in d.TARGETS.items():
            lines += [record(f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'),
                      record(f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}')]
        for name, digest in d.LANGUAGES.items():
            lines.append(record(f'Mod file {d.module_file(name, digest)} is missing mods.toml file',
                                'net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING'))
        failures, counts = d.inspect(lines * 3, self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(len(lines) * 3, sum(counts.values()))
        self.assertEqual(12, counts['native-language-metadata'])

    def test_unknown_repeated_and_malformed_warnings_do_not_fail_correctness(self):
        warning = record('new advisory with no prior classification')
        lines = [warning] * 3501 + [warning.replace('17Oct2026', '99Oct2026'),
                                   warning.replace('[main/WARN]', 'main/WARN]')]
        self.assertEqual([], d.inspect(lines, self.metadata)[0])
        self.assertEqual([], d.inspect([], self.metadata)[0])

    def test_error_severity_is_never_exempted_by_known_warning_text(self):
        target = d.ATTRIBUTE
        familiar = f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'
        for severity in ('ERROR', 'FATAL'):
            lines = [record(familiar, level=severity), record('new advisory', level=severity),
                     record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                            'net.minecraft.server.MinecraftServer/', severity, 'Server thread')]
            self.assertEqual([1, 2, 3], [number for number, _ in d.inspect(lines, self.metadata)[0]])

    def test_actual_operation_failures_block_at_any_severity_or_without_envelope(self):
        messages = ['Registry Object not present: example:missing', 'Registry registration failed',
                    'Error loading KubeJS script', 'KubeJS errors found [2]', 'Failed to save world',
                    'Corrupted chunk data', 'Mixin apply failed SleepingOverhaulMixin',
                    'java.lang.IllegalStateException: unexpected', 'java.lang.AssertionError: invariant',
                    'java.lang.OutOfMemoryError: heap']
        for severity in ('INFO', 'WARN', 'ERROR', 'FATAL'):
            lines = [record(message, level=severity) for message in messages]
            self.assertEqual(len(lines), len(d.inspect(lines, self.metadata)[0]))
        self.assertEqual(len(messages), len(d.inspect(messages, self.metadata)[0]))

    def test_native_startup_notice_preserves_positive_causal_attribution(self):
        lag = record("Can't keep up! Is the server overloaded? Running 5533ms or 110 ticks behind",
                     'net.minecraft.server.MinecraftServer/', thread='Server thread')
        report = {}
        lines = [self.marker('START_1'), self.marker('END_1'), lag, self.marker('START_2')]
        self.assertEqual([], d.inspect(lines, self.metadata, report)[0])
        self.assertTrue(report['startup_boundary_valid'])
        self.assertTrue(report['performance_diagnostics'][0]['accepted'])
        self.assertEqual(5533, report['performance_diagnostics'][0]['duration_ms'])
        self.assertEqual(lag.rstrip(), report['performance_diagnostics'][0]['source_record'])

    def test_delayed_proof_retains_schedule_clock_and_conservative_residual_math(self):
        report = {}
        self.assertEqual([], d.inspect(self.delayed(), self.metadata, report)[0])
        proof = report['performance_diagnostics'][0]['proof']
        self.assertTrue(report['performance_diagnostics'][0]['accepted'])
        self.assertEqual(1871, proof['setup_interval_ms'])
        self.assertEqual(316, proof['nonsetup_residual_upper_ms'])
        self.assertEqual(6, len(proof['schedule_proof']))
        # 2000 ms is a native scheduling threshold for attribution, not a newly
        # invented gameplay budget or a correctness verdict from a WARN alone.
        for duration, eligible in ((3870, True), (3871, False), (5000, False)):
            report = {}
            self.assertEqual([], d.inspect(self.delayed(duration=duration), self.metadata, report)[0])
            self.assertEqual(eligible, report['performance_diagnostics'][0]['accepted'])

    def test_invalid_nonce_clock_order_schedule_and_repeats_lose_attribution_not_correctness(self):
        good = self.delayed()
        cases = [good[:3] + good[4:], good + [good[7]], good[:7] + [good[7]] + good[7:]]
        for index, old, new in ((1, self.metadata['nonce'], '00000000-0000-0000-0000-000000000000'),
                                (3, 'next_tick_ms=100050', 'next_tick_ms=100051'),
                                (5, 'tick_count=1', 'tick_count=2'),
                                (6, '11:31:02.280', '11:31:03.280')):
            lines = good.copy()
            self.assertIn(old, lines[index])
            lines[index] = lines[index].replace(old, new)
            cases.append(lines)
        for lines in cases:
            report = {}
            self.assertEqual([], d.inspect(lines, self.metadata, report)[0])
            self.assertTrue(all(not notice['accepted'] for notice in report['performance_diagnostics']))
            self.assertTrue(report['attribution_findings'])

    def test_fixture_metadata_hash_and_nonce_still_fail_closed(self):
        with self.assertRaises(ValueError):
            d.inspect([], dict(self.metadata, dist='CLIENT'))
        with self.assertRaises(ValueError):
            d.check_hash(b'changed input', '0' * 64)
        Path.home().joinpath('.tmp').mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=Path.home() / '.tmp') as temporary:
            run = Path(temporary)
            log = run / 'logs/latest.log'
            log.parent.mkdir()
            log.write_text(record('new warning'))
            (run / 'native-diagnostic-inputs.json').write_text(json.dumps(self.metadata))
            (run / 'native-diagnostic-nonce').write_text('forged')
            with patch.object(d, 'validate_inputs'), self.assertRaises(ValueError):
                d.main(log)
            self.assertIn('input_error', json.loads((run / 'native-diagnostics.json').read_text()))

    def test_payload_error_word_does_not_replace_structured_severity(self):
        self.assertFalse(d.operation_failed(record('quoted /ERROR] is just text', level='INFO').rstrip()))
        self.assertTrue(d.operation_failed('garbled [main/ERROR] failed'))


class OptionalNativeProbeTest(unittest.TestCase):
    def jarjar(self):
        return record('Failed to load resource META-INF/jarjar/metadata.json from nativeFixture, '
                      'it does not contain dependency information.',
                      logger='net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator/', level='DEBUG')

    def netty(self, array=False):
        if array:
            message = 'jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable'
            heading = ('java.lang.IllegalAccessException: class io.netty.util.internal.PlatformDependent0$7 '
                       '(in module io.netty.common) cannot access class jdk.internal.misc.Unsafe '
                       '(in module java.base) because module java.base does not export jdk.internal.misc to module io.netty.common')
            frame = '\tat io.netty.util.internal.PlatformDependent0$7.run(PlatformDependent0.java:410) ~[netty-common-4.1.82.Final.jar:4.1.82.Final]'
        else:
            message = 'direct buffer constructor: unavailable'
            heading = 'java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled'
            frame = '\tat io.netty.util.internal.ReflectionUtil.trySetAccessible(ReflectionUtil.java:31) ~[netty-common-4.1.82.Final.jar:4.1.82.Final]'
        return [record(message, logger='io.netty.util.internal.PlatformDependent0/', level='DEBUG'), heading, frame]

    def test_proven_optional_debug_probes_are_reported_without_count_budgets(self):
        lines = (self.netty() + self.netty(array=True) + [self.jarjar()]) * 20
        failures, counts = d.scan_operations(lines)
        self.assertEqual([], failures)
        self.assertEqual(40, counts['native-netty-reflection-probe'])
        self.assertEqual(20, counts['native-optional-jarjar-probe'])

    def test_optional_jarjar_rule_cannot_hide_required_loads_or_wrong_envelopes(self):
        for text in [self.jarjar().replace('/DEBUG]', '/ERROR]'),
                     self.jarjar().replace('/DEBUG]', '/FATAL]'),
                     self.jarjar().replace('main/DEBUG', 'Server thread/DEBUG'),
                     self.jarjar().replace('AbstractJarFileDependencyLocator/', 'OtherLocator/'),
                     self.jarjar().replace('metadata.json', 'required.json'),
                     self.jarjar().replace('nativeFixture', 'nativeFixture AssertionError: invariant'),
                     self.jarjar().replace('nativeFixture', 'nativeFixture OutOfMemoryError: heap'),
                     self.jarjar().replace('it does not contain dependency information.', 'JSON is malformed.'),
                     self.jarjar().rstrip() + ' java.lang.AssertionError: failure',
                     self.jarjar().replace('17Oct2026', '99Oct2026')]:
            with self.subTest(text=text):
                self.assertTrue(d.scan_operations([text])[0])

    def test_netty_context_requires_exact_header_heading_and_native_stack_provenance(self):
        for array in (False, True):
            original = self.netty(array)
            variants = []
            for old, new in [('/DEBUG]', '/ERROR]'), ('/DEBUG]', '/FATAL]'),
                             ('/DEBUG]', '/INFO]'), ('main/DEBUG', 'Server thread/DEBUG'),
                             ('io.netty.util.internal.PlatformDependent0/', 'other/'),
                             ('17Oct2026', '99Oct2026')]:
                variants.append([original[0].replace(old, new), *original[1:]])
            variants += [original[1:], original[:2],
                         [original[0], record('unrelated', level='INFO'), *original[1:]],
                         [original[0], '', *original[1:]],
                         [original[0], original[1], '\tat unrelated.Probe.run(Probe.java:1)'],
                         [original[0], original[1] + ' extra failure payload', original[2]]]
            for lines in variants:
                with self.subTest(lines=lines):
                    self.assertTrue(d.scan_operations(lines)[0])

    def test_optional_debug_context_never_hides_uncaught_causes_assertions_or_errors(self):
        for failure in ['java.lang.AssertionError: invariant', 'java.lang.OutOfMemoryError: heap',
                        'java.lang.Error: catastrophic', 'java.lang.Exception: uncaught',
                        'Caused by: java.lang.IllegalStateException: failed',
                        'Exception in thread "server" java.lang.IllegalStateException: uncaught',
                        'Failed to load required registry data', 'Error loading KubeJS script']:
            lines = self.netty() + [failure]
            self.assertIn((len(lines), failure), d.scan_operations(lines)[0])
        self.assertTrue(d.scan_operations([record('Failed to read required save data', level='DEBUG')])[0])

    def test_public_inspect_reports_probes_and_blocks_separate_errors(self):
        metadata = {'dist': 'DEDICATED_SERVER', 'mods': {'forge': '47.4.13'}, 'corrections': {}}
        lines = self.netty() + [self.jarjar()]
        diagnostics = {}
        failures, counts = d.inspect(lines, metadata, diagnostics)
        self.assertEqual([], failures)
        self.assertEqual(1, counts['native-netty-reflection-probe'])
        self.assertEqual(1, counts['native-optional-jarjar-probe'])
        self.assertTrue(diagnostics['passed'])
        error = record('Failed to save required world data', level='ERROR')
        failures, _ = d.inspect(lines + [error], metadata)
        self.assertIn((len(lines) + 1, error.rstrip()), failures)


class CompatDescriptorContractTest(unittest.TestCase):
    def setUp(self):
        self.payloads = {'fixture.Common': b'pinned common target class',
                         'fixture.Client': b'pinned client target class'}
        self.targets = {f'native.Target{index}': ('optional_mod', mixin, hashlib.sha256(payload).hexdigest())
                        for index, (mixin, payload) in enumerate(self.payloads.items())}
        self.config = {'required': False, 'minVersion': '0.8',
                       'package': 'com.bettercontent.bettercompatfixes.mixin',
                       'plugin': 'com.bettercontent.bettercompatfixes.compat.plugin.BetterContentMixinPlugin',
                       'compatibilityLevel': 'JAVA_17', 'injectors': {'defaultRequire': 0},
                       'mixins': ['fixture.Common'], 'client': ['fixture.Client'], 'server': []}

    def validate(self, config, *, payloads=None, omit=()):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, 'w') as jar:
            jar.writestr('better_compat_fixes.mixins.json', json.dumps(config, sort_keys=True, indent=3))
            for mixin, data in (self.payloads if payloads is None else payloads).items():
                if mixin not in omit:
                    jar.writestr('com/bettercontent/bettercompatfixes/mixin/' + mixin.replace('.', '/') + '.class', data)
        archive.seek(0)
        with zipfile.ZipFile(archive) as jar:
            d.validate_compat_inputs(jar, self.targets, client_targets={'fixture.Client'})

    def test_unrelated_inventory_and_descriptor_formatting_do_not_change_contract(self):
        self.validate(self.config)
        self.config['mixins'].insert(0, 'unrelated.NewIntegrationMixin')
        self.config['client'].append('unrelated.NewVisualMixin')
        self.validate(self.config)

    def test_required_package_plugin_mapping_and_loader_policy_drift_fail(self):
        for key, value in [('required', True), ('required', 0), ('package', 'other.mixin'),
                           ('plugin', 'other.Plugin'), ('plugin', None),
                           ('plugin', 'com.bettercontent.bettercompatfixes.mixin.BetterContentMixinPlugin'),
                           ('plugin', 'com.bettercontent.bettercompatfixes.compat.plugin.TreasureUrnMixinPlugin'),
                           ('plugin', 'com.bettercontent.bettercompatfixes.compat.plugin.Oc2rMixinPlugin'),
                           ('compatibilityLevel', 'JAVA_21'),
                           ('minVersion', '0.9'), ('refmap', 'other.refmap.json'),
                           ('injectors', {'defaultRequire': 1}), ('injectors', {'defaultRequire': False})]:
            changed = dict(self.config, **{key: value})
            with self.subTest(key=key, value=value), self.assertRaisesRegex(ValueError, 'better_compat_fixes.mixins.json'):
                self.validate(changed)

    def test_missing_plugin_identity_fails(self):
        changed = dict(self.config)
        del changed['plugin']
        with self.assertRaisesRegex(ValueError, 'better_compat_fixes.mixins.json.*plugin'):
            self.validate(changed)

    def test_relevant_missing_duplicate_or_wrong_side_mapping_fails(self):
        for config in [dict(self.config, mixins=[]), dict(self.config, client=[]),
                       dict(self.config, mixins=['fixture.Common', 'fixture.Common']),
                       dict(self.config, mixins=['fixture.Common', 'fixture.Client']),
                       dict(self.config, mixins=[], server=['fixture.Common']),
                       dict(self.config, mixins=['fixture.Client'], client=['fixture.Common']),
                       dict(self.config, client='fixture.Client')]:
            with self.subTest(config=config), self.assertRaisesRegex(ValueError, 'better_compat_fixes.mixins.json'):
                self.validate(config)

    def test_relevant_class_bytes_and_missing_archive_entries_fail_with_context(self):
        for mixin in self.payloads:
            changed = dict(self.payloads, **{mixin: b'changed target annotations or executable mapping'})
            with self.subTest(mixin=mixin), self.assertRaisesRegex(ValueError, mixin.split('.')[-1] + r'\.class.*sha256 mismatch'):
                self.validate(self.config, payloads=changed)
            with self.assertRaisesRegex(ValueError, mixin.split('.')[-1] + r'\.class.*exactly one'):
                self.validate(self.config, omit={mixin})


if __name__ == '__main__':
    unittest.main()

import datetime
import unittest
import json
import tempfile
from pathlib import Path
from unittest.mock import patch
import check_native_diagnostics as d


def record(message, logger='mixin/', level='WARN', thread='main'):
    return f'[17Oct2026 11:31:27.496] [{thread}/{level}] [{logger}]: {message}\n'


class NativeDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.header = f'Configuration file {d.ROOT}/run-gametest/config/better_spirit_commerce-common.toml is not correct. Correcting'
        self.key = 'Incorrect key wanderingTrader.initialDelay was corrected from null to its default, 48000. '
        self.metadata = {'dist': 'DEDICATED_SERVER', 'mods': {'forge': '47.4.13'},
                         'corrections': {self.header: [self.key]},
                         'nonce': '018c0123-4567-489a-abcd-0123456789ab'}

    def marker(self, phase, **kwargs):
        return record(f"SPIRIT_NATIVE_STARTUP_BOUNDARY nonce={self.metadata['nonce']} phase={phase}",
                      logger=d.STARTUP_LOGGER, level='INFO', thread='Server thread', **kwargs)

    def inspect(self, lines, metadata):
        # Existing classifier contracts retain their original bootstrap records.
        return d.inspect(list(lines) + [self.marker(phase) for phase in d.STARTUP_PHASES], metadata)

    def startup(self, lag=None):
        lines = [self.marker('START_1'), self.marker('END_1'),
                 record("Running test batch 'defaultBatch:1' (24 tests)...",
                        'net.minecraft.gametest.framework.GameTestBatchRunner/', 'INFO', 'Server thread'),
                 record('24 tests are now running!', 'net.minecraft.gametest.framework.GameTestServer/',
                        'INFO', 'Server thread')]
        if lag is not None:
            lines.append(lag)
        return lines + [self.marker('START_2')]

    def test_original_order_startup_lag_is_recorded_without_claiming_repair(self):
        message = "Can't keep up! Is the server overloaded? Running 5533ms or 110 ticks behind"
        diagnostics = {}
        failures, counts = d.inspect(self.startup(record(message, 'net.minecraft.server.MinecraftServer/',
                                                       thread='Server thread')), self.metadata, diagnostics)
        self.assertEqual([], failures)
        self.assertEqual(1, counts['native-first-startup-performance'])
        self.assertTrue(diagnostics['startup_boundary_valid'])
        notice = diagnostics['performance_diagnostics'][0]
        self.assertEqual(5533, notice['duration_ms'])
        self.assertEqual(110, notice['ticks'])
        self.assertEqual(message, notice['message'])
        self.assertEqual(5, notice['source_line'])
        self.assertTrue(notice['accepted'])

    def test_any_positive_consistent_startup_duration_without_tuned_limit(self):
        for ms in [50, 51, 2999, 3000, 5533, 900000, 9223372036854775807]:
            line = record(f"Can't keep up! Is the server overloaded? Running {ms}ms or {ms // 50} ticks behind",
                          'net.minecraft.server.MinecraftServer/', thread='Server thread')
            self.assertEqual([], d.inspect(self.startup(line), self.metadata)[0])

    def test_startup_lag_wrong_identity_numbers_or_payload_fails(self):
        text = "Can't keep up! Is the server overloaded? Running 5533ms or 110 ticks behind"
        valid = record(text, 'net.minecraft.server.MinecraftServer/', thread='Server thread')
        invalid = [valid.replace('[Server thread/WARN]', '[main/WARN]'),
                   valid.replace('[Server thread/WARN]', '[Server thread/ERROR]'),
                   valid.replace('[Server thread/WARN]', '[Server thread/FATAL]'),
                   valid.replace('[Server thread/WARN]', '[Server thread/INFO]'),
                   valid.replace('net.minecraft.server.MinecraftServer/', 'other/'),
                   valid.rstrip() + ' extra payload\n']
        for ms, ticks in [('0', '0'), ('-1', '1'), ('50', '0'), ('5533', '111'),
                          ('NaN', '110'), ('Infinity', '110'), ('55.33', '1'),
                          ('05533', '110'), ('5533', '-110'), ('5533', '0110')]:
            invalid.append(record(f"Can't keep up! Is the server overloaded? Running {ms}ms or {ticks} ticks behind",
                                  'net.minecraft.server.MinecraftServer/', thread='Server thread'))
        for line in invalid:
            with self.subTest(line=line):
                result = {}
                self.assertTrue(d.inspect(self.startup(line), self.metadata, result)[0])
                self.assertEqual(1, len(result['performance_diagnostics']))
                self.assertFalse(result['performance_diagnostics'][0]['accepted'])

    def test_lag_before_end1_after_start2_repeated_or_missing_markers_fails(self):
        lag = record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                     'net.minecraft.server.MinecraftServer/', thread='Server thread')
        cases = [[lag], [lag] + self.startup(), self.startup() + [lag],
                 [self.marker('START_1'), lag, self.marker('END_1'), self.marker('START_2')],
                 self.startup(lag)[:-1], self.startup(lag)[:-1] + [lag, self.marker('START_2')],
                 [self.marker('END_1'), self.marker('START_1'), lag, self.marker('START_2')],
                 self.startup(lag) + [self.marker('START_2')]]
        for lines in cases:
            with self.subTest(lines=lines):
                result = {}
                self.assertTrue(d.inspect(lines, self.metadata, result)[0])
                self.assertTrue(all(not x['accepted'] for x in result['performance_diagnostics']))
        # Even without lag, native runs must prove the fixture boundary was present.
        self.assertTrue(d.inspect([], self.metadata)[0])

    def test_forged_nonce_marker_thread_logger_level_and_continuations_fail(self):
        lag = record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                     'net.minecraft.server.MinecraftServer/', thread='Server thread')
        for replacement in [self.marker('END_1').replace(self.metadata['nonce'], '00000000-0000-0000-0000-000000000000'),
                            self.marker('END_1').replace('[Server thread/INFO]', '[main/INFO]'),
                            self.marker('END_1').replace('[Server thread/INFO]', '[Server thread/WARN]'),
                            self.marker('END_1').replace(d.STARTUP_LOGGER, 'forged/'),
                            self.marker('END_1').rstrip() + ' extra\n']:
            lines = self.startup(lag)
            lines[1] = replacement
            self.assertTrue(d.inspect(lines, self.metadata)[0])
        self.assertTrue(d.inspect(self.startup(lag) + [record('unknown runtime warning')], self.metadata)[0])

    def test_malformed_lag_envelopes_fail_and_still_have_structured_evidence(self):
        lag = record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                     'net.minecraft.server.MinecraftServer/', thread='Server thread')
        for bad in [lag.replace('[Server thread/WARN]', 'Server thread/WARN]'),
                    lag.replace('17Oct2026', '99Oct2026')]:
            report = {}
            self.assertTrue(d.inspect(self.startup(bad), self.metadata, report)[0])
            self.assertEqual(1, len(report['performance_diagnostics']))
            notice = report['performance_diagnostics'][0]
            self.assertEqual(5, notice['source_line'])
            self.assertEqual(3000, notice['duration_ms'])
            self.assertEqual(60, notice['ticks'])
            self.assertFalse(notice['accepted'])

    def test_boundary_timestamps_cannot_be_reordered_around_a_notice(self):
        lag = record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                     'net.minecraft.server.MinecraftServer/', thread='Server thread')
        for index, replacement in [(0, '11:31:28.496'), (1, '11:31:28.496'), (5, '11:31:26.496')]:
            lines = self.startup(lag)
            lines[index] = lines[index].replace('11:31:27.496', replacement)
            self.assertTrue(d.inspect(lines, self.metadata)[0])

    def test_structured_report_written_on_unknown_and_metadata_failure(self):
        Path.home().joinpath('.tmp').mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=Path.home() / '.tmp') as temporary:
            run = Path(temporary)
            log = run / 'logs/latest.log'
            log.parent.mkdir()
            (run / 'native-diagnostic-nonce').write_text(self.metadata['nonce'])
            (run / 'native-diagnostic-inputs.json').write_text(json.dumps(self.metadata))
            lag = record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                         'net.minecraft.server.MinecraftServer/', thread='Server thread')
            log.write_text(''.join(self.startup(lag) + [record('unknown runtime warning')]))
            with patch.object(d, 'validate_inputs'):
                self.assertEqual(1, d.main(log))
                report = json.loads((run / 'native-diagnostics.json').read_text())
                self.assertFalse(report['passed'])
                self.assertTrue(report['failures'])
                self.assertEqual(1, len(report['performance_diagnostics']))
                self.assertEqual(3000, report['performance_diagnostics'][0]['duration_ms'])
                self.assertTrue(report['performance_diagnostics'][0]['accepted'])
                (run / 'native-diagnostic-nonce').write_text('forged')
                with self.assertRaises(ValueError):
                    d.main(log)
                self.assertIn('input_error', json.loads((run / 'native-diagnostics.json').read_text()))

    def delayed(self, duration=2187, setup=1871):
        # Milliseconds in Util's monotonic domain, with independently logged wall time.
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

    def test_delayed_first_setup_debt_requires_explicit_clock_and_schedule_proof(self):
        report = {}
        failures, counts = d.inspect(self.delayed(), self.metadata, report)
        self.assertEqual([], failures)
        self.assertEqual(1, counts['native-delayed-initial-debt-performance'])
        proof = report['performance_diagnostics'][0]['proof']
        self.assertEqual(1871, proof['setup_interval_ms'])
        self.assertEqual(316, proof['nonsetup_residual_ms'])
        self.assertEqual(316, proof['nonsetup_residual_upper_ms'])
        self.assertEqual(6, len(proof['schedule_proof']))
        self.assertTrue(report['tick_timing_valid'])

    def test_delayed_residual_threshold_is_strict_and_does_not_subtract_second_tick(self):
        self.assertEqual([], d.inspect(self.delayed(duration=3870), self.metadata)[0])  # 1999 residual
        for duration in (3871, 5000, 1870, 2000):
            with self.subTest(duration=duration):
                self.assertTrue(d.inspect(self.delayed(duration=duration), self.metadata)[0])
        # One nanosecond of sample uncertainty must not hide a residual of 2000ms.
        lines = self.delayed(duration=3870)
        lines[3] = lines[3].replace('sample_end_ns=100093000000', 'sample_end_ns=100093000001')
        self.assertTrue(d.inspect(lines, self.metadata)[0])

    def test_delayed_missing_reordered_repeated_or_forged_timing_fails(self):
        good = self.delayed()
        cases = [good[:3] + good[4:], good[:6] + good[7:], good[:8] + good[9:],
                 good[:7] + [good[7]] + good[7:], good + [good[8]],
                 good[:6] + [good[8], good[6], good[7]] + good[9:]]
        for index in (1, 3, 5, 6, 8, 9):
            for old, new in [(self.metadata['nonce'], '00000000-0000-0000-0000-000000000000'),
                             ('[Server thread/INFO]', '[main/INFO]'),
                             ('[Server thread/INFO]', '[Server thread/WARN]'),
                             (d.TIMING_LOGGER, 'forged/'), ('tick_count=', 'other_count=')]:
                lines = good.copy(); lines[index] = lines[index].replace(old, new); cases.append(lines)
            lines = good.copy(); lines[index] = lines[index].rstrip() + ' extra\n'; cases.append(lines)
        for lines in cases:
            with self.subTest(lines=lines):
                report = {}
                self.assertTrue(d.inspect(lines, self.metadata, report)[0])
                self.assertTrue(all(not n['accepted'] for n in report['performance_diagnostics']))

    def test_delayed_schedule_counter_clock_and_source_timestamp_mismatches_fail(self):
        for index, old, new in [(1, 'tick_count=0', 'tick_count=1'),
                                (3, 'next_tick_ms=100050', 'next_tick_ms=100051'),
                                (5, 'next_tick_ms=100100', 'next_tick_ms=100101'),
                                (6, 'next_tick_ms=100100', 'next_tick_ms=100101'),
                                (8, 'next_tick_ms=102300', 'next_tick_ms=102301'),
                                (9, 'next_tick_ms=102300', 'next_tick_ms=102301'),
                                (6, 'mono_ms=102280', 'mono_ms=102288'),
                                (8, 'mono_ms=102288', 'mono_ms=102286'),
                                (3, 'sample_end_ns=100093000000', 'sample_end_ns=100092999999'),
                                (6, '11:31:02.280', '11:31:03.280'),
                                (7, '11:31:02.287', '11:31:01.000')]:
            lines = self.delayed(); self.assertIn(old, lines[index])
            lines[index] = lines[index].replace(old, new)
            with self.subTest(index=index, old=old, new=new):
                self.assertTrue(d.inspect(lines, self.metadata)[0])

    def test_delayed_warning_identity_repeats_at_start3_and_later_runtime_stay_blocking(self):
        good = self.delayed()
        cases = [good[:7] + good[8:9] + [good[7]] + good[9:],
                 good + [good[7]], good[:7] + [good[7]] + good[7:]]
        for old, new in [('[Server thread/WARN]', '[main/WARN]'),
                         ('[Server thread/WARN]', '[Server thread/ERROR]'),
                         ('[Server thread/WARN]', '[Server thread/INFO]'),
                         ('2187ms or 43', '2187ms or 44'), ('2187ms', 'NaNms'),
                         ('2187ms', '0ms'), ('net.minecraft.server.MinecraftServer/', 'other/')]:
            lines = good.copy(); lines[7] = lines[7].replace(old, new); cases.append(lines)
        for lines in cases:
            with self.subTest(lines=lines):
                self.assertTrue(d.inspect(lines, self.metadata)[0])

    def test_current_pinned_native_inputs_authorize_the_exact_cases(self):
        d.validate_inputs()

    def test_exact_optional_targets_language_metadata_union_and_native_defaults(self):
        lines = []
        for target, (_, mixin, _) in d.TARGETS.items():
            lines.append(record(f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'))
            lines.append(record(f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}'))
        target = d.ATTRIBUTE
        lines.append(record(f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'))
        for name, digest in d.LANGUAGES.items():
            lines.append(record(f'Mod file {d.module_file(name, digest)} is missing mods.toml file',
                                'net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING'))
        for folder in ('assets', 'data'):
            lines.append(record(f"Assets URL 'union:{d.MAPPED}%23181!/{folder}/.mcassetsroot' uses unexpected schema",
                                'net.minecraft.server.packs.VanillaPackResourcesBuilder/'))
        lines += [record(self.header, 'net.minecraftforge.common.ForgeConfigSpec/CORE'),
                  record(self.key, 'net.minecraftforge.common.ForgeConfigSpec/CORE')]
        failures, counts = self.inspect(lines, self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(len(lines), sum(counts.values()))

    def test_new_targets_errors_bad_logger_bad_thread_extra_payload_and_stack_fail(self):
        target = d.ATTRIBUTE
        valid = f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'
        bad = [record(valid, level='ERROR'), record(valid, level='FATAL'),
               record(valid, logger='other/'), record(valid, thread='worker-1'),
               record(valid + ' other payload'), record(valid.replace('AttributesLibClient', 'NewClient')),
               record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                      'net.minecraft.server.MinecraftServer/'),
               record('Parsing error loading recipe malum:jeed/earthen_might', 'net.minecraft.RecipeManager/', 'ERROR')]
        for line in bad:
            with self.subTest(line=line):
                self.assertTrue(self.inspect([line], self.metadata)[0])
        self.assertTrue(self.inspect([record(valid), 'java.lang.IllegalStateException: unexpected\n'], self.metadata)[0])

    def test_present_target_mod_cannot_be_classified_as_absent(self):
        target, (mod, mixin, _) = next(iter(d.TARGETS.items()))
        self.metadata['mods'][mod] = 'test-present'
        self.assertTrue(self.inspect([record(f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}')], self.metadata)[0])

    def test_sleeping_pseudo_requires_core_present_and_actual_mod_and_runtime_class_absent(self):
        target = d.SLEEPING
        message = f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'
        self.metadata['mods']['better_celestial_events'] = '0.1.0'
        self.metadata['sleeping_server_state_present'] = False
        self.assertEqual([], self.inspect([record(message)], self.metadata)[0])
        self.assertTrue(self.inspect([record(message, level='ERROR')], self.metadata)[0])
        self.assertTrue(self.inspect([record('Mixin apply failed SleepingOverhaulMixin')], self.metadata)[0])
        self.assertTrue(self.inspect([record(message.replace('ServerState', 'OtherState'))], self.metadata)[0])
        self.metadata['mods']['sleepingoverhaul'] = '2.1.0'
        self.assertTrue(self.inspect([record(message)], self.metadata)[0])
        del self.metadata['mods']['sleepingoverhaul']
        self.metadata['sleeping_server_state_present'] = True
        self.assertTrue(self.inspect([record(message)], self.metadata)[0])
        self.metadata['sleeping_server_state_present'] = False
        del self.metadata['mods']['better_celestial_events']
        self.assertTrue(self.inspect([record(message)], self.metadata)[0])

    def test_defaults_require_registered_context_exact_null_value_and_single_creation(self):
        logger = 'net.minecraftforge.common.ForgeConfigSpec/CORE'
        self.assertTrue(self.inspect([record(self.key, logger)], self.metadata)[0])
        for value in (self.key.replace('null', 'wrong'), self.key.replace('48000', '12'), self.key.rstrip()):
            self.assertTrue(self.inspect([record(self.header, logger), record(value, logger)], self.metadata)[0])
        self.assertTrue(self.inspect([record(self.header, logger)] * 2, self.metadata)[0])

    def test_bad_timestamp_missing_brackets_duplicate_and_other_dist_fail(self):
        line = record('unknown')
        for value in (line.replace('17Oct2026', '99Oct2026'), line.replace('[main/WARN]', 'main/WARN]'),
                      record('unknown', level='FATAL').replace('[main/FATAL]', 'main/FATAL]')):
            self.assertTrue(self.inspect([value], self.metadata)[0])
        self.metadata['dist'] = 'CLIENT'
        with self.assertRaises(ValueError):
            self.inspect([], self.metadata)
        with self.assertRaises(ValueError):
            d.check_hash(b'changed native input', '0' * 64)


if __name__ == '__main__':
    unittest.main()

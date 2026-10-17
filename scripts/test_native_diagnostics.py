import unittest
import check_native_diagnostics as d


def record(message, logger='mixin/', level='WARN', thread='main'):
    return f'[17Oct2026 11:31:27.496] [{thread}/{level}] [{logger}]: {message}\n'


class NativeDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.header = f'Configuration file {d.ROOT}/run-gametest/config/better_spirit_commerce-common.toml is not correct. Correcting'
        self.key = 'Incorrect key wanderingTrader.initialDelay was corrected from null to its default, 48000. '
        self.metadata = {'dist': 'DEDICATED_SERVER', 'mods': {'forge': '47.4.13'},
                         'corrections': {self.header: [self.key]}}

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
        failures, counts = d.inspect(lines, self.metadata)
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
                self.assertTrue(d.inspect([line], self.metadata)[0])
        self.assertTrue(d.inspect([record(valid), 'java.lang.IllegalStateException: unexpected\n'], self.metadata)[0])

    def test_present_target_mod_cannot_be_classified_as_absent(self):
        target, (mod, mixin, _) = next(iter(d.TARGETS.items()))
        self.metadata['mods'][mod] = 'test-present'
        self.assertTrue(d.inspect([record(f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}')], self.metadata)[0])

    def test_sleeping_pseudo_requires_core_present_and_actual_mod_and_runtime_class_absent(self):
        target = d.SLEEPING
        message = f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'
        self.metadata['mods']['better_celestial_events'] = '0.1.0'
        self.metadata['sleeping_server_state_present'] = False
        self.assertEqual([], d.inspect([record(message)], self.metadata)[0])
        self.assertTrue(d.inspect([record(message, level='ERROR')], self.metadata)[0])
        self.assertTrue(d.inspect([record('Mixin apply failed SleepingOverhaulMixin')], self.metadata)[0])
        self.assertTrue(d.inspect([record(message.replace('ServerState', 'OtherState'))], self.metadata)[0])
        self.metadata['mods']['sleepingoverhaul'] = '2.1.0'
        self.assertTrue(d.inspect([record(message)], self.metadata)[0])
        del self.metadata['mods']['sleepingoverhaul']
        self.metadata['sleeping_server_state_present'] = True
        self.assertTrue(d.inspect([record(message)], self.metadata)[0])
        self.metadata['sleeping_server_state_present'] = False
        del self.metadata['mods']['better_celestial_events']
        self.assertTrue(d.inspect([record(message)], self.metadata)[0])

    def test_defaults_require_registered_context_exact_null_value_and_single_creation(self):
        logger = 'net.minecraftforge.common.ForgeConfigSpec/CORE'
        self.assertTrue(d.inspect([record(self.key, logger)], self.metadata)[0])
        for value in (self.key.replace('null', 'wrong'), self.key.replace('48000', '12'), self.key.rstrip()):
            self.assertTrue(d.inspect([record(self.header, logger), record(value, logger)], self.metadata)[0])
        self.assertTrue(d.inspect([record(self.header, logger)] * 2, self.metadata)[0])

    def test_bad_timestamp_missing_brackets_duplicate_and_other_dist_fail(self):
        line = record('unknown')
        for value in (line.replace('17Oct2026', '99Oct2026'), line.replace('[main/WARN]', 'main/WARN]'),
                      record('unknown', level='FATAL').replace('[main/FATAL]', 'main/FATAL]')):
            self.assertTrue(d.inspect([value], self.metadata)[0])
        self.metadata['dist'] = 'CLIENT'
        with self.assertRaises(ValueError):
            d.inspect([], self.metadata)
        with self.assertRaises(ValueError):
            d.check_hash(b'changed native input', '0' * 64)


if __name__ == '__main__':
    unittest.main()

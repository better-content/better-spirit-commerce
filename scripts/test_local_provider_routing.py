"""Exercise the actual Kotlin repository helper; never use external repositories.

Synthetic artifacts/builds/logs are retained under build/provider-routing-tests.
A reachable failing loopback Maven repository proves the test detects leakage.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import subprocess
import threading
import unittest
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class LocalProviderRoutingTest(unittest.TestCase):
    def test_available_and_missing_private_artifacts_never_reach_remote(self):
        source = (ROOT / "build.gradle.kts").read_text()
        helper = source.split("// BEGIN local provider routing", 1)[1].split("\n", 1)[1].split(
            "// END local provider routing", 1)[0]
        self.assertNotIn("maven.llamalad7.mixinextras.org", source)
        groups = ["celestial", "dimensiondrink", "fixes", "notifications"]
        for group in groups:
            self.assertIn(f'"bettercontent.local.{group}"', source)
        token = uuid.uuid4().hex
        fixture = ROOT / "build/provider-routing-tests" / token
        fixture.mkdir(parents=True)
        requests = []

        class FailingMaven(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()

            do_HEAD = do_GET

            def log_message(self, *_):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), FailingMaven)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        calls = []
        declarations = []
        checks = []
        for index, group in enumerate(groups):
            artifact = f"provider-{group}"
            jar = fixture / group / f"{artifact}-1.0.jar"
            jar.parent.mkdir()
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("fixture.txt", group)
            # A local Maven stand-in for ForgeGradle's synthetic deobfuscation
            # repository must remain eligible for *_mapped_* coordinates.
            mapped = fixture / "mapped" / f"bettercontent/local/{group}" / artifact / "1.0_mapped_fixture"
            mapped.mkdir(parents=True)
            mapped_jar = mapped / f"{artifact}-1.0_mapped_fixture.jar"
            mapped_jar.write_bytes(jar.read_bytes())
            (mapped / f"{artifact}-1.0_mapped_fixture.pom").write_text(
                f'<project><modelVersion>4.0.0</modelVersion><groupId>bettercontent.local.{group}</groupId>'
                f'<artifactId>{artifact}</artifactId><version>1.0_mapped_fixture</version></project>')
            calls.append(f'localProviderRepository("{group}", "bettercontent.local.{group}", file({json.dumps(str(jar))}))')
            declarations.append(f'val bound{index} = configurations.create("bound{index}")\n'
                                f'dependencies.add(bound{index}.name, "bettercontent.local.{group}:{artifact}:1.0")\n'
                                f'val missing{index} = configurations.create("missing{index}")\n'
                                f'dependencies.add(missing{index}.name, "bettercontent.local.{group}:absent:1.0")\n'
                                f'val mapped{index} = configurations.create("mapped{index}")\n'
                                f'dependencies.add(mapped{index}.name, "bettercontent.local.{group}:{artifact}:1.0_mapped_fixture")\n'
                                f'val missingMapped{index} = configurations.create("missingMapped{index}")\n'
                                f'dependencies.add(missingMapped{index}.name, "bettercontent.local.{group}:absent:1.0_mapped_fixture")')
            checks.append(f'check(bound{index}.singleFile.canonicalFile == file({json.dumps(str(jar))}).canonicalFile)\n'
                          f'check(runCatching {{ missing{index}.resolve() }}.isFailure)\n'
                          f'check(mapped{index}.singleFile.canonicalFile == file({json.dumps(str(mapped_jar))}).canonicalFile)\n'
                          f'check(runCatching {{ missingMapped{index}.resolve() }}.isFailure)')
        public_group = f"probe.control.{token}"
        build = helper + "\nrepositories {\n" + (
            f'maven {{ url = uri("http://127.0.0.1:{server.server_port}"); isAllowInsecureProtocol = true }}\n'
            f'maven {{ url = uri({json.dumps(str(fixture / "mapped"))}) }}\n'
        ) + "\n".join(calls) + "\n}\n" + "\n".join(declarations) + (
            '\nval control = configurations.create("control")\n'
            f'dependencies.add(control.name, "{public_group}:absent:1.0")\n'
            'tasks.register("verifyRouting") { doLast {\n'
        ) + "\n".join(checks) + (
            '\ncheck(runCatching { control.resolve() }.isFailure)\n'
            'println("LOCAL_PROVIDER_ROUTING_PASSED")\n} }\n'
        )
        (fixture / "settings.gradle.kts").write_text('rootProject.name = "provider-routing-fixture"\n')
        (fixture / "build.gradle.kts").write_text(build)
        try:
            with (fixture / "gradle.log").open("w") as log:
                result = subprocess.run(
                    [str(ROOT / "gradlew"), "--no-daemon", "--max-workers=1", "--console=plain",
                     "-p", str(fixture), "verifyRouting"], cwd=ROOT, stdout=log,
                    stderr=subprocess.STDOUT, timeout=180, check=False)
            text = (fixture / "gradle.log").read_text()
            self.assertEqual(result.returncode, 0, f"{fixture}/gradle.log\n{text[-12000:]}")
            self.assertIn("LOCAL_PROVIDER_ROUTING_PASSED", text)
            self.assertTrue(requests, "HTTP control failed to prove the failing repository was reachable")
            prefix = "/" + public_group.replace(".", "/") + "/"
            self.assertTrue(all(path.startswith(prefix) for path in requests),
                            f"Private provider escaped its sealed local repository: {requests}")
            print(f"Four raw and four mapped providers plus eight missing-provider failures verified; no private HTTP requests: {fixture}")
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5)
            (fixture / "http-requests.json").write_text(json.dumps(requests, indent=2))


if __name__ == "__main__":
    unittest.main()

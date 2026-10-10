// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.nostr

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * OI-NOSTR01 — local nostr-rs-relay install/start for wizard + Integrations.
 * Dev path: {@code $STEAKOS_ROOT/var/nostr} (D2 native, D3 loopback, D5 separate data dir).
 */
@Slf4j
@Transactional
class NostrRelayOpsService {

    NostrIntegrationService nostrIntegrationService

    /** Project root (grails-app/../..). */
    private File repoRoot() {
        // grails-app/services/... → up to repo
        File here = new File('.').canonicalFile
        // When running from project root, '.' is fine; also try STEAKOS_ROOT
        String env = System.getenv('STEAKOS_ROOT')
        if (env) return new File(env).canonicalFile
        here
    }

    private File dataDir() {
        new File(repoRoot(), 'var/nostr')
    }

    private File binaryFile() {
        new File(dataDir(), 'bin/nostr-rs-relay')
    }

    private File installScript() {
        new File(repoRoot(), 'scripts/install/nostr/install-nostr-relay.sh')
    }

    private File startScript() {
        new File(dataDir(), 'start-relay.sh')
    }

    Map status(String npubOwner) {
        File bin = binaryFile()
        File data = dataDir()
        Map probe = nostrIntegrationService.probeRelay(npubOwner)
        [
                dataDir      : data.absolutePath,
                binaryPath   : bin.absolutePath,
                binaryPresent: bin.isFile() && bin.canExecute(),
                installScript: installScript().absolutePath,
                installScriptPresent: installScript().isFile(),
                startScript  : startScript().absolutePath,
                relayUrl     : probe.relayUrl,
                reachable    : probe.reachable == true,
                ok           : probe.ok == true,
                detail       : probe.detail,
                probedAt     : probe.probedAt
        ]
    }

    /**
     * Ensure config + binary exist, start relay if not reachable, re-probe.
     * Safe for wizard when Level ≥ 1. Never requires root (project-local).
     */
    Map ensureInstalledAndStarted(String npubOwner) {
        List<String> steps = []
        File data = dataDir()
        data.mkdirs()
        new File(data, 'bin').mkdirs()

        File script = installScript()
        if (!script.isFile()) {
            return status(npubOwner) + [
                    ensured : false,
                    error   : "Install script missing: ${script.absolutePath}",
                    steps   : steps
            ]
        }

        // Always refresh config / start script (idempotent)
        Map installRun = runShell([script.absolutePath], repoRoot(), 180_000)
        steps << "install-script exit=${installRun.exitCode}"
        if (installRun.stdout) steps << installRun.stdout.take(500)
        if (installRun.stderr) steps << installRun.stderr.take(500)

        File bin = binaryFile()
        if (!(bin.isFile() && bin.canExecute())) {
            // Try cargo if available (may already be running elsewhere)
            String cargo = which('cargo')
            if (cargo) {
                File cargoRoot = new File(data, 'cargo-root')
                steps << "cargo install nostr-rs-relay --root ${cargoRoot}"
                // Prefer user-local OpenSSL headers if present (no sudo libssl-dev)
                File home = new File(System.getProperty('user.home'))
                File userSsl = new File(home, '.local/opt/openssl-dev/usr')
                File protocBin = new File(home, '.local/opt/protoc/usr/bin/protoc')
                File protocLib = new File(home, '.local/opt/protoc/usr/lib/x86_64-linux-gnu')
                Map envExtra = [:]
                if (new File(userSsl, 'include/openssl/ssl.h').isFile()) {
                    envExtra.OPENSSL_DIR = userSsl.absolutePath
                    envExtra.OPENSSL_INCLUDE_DIR = new File(userSsl, 'include').absolutePath
                    envExtra.OPENSSL_LIB_DIR = new File(userSsl, 'lib/x86_64-linux-gnu').absolutePath
                    envExtra.PKG_CONFIG_PATH = new File(userSsl, 'lib/x86_64-linux-gnu/pkgconfig').absolutePath
                    steps << "using OPENSSL_DIR=${userSsl.absolutePath}"
                }
                if (protocBin.isFile()) {
                    envExtra.PROTOC = protocBin.absolutePath
                    String path = System.getenv('PATH') ?: '/usr/bin'
                    envExtra.PATH = "${protocBin.parentFile.absolutePath}:${path}"
                    if (protocLib.isDirectory()) {
                        String ld = System.getenv('LD_LIBRARY_PATH') ?: ''
                        envExtra.LD_LIBRARY_PATH = ld ? "${protocLib.absolutePath}:${ld}" : protocLib.absolutePath
                    }
                    steps << "using PROTOC=${protocBin.absolutePath}"
                }
                // Prefer git package (newer deps) then crates.io
                Map cargoRun = runShell(
                        [cargo, 'install', '--git', 'https://github.com/scsibug/nostr-rs-relay',
                         'nostr-rs-relay', '--root', cargoRoot.absolutePath],
                        data,
                        900_000,
                        envExtra
                )
                if (cargoRun.exitCode != 0) {
                    steps << 'git install failed; trying crates.io'
                    cargoRun = runShell(
                            [cargo, 'install', 'nostr-rs-relay', '--root', cargoRoot.absolutePath],
                            data,
                            900_000,
                            envExtra
                    )
                }
                steps << "cargo exit=${cargoRun.exitCode}"
                File built = new File(cargoRoot, 'bin/nostr-rs-relay')
                if (built.isFile()) {
                    bin.parentFile.mkdirs()
                    built.withInputStream { InputStream ins ->
                        bin.withOutputStream { OutputStream outs -> outs << ins }
                    }
                    bin.setExecutable(true)
                    steps << "copied binary to ${bin.absolutePath}"
                } else {
                    steps << (cargoRun.stderr ?: cargoRun.stdout ?: 'cargo did not produce binary').toString().take(800)
                }
            } else {
                steps << 'cargo not on PATH; place binary at var/nostr/bin/nostr-rs-relay'
            }
        }

        Map st = status(npubOwner)
        if (st.reachable) {
            return st + [ensured: true, alreadyRunning: true, steps: steps]
        }

        if (!(bin.isFile() && bin.canExecute())) {
            return st + [
                    ensured: false,
                    error  : "Relay binary missing at ${bin.absolutePath}. Run scripts/install/nostr/install-nostr-relay.sh or cargo install.",
                    steps  : steps
            ]
        }

        // Start in background
        File start = startScript()
        if (!start.isFile()) {
            // Minimal start script
            start.text = """#!/usr/bin/env bash
set -euo pipefail
BIN="${bin.absolutePath}"
CFG="${new File(data, 'config.toml').absolutePath}"
DB="${data.absolutePath}"
exec "\$BIN" --config "\$CFG" --db "\$DB"
"""
            start.setExecutable(true)
            steps << 'wrote start-relay.sh'
        }

        File logFile = new File(data, 'relay.log')
        ProcessBuilder pb = new ProcessBuilder('/bin/bash', start.absolutePath)
        pb.directory(data)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        Process p = pb.start()
        steps << "started pid=${p.pid()} log=${logFile.absolutePath}"

        // Wait briefly for listen
        boolean up = false
        for (int i = 0; i < 20; i++) {
            Thread.sleep(500)
            Map probe = nostrIntegrationService.probeRelay(npubOwner)
            if (probe.reachable) {
                up = true
                break
            }
        }

        st = status(npubOwner)
        st + [
                ensured: up,
                error  : up ? null : 'Relay started but not reachable yet — check var/nostr/relay.log',
                steps  : steps
        ]
    }

    private static String which(String cmd) {
        try {
            Process p = new ProcessBuilder('which', cmd).start()
            p.waitFor()
            if (p.exitValue() == 0) {
                return p.inputStream.text?.trim()
            }
        } catch (ignored) {}
        null
    }

    private static Map runShell(List<String> cmd, File cwd, long timeoutMs, Map extraEnv = [:]) {
        ProcessBuilder pb = new ProcessBuilder(cmd)
        pb.directory(cwd)
        pb.redirectErrorStream(false)
        if (extraEnv) {
            Map env = pb.environment()
            extraEnv.each { k, v -> if (v != null) env.put(k.toString(), v.toString()) }
        }
        Process p = pb.start()
        StringBuilder out = new StringBuilder()
        StringBuilder err = new StringBuilder()
        Thread tOut = Thread.start { out << p.inputStream.text }
        Thread tErr = Thread.start { err << p.errorStream.text }
        boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            p.destroyForcibly()
            tOut.join(1000)
            tErr.join(1000)
            return [exitCode: -1, stdout: out.toString(), stderr: err.toString() + '\n(timeout)']
        }
        tOut.join(2000)
        tErr.join(2000)
        [exitCode: p.exitValue(), stdout: out.toString(), stderr: err.toString()]
    }
}

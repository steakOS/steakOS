// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.security

import groovy.util.logging.Slf4j

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One-time setup token file, mode 0600. The API never returns the token.
 * Production requires it for anonymous setup mutations. Dev does not.
 */
@Slf4j
class SetupTokenService {

    /** Test hook. When set, the token file lives in this directory. */
    File directoryOverride

    /** Test hook. When true, enforcement does not depend on the process environment. */
    boolean forceEnforce = false

    boolean enforced() {
        if (forceEnforce) {
            return true
        }
        if (ProductionSecrets.productionFlag()) {
            return true
        }
        String flag = System.getenv('STEAKOS_SETUP_TOKEN_REQUIRED')
        return flag == 'true' || flag == '1'
    }

    File tokenFile() {
        File dir = directoryOverride ?: defaultDirectory()
        return new File(dir, 'setup-token')
    }

    void ensureMaterial() {
        if (!enforced()) {
            return
        }
        File file = tokenFile()
        if (file.isFile() && file.length() > 0) {
            return
        }
        File parent = file.parentFile
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException('Could not create the setup token directory')
        }
        byte[] bytes = new byte[32]
        new SecureRandom().nextBytes(bytes)
        StringBuilder tokenBuilder = new StringBuilder(bytes.length * 2)
        for (byte b : bytes) {
            tokenBuilder.append(String.format('%02x', b & 0xff))
        }
        String token = tokenBuilder.toString()
        file.setText(token, 'UTF-8')
        ownerOnly(file)
        log.info('Setup token written to {}', file.absolutePath)
    }

    boolean allows(String presented) {
        if (!enforced()) {
            return true
        }
        File file = tokenFile()
        if (!file.isFile()) {
            return false
        }
        String expected = file.getText('UTF-8')?.trim()
        return constantTimeEquals(expected, presented?.trim())
    }

    void discard() {
        File file = tokenFile()
        if (!file.isFile()) {
            return
        }
        if (!file.delete()) {
            log.warn('Setup token file could not be removed at {}', file.absolutePath)
            return
        }
        log.info('Setup token file removed')
    }

    private static File defaultDirectory() {
        String home = System.getProperty('steakos.home') ?: System.getenv('STEAKOS_HOME') ?: System.getProperty('user.dir')
        return new File(home, 'run')
    }

    private static void ownerOnly(File file) {
        try {
            if (!FileSystems.default.supportedFileAttributeViews().contains('posix')) {
                return
            }
            Path path = file.toPath()
            Set<PosixFilePermission> perms = [
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
            ] as Set
            Files.setPosixFilePermissions(path, perms)
        } catch (Exception ignored) {
            // Windows and some containers have no POSIX view.
        }
    }

    static boolean constantTimeEquals(String left, String right) {
        byte[] a = (left ?: '').getBytes('UTF-8')
        byte[] b = (right ?: '').getBytes('UTF-8')
        return MessageDigest.isEqual(a, b)
    }
}

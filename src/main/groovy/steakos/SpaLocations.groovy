// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos

import java.nio.file.Paths

/** Finds prefix/web for a packaged install (java -jar prefix/app/steakOS.jar). */
class SpaLocations {

    static File webRoot() {
        List<String> homes = []
        String prop = System.getProperty('steakos.home')
        if (prop) homes << prop
        String env = System.getenv('STEAKOS_HOME')
        if (env) homes << env
        String fromJar = homeFromJar()
        if (fromJar) homes << fromJar
        for (String home : homes) {
            File web = new File(home, 'web')
            if (web.isDirectory()) return web
        }
        return null
    }

    private static String homeFromJar() {
        try {
            URL loc = Application.protectionDomain?.codeSource?.location
            if (loc == null) return null
            String path = loc.toURI().schemeSpecificPart ?: loc.path
            if (!path) return null
            int bang = path.indexOf('!')
            if (bang >= 0) path = path.substring(0, bang)
            if (path.startsWith('file:')) path = path.substring('file:'.length())
            path = URLDecoder.decode(path, 'UTF-8')
            File jar = Paths.get(path).toFile()
            // prefix/app/steakOS.jar → prefix
            File appDir = jar.parentFile
            File prefix = appDir?.parentFile
            return prefix?.absolutePath
        } catch (ignored) {
            return null
        }
    }
}

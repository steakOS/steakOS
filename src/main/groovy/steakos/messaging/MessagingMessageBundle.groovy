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

package steakos.messaging

/**
 * OI-I18N02 PR4a / PL-TR3 — EN/DE subject+body packs for partner outbound email.
 * Mirrors {@link steakos.core.PdfMessageBundle}; not catalogue text.
 */
class MessagingMessageBundle {

    private static final Map<String, Properties> CACHE = [:]
    private static final Object LOCK = new Object()

    final String language
    private final Properties props

    private MessagingMessageBundle(String language, Properties props) {
        this.language = language
        this.props = props
    }

    static MessagingMessageBundle forLanguage(String lang) {
        String key = (lang == 'de') ? 'de' : 'en'
        synchronized (LOCK) {
            Properties p = CACHE[key]
            if (p == null) {
                p = loadPack(key)
                CACHE[key] = p
            }
            return new MessagingMessageBundle(key, p)
        }
    }

    static MessagingMessageBundle forLocale(String localeCode) {
        String lang = 'en'
        if (localeCode) {
            String primary = localeCode.replace('-', '_').split('_')[0]?.toLowerCase()
            if (primary == 'de') lang = 'de'
        }
        forLanguage(lang)
    }

    String get(String key) {
        String v = props.getProperty(key)
        if (v != null) return v
        if (language != 'en') {
            return forLanguage('en').get(key)
        }
        key
    }

    String format(String key, Object... args) {
        String pattern = get(key)
        if (!args) return pattern
        try {
            return java.text.MessageFormat.format(pattern, args)
        } catch (ignored) {
            return pattern
        }
    }

    private static Properties loadPack(String lang) {
        Properties p = new Properties()
        String path = "steakos/messaging/messages_${lang}.properties"
        InputStream is = MessagingMessageBundle.classLoader.getResourceAsStream(path)
        if (is == null) {
            is = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
        }
        if (is != null) {
            try {
                p.load(new InputStreamReader(is, 'UTF-8'))
            } finally {
                is.close()
            }
        }
        p
    }
}

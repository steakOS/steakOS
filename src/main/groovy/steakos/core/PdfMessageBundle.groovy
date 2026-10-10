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

package steakos.core

/**
 * OI-I18N02 — EN/DE label packs for commercial PDFs (labels/phrases only; not catalogue text).
 */
class PdfMessageBundle {

    private static final Map<String, Properties> CACHE = [:]
    private static final Object LOCK = new Object()

    final String language
    private final Properties props

    private PdfMessageBundle(String language, Properties props) {
        this.language = language
        this.props = props
    }

    static PdfMessageBundle forLanguage(String lang) {
        String key = (lang == 'de') ? 'de' : 'en'
        synchronized (LOCK) {
            Properties p = CACHE[key]
            if (p == null) {
                p = loadPack(key)
                CACHE[key] = p
            }
            return new PdfMessageBundle(key, p)
        }
    }

    static PdfMessageBundle forLocale(String localeCode) {
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

    /** Structural labels map for {@link Iso6422TradeDocumentPdfBuilder}. */
    Map<String, String> structuralLabels(String currencyCode) {
        String cur = currencyCode ?: 'EUR'
        [
                consignor     : get('pdf.consignor'),
                dateAndRef    : get('pdf.dateAndRef'),
                no            : get('pdf.no'),
                status        : get('pdf.status'),
                currency      : get('pdf.currency'),
                buyerOther    : get('pdf.buyerOther'),
                terms         : get('pdf.terms'),
                pos           : get('pdf.pos'),
                description   : get('pdf.description'),
                qty           : get('pdf.qty'),
                unitPrice     : format('pdf.unitPrice', cur),
                amount        : format('pdf.amount', cur),
                noLines       : get('pdf.noLines'),
                freeDisposal  : get('pdf.freeDisposal'),
                bank          : get('pdf.bank'),
                amounts       : get('pdf.amounts'),
                footer        : get('pdf.footer'),
                issueDate     : get('pdf.issueDate'),
                orderDate     : get('pdf.orderDate'),
                validUntil    : get('pdf.validUntil'),
                dueDate       : get('pdf.dueDate'),
                taxIdVat      : get('pdf.taxIdVat'),
                country       : get('pdf.country'),
                partnerDefault: get('pdf.partner.customer'),
        ]
    }

    private static Properties loadPack(String lang) {
        Properties p = new Properties()
        String path = "steakos/pdf/messages_${lang}.properties"
        InputStream is = PdfMessageBundle.classLoader.getResourceAsStream(path)
        if (is == null) {
            is = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
        }
        if (is != null) {
            try {
                // ISO-8859-1 for classic .properties; DE file uses \\u escapes where needed
                p.load(new InputStreamReader(is, 'UTF-8'))
            } finally {
                is.close()
            }
        }
        p
    }
}

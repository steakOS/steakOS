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
 * Minimal PDF 1.4 builder (Helvetica, single page).
 * Commercial trade docs (quote / order / PO / invoice) use
 * {@link Iso6422TradeDocumentPdfBuilder} (ISO 6422-1:2010 A4) instead.
 */
class SimplePdfBuilder {

    static byte[] build(List<String> textLines) {
        StringBuilder content = new StringBuilder()
        content.append('BT\n/F1 11 Tf\n14 TL\n50 800 Td\n')
        textLines.eachWithIndex { String line, int i ->
            String safe = escapePdfText(line)
            if (i == 0) {
                content.append("/F1 18 Tf\n(${safe}) Tj\n/F1 11 Tf\nT*\n")
            } else if (safe.isEmpty()) {
                content.append('T*\n')
            } else {
                content.append("(${safe}) Tj\nT*\n")
            }
        }
        content.append('ET')
        byte[] streamBytes = content.toString().getBytes('ISO-8859-1')

        ByteArrayOutputStream out = new ByteArrayOutputStream()
        List offsets = []
        Closure write = { String s -> out.write(s.getBytes('ISO-8859-1')) }

        write('%PDF-1.4\n')
        offsets << out.size()
        write('1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n')
        offsets << out.size()
        write('2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n')
        offsets << out.size()
        write('3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>endobj\n')
        offsets << out.size()
        write("4 0 obj<< /Length ${streamBytes.length} >>stream\n")
        out.write(streamBytes)
        write('\nendstream\nendobj\n')
        offsets << out.size()
        write('5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n')

        int xref = out.size()
        write('xref\n0 6\n')
        write('0000000000 65535 f \n')
        offsets.each { int off ->
            write(String.format('%010d 00000 n \n', off))
        }
        write("trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n")
        return out.toByteArray()
    }

    static String escapePdfText(String s) {
        if (s == null) return ''
        StringBuilder b = new StringBuilder()
        s.toString().each { ch ->
            char c = ch as char
            if (c == '\\' as char) b.append('\\\\')
            else if (c == '(' as char) b.append('\\(')
            else if (c == ')' as char) b.append('\\)')
            else if (c < 32 || c > 126) b.append('?')
            else b.append(c)
        }
        return b.toString()
    }
}

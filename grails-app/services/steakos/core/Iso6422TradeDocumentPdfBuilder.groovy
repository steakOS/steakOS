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

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter

import java.awt.Color
import java.text.SimpleDateFormat

/**
 * ISO 6422-1:2010 Layout key for trade documents — A4 paper-based rendering.
 *
 * Margins (standard): left 20 mm (filing), top 10 mm (gripper).
 * Image area ≈ 183 × 280 mm on A4 (ISO 216).
 *
 * Zones used (adapted for quote / sales order / purchase order / invoice):
 *  - Consignor (exporter / seller)
 *  - Consignee / other party (buyer or vendor)
 *  - Date and reference number
 *  - Terms of delivery and payment
 *  - Description of goods (line-item table with wrapping)
 *  - Free disposal (totals, bank, notes, footer)
 */
class Iso6422TradeDocumentPdfBuilder {

    /** 1 mm in PDF points (1/72 in). */
    static final float MM = 2.8346457f

    static final float MARGIN_LEFT = 20f * MM
    static final float MARGIN_TOP = 10f * MM
    static final float MARGIN_RIGHT = 7f * MM
    static final float MARGIN_BOTTOM = 7f * MM

    private static final Color BOX = new Color(0x33, 0x33, 0x33)
    private static final Color HEADER_BG = new Color(0xF2, 0xF2, 0xF2)
    private static final Color RULE = new Color(0x99, 0x99, 0x99)

    private static final Font F_LABEL = FontFactory.getFont(FontFactory.HELVETICA, 7f, Font.NORMAL, BOX)
    private static final Font F_BODY = FontFactory.getFont(FontFactory.HELVETICA, 9f, Font.NORMAL, Color.BLACK)
    private static final Font F_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, Font.NORMAL, Color.BLACK)
    private static final Font F_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, Font.NORMAL, Color.BLACK)
    private static final Font F_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8f, Font.NORMAL, Color.BLACK)
    private static final Font F_FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 7f, Font.NORMAL, new Color(0x66, 0x66, 0x66))

    /**
     * @param doc map keys:
     *   documentType (String) — e.g. QUOTE, SALES ORDER, PURCHASE ORDER, INVOICE
     *   documentNumber, status, currencyCode
     *   issueDate, dueDate / validUntil / orderDate (Date or String)
     *   seller (Map from TradeDocumentCompanyService)
     *   partnerLabel (String) — Consignee / Bill to / Vendor
     *   partnerName, partnerLines (List&lt;String&gt;)
     *   termsLines (List&lt;String&gt;)
     *   lines (List of Map: lineNo, name, description, quantity, unit, unitPrice, lineTotal)
     *   totals (List of [label, value] or Map label→value)
     *   notesLines (List&lt;String&gt;)
     *   freeDisposalLines (List&lt;String&gt;) — bank / payment rails
     *   labels (Map) — optional OI-I18N02 structural label pack (EN default)
     */
    static byte[] build(Map doc) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        Document document = new Document(PageSize.A4, MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM)
        PdfWriter writer = PdfWriter.getInstance(document, baos)
        writer.setStrictImageSequence(true)
        document.open()

        document.add(headerBand(doc))
        document.add(spacer(4f))
        document.add(partiesAndRefs(doc))
        document.add(spacer(4f))
        document.add(termsBox(doc))
        document.add(spacer(6f))
        document.add(goodsTable(doc))
        document.add(spacer(6f))
        document.add(totalsAndFree(doc))
        document.add(spacer(8f))
        document.add(footer(doc))

        document.close()
        return baos.toByteArray()
    }

    private static Map labelsOf(Map doc) {
        (doc.labels instanceof Map) ? (Map) doc.labels : [:]
    }

    private static String L(Map doc, String key, String fallback) {
        Map labels = labelsOf(doc)
        Object v = labels[key]
        (v != null && v.toString()) ? v.toString() : fallback
    }

    private static PdfPTable headerBand(Map doc) {
        PdfPTable t = new PdfPTable(2)
        t.setWidthPercentage(100f)
        t.setWidths([62f, 38f] as float[])

        Map seller = (doc.seller instanceof Map) ? (Map) doc.seller : [:]
        Paragraph consignor = new Paragraph()
        consignor.add(new Chunk(L(doc, 'consignor', 'Consignor (exporter / seller)'), F_LABEL))
        consignor.add(Chunk.NEWLINE)
        consignor.add(new Chunk(nz(seller.name), F_BODY_BOLD))
        if (seller.legalForm) {
            consignor.add(Chunk.NEWLINE)
            consignor.add(new Chunk(nz(seller.legalForm), F_SMALL))
        }
        (seller.addressLines as List)?.each { Object line ->
            consignor.add(Chunk.NEWLINE)
            consignor.add(new Chunk(nz(line), F_BODY))
        }
        List meta = []
        if (seller.taxId) meta << "${L(doc, 'taxIdVat', 'Tax ID / VAT')}: ${seller.taxId}"
        if (seller.countryCode) meta << "${L(doc, 'country', 'Country')}: ${seller.countryCode}"
        if (meta) {
            consignor.add(Chunk.NEWLINE)
            consignor.add(new Chunk(meta.join(' · '), F_SMALL))
        }

        Paragraph right = new Paragraph()
        right.add(new Chunk(nz(doc.documentType), F_TITLE))
        right.add(Chunk.NEWLINE)
        right.add(new Chunk(L(doc, 'dateAndRef', 'Date and reference number'), F_LABEL))
        right.add(Chunk.NEWLINE)
        right.add(new Chunk("${L(doc, 'no', 'No.')}: ${nz(doc.documentNumber)}", F_BODY_BOLD))
        if (doc.status) {
            right.add(Chunk.NEWLINE)
            right.add(new Chunk("${L(doc, 'status', 'Status')}: ${nz(doc.status)}", F_SMALL))
        }
        dateRows(doc).each { String row ->
            right.add(Chunk.NEWLINE)
            right.add(new Chunk(row, F_BODY))
        }
        if (doc.currencyCode) {
            right.add(Chunk.NEWLINE)
            right.add(new Chunk("${L(doc, 'currency', 'Currency')}: ${nz(doc.currencyCode)}", F_BODY))
        }

        t.addCell(boxCell(consignor, Element.ALIGN_LEFT))
        t.addCell(boxCell(right, Element.ALIGN_LEFT))
        return t
    }

    private static PdfPTable partiesAndRefs(Map doc) {
        PdfPTable t = new PdfPTable(2)
        t.setWidthPercentage(100f)
        t.setWidths([50f, 50f] as float[])

        Paragraph partner = new Paragraph()
        partner.add(new Chunk(nz(doc.partnerLabel ?: L(doc, 'partnerDefault', 'Consignee')), F_LABEL))
        partner.add(Chunk.NEWLINE)
        partner.add(new Chunk(nz(doc.partnerName) ?: '—', F_BODY_BOLD))
        (doc.partnerLines as List)?.each { Object line ->
            if (line) {
                partner.add(Chunk.NEWLINE)
                partner.add(new Chunk(nz(line), F_BODY))
            }
        }

        Paragraph other = new Paragraph()
        other.add(new Chunk(L(doc, 'buyerOther', 'Buyer (if other than consignee) / references'), F_LABEL))
        other.add(Chunk.NEWLINE)
        List refs = (doc.referenceLines as List) ?: []
        if (refs) {
            refs.eachWithIndex { Object line, int i ->
                if (i > 0) other.add(Chunk.NEWLINE)
                other.add(new Chunk(nz(line), F_BODY))
            }
        } else {
            other.add(new Chunk('—', F_BODY))
        }

        t.addCell(boxCell(partner, Element.ALIGN_LEFT))
        t.addCell(boxCell(other, Element.ALIGN_LEFT))
        return t
    }

    private static PdfPTable termsBox(Map doc) {
        PdfPTable t = new PdfPTable(1)
        t.setWidthPercentage(100f)
        Paragraph p = new Paragraph()
        p.add(new Chunk(L(doc, 'terms', 'Terms of delivery and payment'), F_LABEL))
        List terms = (doc.termsLines as List)?.findAll { it } ?: ['—']
        terms.each { Object line ->
            p.add(Chunk.NEWLINE)
            p.add(new Chunk(nz(line), F_BODY))
        }
        t.addCell(boxCell(p, Element.ALIGN_LEFT))
        return t
    }

    private static PdfPTable goodsTable(Map doc) {
        // Pos | Description | Qty | Unit price | Amount
        PdfPTable t = new PdfPTable(5)
        t.setWidthPercentage(100f)
        t.setWidths([8f, 44f, 12f, 18f, 18f] as float[])
        t.setHeaderRows(1)
        t.setSplitLate(false)

        String cur = nz(doc.currencyCode ?: 'EUR')
        String posH = L(doc, 'pos', 'Pos')
        String descH = L(doc, 'description', 'Description of goods')
        String qtyH = L(doc, 'qty', 'Qty')
        String unitH = L(doc, 'unitPrice', "Unit price (${cur})")
        String amtH = L(doc, 'amount', "Amount (${cur})")
        [posH, descH, qtyH, unitH, amtH].each { String h ->
            PdfPCell c = new PdfPCell(new Phrase(h, F_LABEL))
            c.setBackgroundColor(HEADER_BG)
            c.setBorderColor(RULE)
            c.setPadding(4f)
            c.setHorizontalAlignment(h == descH || h == posH ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT)
            t.addCell(c)
        }

        List lines = (doc.lines as List) ?: []
        if (!lines) {
            PdfPCell empty = new PdfPCell(new Phrase(L(doc, 'noLines', '(no line items)'), F_BODY))
            empty.setColspan(5)
            empty.setBorderColor(RULE)
            empty.setPadding(6f)
            t.addCell(empty)
            return t
        }

        lines.eachWithIndex { Object raw, int idx ->
            Map lm = (raw instanceof Map) ? (Map) raw : [:]
            String pos = lm.lineNo != null ? lm.lineNo.toString() : String.valueOf(idx + 1)
            String desc = buildDescription(lm)
            String qty = formatQty(lm.quantity)
            String unit = formatMoney(lm.unitPrice != null ? lm.unitPrice : lm.unitPriceEur)
            String amount = formatMoney(lm.lineTotal != null ? lm.lineTotal : lm.lineTotalEur)

            t.addCell(dataCell(pos, Element.ALIGN_LEFT))
            t.addCell(dataCell(desc, Element.ALIGN_LEFT))
            t.addCell(dataCell(qty, Element.ALIGN_RIGHT))
            t.addCell(dataCell(unit, Element.ALIGN_RIGHT))
            t.addCell(dataCell(amount, Element.ALIGN_RIGHT))
        }
        return t
    }

    private static PdfPTable totalsAndFree(Map doc) {
        PdfPTable outer = new PdfPTable(2)
        outer.setWidthPercentage(100f)
        outer.setWidths([55f, 45f] as float[])

        Paragraph free = new Paragraph()
        free.add(new Chunk(L(doc, 'freeDisposal', 'Free disposal / payment details'), F_LABEL))
        List freeLines = []
        (doc.freeDisposalLines as List)?.each { freeLines << it }
        (doc.notesLines as List)?.each { freeLines << it }
        Map seller = (doc.seller instanceof Map) ? (Map) doc.seller : [:]
        String bankLabel = L(doc, 'bank', 'Bank')
        (seller.bankLines as List)?.each { freeLines << "${bankLabel}: ${it}" }
        if (!freeLines) freeLines << '—'
        freeLines.each { Object line ->
            free.add(Chunk.NEWLINE)
            free.add(new Chunk(nz(line), F_SMALL))
        }

        PdfPTable totals = new PdfPTable(2)
        totals.setWidthPercentage(100f)
        totals.setWidths([55f, 45f] as float[])
        List totalRows = normalizeTotals(doc)
        if (!totalRows) {
            totalRows = [[L(doc, 'amounts', 'Total'), formatMoney(doc.totalAmount)]]
        }
        totalRows.eachWithIndex { List row, int i ->
            boolean last = i == totalRows.size() - 1
            Font lf = last ? F_BODY_BOLD : F_BODY
            Font vf = last ? F_BODY_BOLD : F_BODY
            PdfPCell l = new PdfPCell(new Phrase(nz(row[0]), lf))
            l.setBorder(Rectangle.NO_BORDER)
            l.setPadding(2f)
            PdfPCell v = new PdfPCell(new Phrase(nz(row[1]), vf))
            v.setBorder(Rectangle.NO_BORDER)
            v.setHorizontalAlignment(Element.ALIGN_RIGHT)
            v.setPadding(2f)
            totals.addCell(l)
            totals.addCell(v)
        }

        Paragraph totalsWrap = new Paragraph()
        totalsWrap.add(new Chunk(L(doc, 'amounts', 'Amounts'), F_LABEL))
        totalsWrap.add(Chunk.NEWLINE)
        // embed table via nested — OpenPDF: add table as element in cell
        PdfPCell freeCell = boxCell(free, Element.ALIGN_LEFT)
        PdfPCell totalsCell = new PdfPCell()
        totalsCell.setBorderColor(BOX)
        totalsCell.setBorderWidth(0.6f)
        totalsCell.setPadding(6f)
        totalsCell.addElement(new Paragraph(new Chunk(L(doc, 'amounts', 'Amounts'), F_LABEL)))
        totalsCell.addElement(totals)

        outer.addCell(freeCell)
        outer.addCell(totalsCell)
        return outer
    }

    private static Paragraph footer(Map doc) {
        Paragraph p = new Paragraph()
        p.setAlignment(Element.ALIGN_CENTER)
        String pattern = L(doc, 'footer',
                'ISO 6422-1:2010 layout key · A4 (ISO 216) · Generated by steakOS · {0}')
        String text
        try {
            text = java.text.MessageFormat.format(pattern, formatDateTime(new Date()))
        } catch (ignored) {
            text = "${pattern} ${formatDateTime(new Date())}"
        }
        p.add(new Chunk(text, F_FOOTER))
        return p
    }

    // ---- helpers ----

    private static PdfPCell boxCell(Paragraph content, int align) {
        PdfPCell c = new PdfPCell()
        c.setBorderColor(BOX)
        c.setBorderWidth(0.6f)
        c.setPadding(6f)
        c.setHorizontalAlignment(align)
        c.addElement(content)
        return c
    }

    private static PdfPCell dataCell(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(nz(text), F_BODY))
        c.setBorderColor(RULE)
        c.setPadding(4f)
        c.setHorizontalAlignment(align)
        c.setVerticalAlignment(Element.ALIGN_TOP)
        return c
    }

    private static Paragraph spacer(float pt) {
        Paragraph p = new Paragraph(' ')
        p.setLeading(pt)
        return p
    }

    private static String buildDescription(Map lm) {
        String name = nz(lm.name ?: lm.description)
        String extra = lm.description && lm.name && lm.description.toString().trim() != lm.name.toString().trim() ?
                lm.description.toString().trim() : null
        String unit = lm.unit ? " [${lm.unit}]" : ''
        if (extra) return "${name}${unit}\n${extra}"
        return "${name}${unit}"
    }

    private static List dateRows(Map doc) {
        List rows = []
        if (doc.issueDate) rows << "${L(doc, 'issueDate', 'Issue date')}: ${formatDate(doc.issueDate)}"
        if (doc.orderDate) rows << "${L(doc, 'orderDate', 'Order date')}: ${formatDate(doc.orderDate)}"
        if (doc.validUntil) rows << "${L(doc, 'validUntil', 'Valid until')}: ${formatDate(doc.validUntil)}"
        if (doc.dueDate) rows << "${L(doc, 'dueDate', 'Due date')}: ${formatDate(doc.dueDate)}"
        rows
    }

    private static List normalizeTotals(Map doc) {
        Object raw = doc.totals
        if (raw instanceof List) {
            return (raw as List).collect { Object row ->
                if (row instanceof List && (row as List).size() >= 2) {
                    return [nz((row as List)[0]), nz((row as List)[1])]
                }
                if (row instanceof Map) {
                    Map m = row as Map
                    return [nz(m.label ?: m.key), nz(m.value)]
                }
                return [nz(row), '']
            }
        }
        if (raw instanceof Map) {
            return (raw as Map).collect { k, v -> [nz(k), nz(v)] }
        }
        return []
    }

    static String formatDate(Object d) {
        if (d == null) return '—'
        if (d instanceof Date) return new SimpleDateFormat('yyyy-MM-dd').format(d as Date)
        String s = d.toString().trim()
        if (s.isLong()) {
            return new SimpleDateFormat('yyyy-MM-dd').format(new Date(s.toLong()))
        }
        return s ?: '—'
    }

    static String formatDateTime(Date d) {
        if (!d) return '—'
        new SimpleDateFormat("yyyy-MM-dd HH:mm").format(d)
    }

    static String formatMoney(Object v) {
        if (v == null || v == '') return '0.00'
        try {
            BigDecimal bd = v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString())
            return String.format(Locale.US, '%,.2f', bd)
        } catch (ignored) {
            return v.toString()
        }
    }

    static String formatQty(Object v) {
        if (v == null) return '1'
        try {
            BigDecimal bd = v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString())
            if (bd.stripTrailingZeros().scale() <= 0) return bd.toBigInteger().toString()
            return bd.stripTrailingZeros().toPlainString()
        } catch (ignored) {
            return v.toString()
        }
    }

    private static String nz(Object v) {
        v == null ? '' : v.toString()
    }
}

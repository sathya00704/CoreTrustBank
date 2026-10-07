package com.coretrust.bank.statement.service;

import com.coretrust.bank.statement.dto.Statement;
import com.coretrust.bank.statement.dto.StatementLine;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
public class StatementPdfRenderer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);

    public byte[] render(Statement s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(s.bankName() + " - Account Statement", TITLE));
            document.add(new Paragraph(" ", NORMAL));
            document.add(new Paragraph("Account holder: " + s.accountHolder(), NORMAL));
            document.add(new Paragraph("Account number: %s (%s)".formatted(s.accountNumber(), s.accountType()), NORMAL));
            document.add(new Paragraph("Period: %s to %s".formatted(s.fromDate(), s.toDate()), NORMAL));
            document.add(new Paragraph("Generated: " + s.generatedAt().format(DATE_TIME) + " IST", NORMAL));
            document.add(new Paragraph("Opening balance: %s %s".formatted(s.currency(), money(s.openingBalance())), BOLD));
            document.add(new Paragraph(" ", NORMAL));

            PdfPTable table = new PdfPTable(new float[]{2.3f, 3.0f, 5.2f, 1.9f, 1.9f, 2.1f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);   // repeat the header on every page
            for (String header : new String[]{"Date", "Reference", "Description", "Debit", "Credit", "Balance"}) {
                PdfPCell cell = cell(header, BOLD, Element.ALIGN_LEFT);
                cell.setBackgroundColor(new Color(230, 230, 230));
                table.addCell(cell);
            }

            if (s.lines().isEmpty()) {
                PdfPCell empty = cell("No transactions in this period.", NORMAL, Element.ALIGN_CENTER);
                empty.setColspan(6);
                table.addCell(empty);
            }
            for (StatementLine line : s.lines()) {
                table.addCell(cell(line.dateTime().format(DATE_TIME), NORMAL, Element.ALIGN_LEFT));
                table.addCell(cell(line.reference(), NORMAL, Element.ALIGN_LEFT));
                table.addCell(cell(line.narrative(), NORMAL, Element.ALIGN_LEFT));
                table.addCell(cell(money(line.debit()), NORMAL, Element.ALIGN_RIGHT));
                table.addCell(cell(money(line.credit()), NORMAL, Element.ALIGN_RIGHT));
                table.addCell(cell(money(line.balance()), NORMAL, Element.ALIGN_RIGHT));
            }
            document.add(table);

            document.add(new Paragraph(" ", NORMAL));
            document.add(new Paragraph("Total debits: %s %s".formatted(s.currency(), money(s.totalDebits())), NORMAL));
            document.add(new Paragraph("Total credits: %s %s".formatted(s.currency(), money(s.totalCredits())), NORMAL));
            document.add(new Paragraph("Closing balance: %s %s".formatted(s.currency(), money(s.closingBalance())), BOLD));
            document.add(new Paragraph(" ", NORMAL));
            document.add(new Paragraph("This is a computer-generated statement and needs no signature.", NORMAL));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate the PDF statement", ex);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        return cell;
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : "%,.2f".formatted(amount);
    }
}
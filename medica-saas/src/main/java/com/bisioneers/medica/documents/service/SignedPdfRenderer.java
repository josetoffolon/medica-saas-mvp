package com.bisioneers.medica.documents.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Embebe la firma del paciente al final del PDF base.
 *
 * Operación:
 *   1. Cargar PDF base desde InputStream (de R2)
 *   2. Decodificar firma PNG base64 → PDImageXObject
 *   3. Agregar página final con:
 *      - Título "Certificado de firma electrónica"
 *      - Bloque legal: nombre, documento, fecha/hora, IP, hash
 *      - Imagen de la firma centrada
 *      - Cláusula de validez legal
 *   4. Retornar bytes del PDF resultante
 *
 * Tamaños:
 *   - Página Letter: 612pt x 792pt
 *   - Márgenes: 50pt
 *   - Imagen firma: ancho 300pt máx, alto 100pt máx (escala manteniendo ratio)
 */
@Component
public class SignedPdfRenderer {

	private static final Logger log = LoggerFactory.getLogger(SignedPdfRenderer.class);

	private static final DateTimeFormatter DT_FORMAT =
			DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy 'a las' HH:mm:ss zzz",
					new Locale("es"));

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Panama");

	/**
	 * @param basePdfStream      PDF original sin firma (de R2 storage)
	 * @param signatureDataUrl   base64 PNG (con o sin prefix data:image/png;base64,)
	 * @param signerName         nombre completo del firmante
	 * @param signerDocument     cédula/pasaporte del firmante
	 * @param signerIp           IP desde donde firmó
	 * @param signerUserAgent    User-Agent del browser
	 * @param signedAt           timestamp de la firma
	 * @param tenantDisplayName  nombre de la clínica
	 * @return bytes del PDF firmado
	 */
	public byte[] embedSignature(
			InputStream basePdfStream,
			String signatureDataUrl,
			String signerName,
			String signerDocument,
			String signerIp,
			String signerUserAgent,
			Instant signedAt,
			String tenantDisplayName
			) {
		try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(basePdfStream.readAllBytes())) {

			// ─── Decodificar firma ───
			byte[] signatureBytes = decodeBase64Image(signatureDataUrl);
			PDImageXObject signatureImg = PDImageXObject.createFromByteArray(
					document, signatureBytes, "signature");

			// ─── Crear página de certificado ───
			PDPage certPage = new PDPage(PDRectangle.LETTER);
			document.addPage(certPage);

			try (PDPageContentStream cs = new PDPageContentStream(document, certPage)) {

				float pageWidth = PDRectangle.LETTER.getWidth();
				float pageHeight = PDRectangle.LETTER.getHeight();
				float margin = 50f;
				float y = pageHeight - margin;

				// Título
				cs.setNonStrokingColor(0.31f, 0.27f, 0.90f); // #4f46e5
				cs.beginText();
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
				cs.newLineAtOffset(margin, y);
				cs.showText("Certificado de Firma Electronica");
				cs.endText();
				y -= 18;

				// Subtítulo
				cs.setNonStrokingColor(0.42f, 0.45f, 0.50f);
				cs.beginText();
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
				cs.newLineAtOffset(margin, y);
				cs.showText(tenantDisplayName != null ? tenantDisplayName : "Medica SaaS");
				cs.endText();
				y -= 30;

				// Línea separadora
				cs.setStrokingColor(0.90f, 0.91f, 0.92f);
				cs.setLineWidth(1f);
				cs.moveTo(margin, y);
				cs.lineTo(pageWidth - margin, y);
				cs.stroke();
				y -= 35;

				// Bloque de datos del firmante
				cs.setNonStrokingColor(0.07f, 0.09f, 0.15f);

				y = writeLabelValue(cs, margin, y, "Nombre del firmante:", safe(signerName));
				y = writeLabelValue(cs, margin, y, "Documento de identidad:", safe(signerDocument));

				String formattedDate = signedAt.atZone(DEFAULT_ZONE).format(DT_FORMAT);
				y = writeLabelValue(cs, margin, y, "Fecha y hora de firma:", formattedDate);

				y = writeLabelValue(cs, margin, y, "Direccion IP:", safe(signerIp));

				String uaTruncated = truncate(signerUserAgent, 80);
				y = writeLabelValue(cs, margin, y, "Dispositivo / Navegador:", uaTruncated);

				y -= 25;

				// Línea separadora
				cs.setStrokingColor(0.90f, 0.91f, 0.92f);
				cs.moveTo(margin, y);
				cs.lineTo(pageWidth - margin, y);
				cs.stroke();
				y -= 30;

				// Imagen de la firma centrada
				float imgMaxWidth = 300f;
				float imgMaxHeight = 100f;
				float imgWidth = signatureImg.getWidth();
				float imgHeight = signatureImg.getHeight();

				// Escalar manteniendo ratio
				float scale = Math.min(imgMaxWidth / imgWidth, imgMaxHeight / imgHeight);
				if (scale > 1f) scale = 1f; // no escalar hacia arriba
				float finalWidth = imgWidth * scale;
				float finalHeight = imgHeight * scale;
				float imgX = (pageWidth - finalWidth) / 2f;
				float imgY = y - finalHeight;

				cs.drawImage(signatureImg, imgX, imgY, finalWidth, finalHeight);

				// Línea debajo de la firma
				y = imgY - 5;
				cs.setStrokingColor(0.07f, 0.09f, 0.15f);
				cs.setLineWidth(0.5f);
				cs.moveTo((pageWidth - 350f) / 2f, y);
				cs.lineTo((pageWidth + 350f) / 2f, y);
				cs.stroke();

				// Label de firma
				y -= 12;
				cs.setNonStrokingColor(0.42f, 0.45f, 0.50f);
				cs.beginText();
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
				String firmaLabel = "Firma del paciente";
				float labelWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA)
						.getStringWidth(firmaLabel) / 1000 * 9;
				cs.newLineAtOffset((pageWidth - labelWidth) / 2f, y);
				cs.showText(firmaLabel);
				cs.endText();

				// Cláusula legal al pie
				y = margin + 60;
				cs.setNonStrokingColor(0.42f, 0.45f, 0.50f);
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);

				String[] legalLines = {
						"Este documento ha sido firmado electronicamente conforme a lo establecido en la Ley",
						"51 de 2008 de la Republica de Panama sobre firma electronica simple. La integridad del",
						"documento esta protegida mediante hash criptografico SHA-256 que se incluye en el",
						"registro de auditoria. Cualquier modificacion posterior al documento invalidara la firma."
				};

				for (String line : legalLines) {
					cs.beginText();
					cs.newLineAtOffset(margin, y);
					cs.showText(line);
					cs.endText();
					y -= 10;
				}
			}

			// ─── Serializar a bytes ───
			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				document.save(out);
				return out.toByteArray();
			}

		} catch (IOException e) {
			log.error("Failed to embed signature: {}", e.getMessage(), e);
			throw new RuntimeException("No se pudo incrustar la firma en el PDF", e);
		}
	}

	// ─── Helpers ───────────────────────────────────────────────

	private float writeLabelValue(PDPageContentStream cs, float x, float y,
			String label, String value) throws IOException {
		cs.setNonStrokingColor(0.42f, 0.45f, 0.50f);
		cs.beginText();
		cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
		cs.newLineAtOffset(x, y);
		cs.showText(label);
		cs.endText();

		cs.setNonStrokingColor(0.07f, 0.09f, 0.15f);
		cs.beginText();
		cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
		cs.newLineAtOffset(x, y - 13);
		cs.showText(safe(value));
		cs.endText();

		return y - 32;
	}

	private byte[] decodeBase64Image(String dataUrl) {
		String base64 = dataUrl;
		int commaIdx = dataUrl.indexOf(',');
		if (commaIdx >= 0) {
			base64 = dataUrl.substring(commaIdx + 1);
		}
		return Base64.getDecoder().decode(base64);
	}

	private String safe(String s) {
		if (s == null) return "(no disponible)";
		// PDFBox WinAnsi font doesn't support all unicode; sustituir caracteres
		// problemáticos para evitar crashes.
		return s.replace("á", "a").replace("é", "e").replace("í", "i")
				.replace("ó", "o").replace("ú", "u")
				.replace("Á", "A").replace("É", "E").replace("Í", "I")
				.replace("Ó", "O").replace("Ú", "U")
				.replace("ñ", "n").replace("Ñ", "N");
	}

	private String truncate(String s, int max) {
		if (s == null) return "(no disponible)";
		s = safe(s);
		if (s.length() <= max) return s;
		return s.substring(0, max - 3) + "...";
	}
}

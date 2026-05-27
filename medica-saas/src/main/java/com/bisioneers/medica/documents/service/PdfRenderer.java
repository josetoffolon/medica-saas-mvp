package com.bisioneers.medica.documents.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renderiza HTML a PDF usando OpenHTMLToPDF.
 *
 * Razones de la elección:
 *  - PDF/A compatible, soporta texto Unicode (incluye español con acentos y ñ)
 *  - No requiere Chrome ni navegador headless (vs Puppeteer)
 *  - Más liviano que iText 7 y con licencia LGPL (vs iText AGPL)
 *  - Renderiza CSS3 razonablemente bien para documentos médicos sencillos
 *
 * Limitaciones:
 *  - CSS muy complejo (flexbox, grid avanzado) puede no renderizar perfecto
 *  - Se usa para documentos médicos simples — formato sobrio y profesional
 */
@Component
public class PdfRenderer {

	private static final Logger log = LoggerFactory.getLogger(PdfRenderer.class);

	/**
	 * Convierte un HTML body a PDF. El HTML recibido se envuelve en
	 * un documento XHTML completo con estilos médicos por defecto.
	 */
	public byte[] htmlToPdf(String bodyHtml, String title) {
		String xhtml = wrapInXhtml(bodyHtml, title);

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withHtmlContent(xhtml, null);
			builder.toStream(out);
			builder.run();

			byte[] pdfBytes = out.toByteArray();
			log.debug("PDF generated: {} bytes", pdfBytes.length);
			return pdfBytes;

		} catch (IOException e) {
			log.error("Failed to render PDF: {}", e.getMessage());
			throw new RuntimeException("No se pudo generar el PDF", e);
		}
	}

	/**
	 * Envuelve el body en un XHTML válido con estilos médicos profesionales.
	 */
	private String wrapInXhtml(String bodyHtml, String title) {
		String safeTitle = title != null ? title : "Documento médico";
		
		String safeBody = sanitizeHtmlEntities(bodyHtml);

		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml">
				<head>
				    <meta charset="UTF-8" />
				    <title>%s</title>
				    <style>
				        @page {
				            size: letter;
				            margin: 2cm 2cm 2.5cm 2cm;
				            @bottom-center {
				                content: "Página " counter(page) " de " counter(pages);
				                font-family: Helvetica, Arial, sans-serif;
				                font-size: 9pt;
				                color: #6b7280;
				            }
				        }
				        * { box-sizing: border-box; }
				        body {
				            font-family: Helvetica, Arial, sans-serif;
				            font-size: 10.5pt;
				            line-height: 1.5;
				            color: #1f2937;
				            margin: 0;
				            padding: 0;
				        }
				        h1 {
				            font-size: 16pt;
				            color: #111827;
				            margin: 0 0 0.3em 0;
				            text-align: center;
				        }
				        h2 {
				            font-size: 13pt;
				            color: #1f2937;
				            margin: 1em 0 0.4em 0;
				            border-bottom: 1px solid #e5e7eb;
				            padding-bottom: 0.2em;
				        }
				        h3 {
				            font-size: 11pt;
				            color: #374151;
				            margin: 0.8em 0 0.3em 0;
				            font-weight: bold;
				        }
				        p { margin: 0 0 0.6em 0; text-align: justify; }
				        ul, ol { margin: 0.4em 0 0.8em 1.5em; padding: 0; }
				        li { margin-bottom: 0.2em; }
				        strong, b { font-weight: bold; color: #111827; }
				        em, i { font-style: italic; }
				        hr { border: none; border-top: 1px solid #d1d5db; margin: 1em 0; }
				        .header {
				            text-align: center;
				            border-bottom: 2px solid #4f46e5;
				            padding-bottom: 0.8em;
				            margin-bottom: 1.5em;
				        }
				        .header .clinic-name { font-size: 14pt; font-weight: bold; color: #4f46e5; }
				        .footer-info {
				            font-size: 8.5pt; color: #6b7280;
				            margin-top: 1em; padding-top: 0.5em;
				            border-top: 1px solid #e5e7eb;
				        }
				        .signature-block {
				            margin-top: 2.5em; page-break-inside: avoid;
				        }
				        .signature-line {
				            border-bottom: 1px solid #111827;
				            width: 60%%; height: 2.5em;
				            margin: 0 auto 0.3em auto;
				        }
				        .signature-img {
				            max-height: 80px; max-width: 250px;
				            display: block; margin: 0 auto;
				        }
				        .signature-label {
				            text-align: center; font-size: 9pt; color: #6b7280;
				        }
				        .checkbox-option {
				            margin: 0.3em 0; padding-left: 1.5em;
				        }
				        table {
				            width: 100%%; border-collapse: collapse; margin: 0.8em 0;
				        }
				        th, td {
				            border: 1px solid #d1d5db; padding: 6pt 8pt;
				            text-align: left; vertical-align: top;
				        }
				        th { background: #f3f4f6; font-weight: bold; }
				        .text-center { text-align: center; }
				        .small { font-size: 9pt; color: #6b7280; }
				    </style>
				</head>
				<body>
				%s
				</body>
				</html>
				""".formatted(escapeXml(safeTitle), safeBody);
	}

	private static String escapeXml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
	
	private String sanitizeHtmlEntities(String html) {
	    if (html == null) return "";
	    return html
	        .replace("&nbsp;", "&#160;")
	        .replace("&copy;", "&#169;")
	        .replace("&reg;", "&#174;")
	        .replace("&trade;", "&#8482;")
	        .replace("&hellip;", "&#8230;")
	        .replace("&mdash;", "&#8212;")
	        .replace("&ndash;", "&#8211;")
	        .replace("&laquo;", "&#171;")
	        .replace("&raquo;", "&#187;");
	}
}

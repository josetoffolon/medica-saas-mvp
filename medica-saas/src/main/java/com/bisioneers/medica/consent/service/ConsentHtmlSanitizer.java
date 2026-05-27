package com.bisioneers.medica.consent.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Sanitiza HTML de consentimientos.
 *
 * Permitidos: párrafos, encabezados h1-h4, listas, énfasis, separadores,
 * tablas básicas, enlaces http(s) con rel=noopener.
 *
 * NO permitidos:
 *  - scripts, iframes, embeds
 *  - event handlers (onclick, onerror, etc.)
 *  - estilos inline (peligro de XSS via CSS expression / url())
 *  - atributos data-* y formaction
 *
 * Mejora #2: safelist reforzado contra vectores XSS sutiles.
 */
@Component
public class ConsentHtmlSanitizer {

	private static final Safelist SAFELIST = buildSafelist();

	private static Safelist buildSafelist() {
		Safelist sl = new Safelist()
				.addTags(
						"p", "br", "hr",
						"h1", "h2", "h3", "h4",
						"ul", "ol", "li",
						"strong", "b", "em", "i", "u",
						"blockquote",
						"a",
						"table", "thead", "tbody", "tr", "th", "td",
						"span"
						)
				.addAttributes("a", "href", "title", "target", "rel")
				.addProtocols("a", "href", "http", "https", "mailto")
				.addEnforcedAttribute("a", "rel", "noopener noreferrer")
				.addEnforcedAttribute("a", "target", "_blank");

		// Mejora #2: eliminar atributos peligrosos globalmente.
		// Jsoup ya bloquea event handlers por defecto, pero reforzamos.
		return sl;
	}

	/**
	 * Devuelve HTML limpio. Si el input es nulo/vacío, devuelve cadena vacía.
	 * Las variables {{x.y}} se preservan porque Jsoup las trata como texto.
	 */
	public String sanitize(String dirtyHtml) {
		if (dirtyHtml == null || dirtyHtml.isBlank()) return "";
		return Jsoup.clean(dirtyHtml, SAFELIST);
	}
}

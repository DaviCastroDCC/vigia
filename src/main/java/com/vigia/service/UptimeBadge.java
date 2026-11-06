package com.vigia.service;

import java.util.Locale;

/**
 * Gera um badge SVG no estilo dos badges de README ("uptime | 99.95%").
 * Sem bibliotecas: é só texto, com a largura estimada pelo número de caracteres.
 */
final class UptimeBadge {

    private static final double CHAR_WIDTH = 6.6; // Verdana 11px, aproximado
    private static final int PADDING = 10;

    private UptimeBadge() {
    }

    static String render(String label, Double percent) {
        String value = percent == null ? "sem dados" : format(percent);
        String color = colorFor(percent);
        int labelWidth = width(label);
        int valueWidth = width(value);
        int total = labelWidth + valueWidth;
        return String.format(Locale.ROOT, """
                <svg xmlns="http://www.w3.org/2000/svg" width="%1$d" height="20" role="img" aria-label="%2$s: %3$s">
                  <title>%2$s: %3$s</title>
                  <linearGradient id="s" x2="0" y2="100%%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient>
                  <clipPath id="r"><rect width="%1$d" height="20" rx="3" fill="#fff"/></clipPath>
                  <g clip-path="url(#r)">
                    <rect width="%4$d" height="20" fill="#555"/>
                    <rect x="%4$d" width="%5$d" height="20" fill="%6$s"/>
                    <rect width="%1$d" height="20" fill="url(#s)"/>
                  </g>
                  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
                    <text x="%7$.1f" y="15" fill="#010101" fill-opacity=".3">%2$s</text>
                    <text x="%7$.1f" y="14">%2$s</text>
                    <text x="%8$.1f" y="15" fill="#010101" fill-opacity=".3">%3$s</text>
                    <text x="%8$.1f" y="14">%3$s</text>
                  </g>
                </svg>
                """, total, escape(label), escape(value), labelWidth, valueWidth, color,
                labelWidth / 2.0, labelWidth + valueWidth / 2.0);
    }

    static String format(double percent) {
        return (percent >= 99.995 ? "100" : String.format(Locale.ROOT, "%.2f", percent)) + "%";
    }

    private static String colorFor(Double percent) {
        if (percent == null) return "#9f9f9f";
        if (percent >= 99.9) return "#2ea44f";
        if (percent >= 99) return "#97ca00";
        if (percent >= 95) return "#dfb317";
        return "#e05d44";
    }

    private static int width(String text) {
        return (int) Math.round(text.length() * CHAR_WIDTH) + PADDING;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

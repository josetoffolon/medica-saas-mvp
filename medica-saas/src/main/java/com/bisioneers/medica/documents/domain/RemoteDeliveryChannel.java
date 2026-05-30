package com.bisioneers.medica.documents.domain;

/**
 * Canal por el que se entregó el link de firma remota.
 *
 *  WHATSAPP → enviado por Twilio WhatsApp
 *  EMAIL    → enviado por SendGrid / SMTP
 *  MANUAL   → solo se generó el link, staff lo copió/pegó (sin envío automático)
 */
public enum RemoteDeliveryChannel {
    WHATSAPP,
    EMAIL,
    MANUAL
}

package org.example.mail.autodetect;

/** Simple data holder for discovered SMTP settings. */
public record AutoConfigResult(String host, int port, boolean ssl) {}

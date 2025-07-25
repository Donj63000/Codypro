package org.example.mail.autodetect;

import java.io.Serializable;

public record AutoConfigResult(String host, int port, boolean ssl) implements Serializable {
    public AutoConfigResult {
        host = host == null ? "" : host.trim();
        if (host.isEmpty()) throw new IllegalArgumentException("host");
        if (port <= 0)    throw new IllegalArgumentException("port");
    }
}

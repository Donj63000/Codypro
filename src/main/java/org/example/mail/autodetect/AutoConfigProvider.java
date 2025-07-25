package org.example.mail.autodetect;

@FunctionalInterface
public interface AutoConfigProvider {
    AutoConfigResult discover(String email) throws Exception;
}

package com.glancesync.modules.mail.config;

/**
 * SMTP settings, bound from {@code glancesync.mail.*} in application.properties.
 * An empty host means "log emails instead of sending them" (local development).
 */
public class MailConfig {

    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private boolean starttls = true;
    private String from = "GlanceSync <no-reply@glancesync.local>";

    public boolean isEnabled() {
        return host != null && !host.isBlank();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}

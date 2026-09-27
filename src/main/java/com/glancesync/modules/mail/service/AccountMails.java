package com.glancesync.modules.mail.service;

import jakarta.inject.Singleton;

import java.time.Duration;

/** The emails the app sends about a user's account. Wording lives here, delivery in MailService. */
@Singleton
public class AccountMails {

    private final MailService mailService;

    public AccountMails(MailService mailService) {
        this.mailService = mailService;
    }

    public void sendVerification(String to, String firstName, String link, Duration validFor) {
        mailService.send(to, "Confirm your GlanceSync account",
                "Hi " + firstName + ",\n\n"
                        + "Welcome to GlanceSync! Confirm your email address to activate your account:\n\n"
                        + link + "\n\n"
                        + "This link expires in " + describe(validFor)
                        + ". If you did not sign up, you can ignore this email.\n");
    }

    /** "15 minutes", "1 hour", "24 hours". */
    private static String describe(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes % 60 != 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = minutes / 60;
        return hours + (hours == 1 ? " hour" : " hours");
    }
}

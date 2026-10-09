package com.glancesync.modules.mail.service;


import java.time.Duration;

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

    public void sendEmailChange(String to, String firstName, String link, Duration validFor) {
        mailService.send(to, "Confirm your new GlanceSync email address",
                "Hi " + firstName + ",\n\n"
                        + "You asked to use this address for your GlanceSync account. Confirm it here:\n\n"
                        + link + "\n\n"
                        + "This link expires in " + describe(validFor)
                        + ". Until then your account keeps its old address. If this was not you, ignore this email.\n");
    }

    public void sendPasswordReset(String to, String firstName, String link, Duration validFor) {
        mailService.send(to, "Reset your GlanceSync password",
                "Hi " + firstName + ",\n\n"
                        + "Someone asked to reset the password for your GlanceSync account. Choose a new one here:\n\n"
                        + link + "\n\n"
                        + "This link expires in " + describe(validFor)
                        + ". If you did not ask for this, ignore this email and your password stays the same.\n");
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

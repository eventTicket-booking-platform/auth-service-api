package com.ec7205.event_hub.auth_service_api.utils;

import org.springframework.stereotype.Component;

import java.util.Random;
@Component
public class PasswordGenerator {
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHA = "!@#$%^&*";

    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL_CHA;

    public String generatePassword() {
        StringBuilder password = new StringBuilder(6);
        Random random = new Random();
        password.append(
                UPPERCASE.charAt(
                        random.nextInt(UPPERCASE.length()
                        )
                ));
        password.append(
                LOWERCASE.charAt(
                        random.nextInt(LOWERCASE.length()
                        )
                ));
        password.append(
                DIGITS.charAt(
                        random.nextInt(DIGITS.length()
                        )
                ));
        password.append(
                SPECIAL_CHA.charAt(
                        random.nextInt(SPECIAL_CHA.length()
                        )
                ));
        for (int i = 4; i < 6; i++) {
            password.append(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }
        return shuffleString(password.toString(), random);
    }

    private String shuffleString(String input, Random random) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
        }
        return chars.toString();
    }
}

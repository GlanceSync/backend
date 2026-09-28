package com.glancesync.modules.user.dto;

import com.glancesync.modules.user.enums.Gender;
import com.glancesync.modules.user.enums.SexualPreference;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;

@Serdeable
public record ProfileRequest(Gender gender, SexualPreference sexualPreference, String biography,
                             LocalDate birthDate) {
}

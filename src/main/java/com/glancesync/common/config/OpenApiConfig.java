package com.glancesync.common.config;

import com.glancesync.modules.auth.service.SessionService;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(title = "GlanceSync", version = "0.1", description = "GlanceSync backend API"),
        security = @SecurityRequirement(name = "session"))
@SecurityScheme(name = "session", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE, paramName = SessionService.COOKIE_NAME)
public final class OpenApiConfig {

    private OpenApiConfig() {
    }
}

package com.glancesync.modules.user.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.AccountRequest;
import com.glancesync.modules.user.dto.AccountResponse;
import com.glancesync.modules.user.service.AccountService;
import com.glancesync.web.Nullable;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.MediaType;
import com.glancesync.web.annotation.Body;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.Put;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users")
@Controller("/api/users")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Get("/me/account")
    public AccountResponse mine(HttpRequest<?> request) {
        return accountService.get(selfId(request));
    }

    @Put(value = "/me/account", consumes = MediaType.APPLICATION_JSON)
    public AccountResponse update(HttpRequest<?> request, @Body @Nullable AccountRequest body) {
        return accountService.update(selfId(request), body);
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}

package com.glancesync.modules.user.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.service.CredentialRules;
import com.glancesync.modules.auth.service.EmailVerificationService;
import com.glancesync.modules.user.dto.AccountRequest;
import com.glancesync.modules.user.dto.AccountResponse;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;

import java.util.Map;

public class AccountService {

    private final UserDao userDao;
    private final CredentialRules rules;
    private final EmailVerificationService emailVerification;

    public AccountService(UserDao userDao, CredentialRules rules, EmailVerificationService emailVerification) {
        this.userDao = userDao;
        this.rules = rules;
        this.emailVerification = emailVerification;
    }

    public AccountResponse get(UUID selfId) {
        User user = requireUser(selfId);
        return new AccountResponse(user.id(), user.username(), user.email(), user.firstName(), user.lastName(),
                emailVerification.pendingEmail(selfId).orElse(null));
    }

    /**
     * Names change straight away. A new email only takes over once the link sent to it is
     * followed, so a typo can never lock anyone out of their account.
     */
    public AccountResponse update(UUID selfId, AccountRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        String firstName = trim(request.firstName());
        String lastName = trim(request.lastName());
        String email = trim(request.email());

        Map<String, String> errors = rules.validateAccount(firstName, lastName, email);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        }

        User user = requireUser(selfId);
        userDao.updateNames(selfId, firstName, lastName);

        if (!email.equalsIgnoreCase(user.email())) {
            if (userDao.emailExists(email)) {
                throw new ApiException(HttpStatus.CONFLICT, "Please correct the highlighted fields.",
                        Map.of("email", "An account already uses that email address."));
            }
            emailVerification.sendEmailChangeLink(requireUser(selfId), email);
        }
        return get(selfId);
    }

    private User requireUser(UUID userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That account no longer exists."));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

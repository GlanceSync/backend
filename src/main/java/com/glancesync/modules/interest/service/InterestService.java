package com.glancesync.modules.interest.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.interest.entity.Interest;
import com.glancesync.modules.interest.repo.InterestDao;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


@Singleton
public class InterestService {

    public static final int MAX_PER_USER = 10;
    private static final int MAX_SEARCH_RESULTS = 50;

    private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}\\p{N}_-]{1,30}");

    private final InterestDao interestDao;
    private final UserDao userDao;

    public InterestService(InterestDao interestDao, UserDao userDao) {
        this.interestDao = interestDao;
        this.userDao = userDao;
    }

    public record Created(Interest interest, boolean isNew) {
    }

    public List<Interest> search(String query) {
        String prefix = query == null ? "" : stripHash(query.trim()).toLowerCase(Locale.ROOT);
        return interestDao.search(prefix, MAX_SEARCH_RESULTS);
    }

    public Interest get(UUID interestId) {
        return interestDao.findById(interestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That tag does not exist."));
    }

    public Created create(UUID selfId, String rawName) {
        String name = normalise(rawName);
        return interestDao.createIfAbsent(name, selfId)
                .map(id -> new Created(get(id), true))
                .orElseGet(() -> new Created(findByNameOrFail(name), false));
    }

    public Interest rename(UUID selfId, UUID interestId, String rawName) {
        Interest interest = requireEditable(selfId, interestId);
        String name = normalise(rawName);
        if (name.equals(interest.name())) {
            return interest;
        }
        if (interestDao.findByName(name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "#" + name + " already exists.",
                    Map.of("name", "Already taken."));
        }
        interestDao.rename(interestId, name);
        return get(interestId);
    }

    public void delete(UUID selfId, UUID interestId) {
        requireEditable(selfId, interestId);
        interestDao.delete(interestId);
        userDao.refreshProfileCompleted(selfId);
    }

    public List<Interest> forUser(UUID userId) {
        if (userDao.findById(userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }
        return interestDao.forUser(userId);
    }

    public List<Interest> addToUser(UUID selfId, String rawName) {
        Interest interest = create(selfId, rawName).interest();
        boolean alreadyHas = interestDao.forUser(selfId).stream()
                .anyMatch(existing -> existing.id().equals(interest.id()));
        if (!alreadyHas && interestDao.countForUser(selfId) >= MAX_PER_USER) {
            throw tooMany();
        }
        interestDao.addToUser(selfId, interest.id());
        userDao.refreshProfileCompleted(selfId);
        return interestDao.forUser(selfId);
    }

    public List<Interest> replaceForUser(UUID selfId, List<String> rawNames) {
        if (rawNames == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send the list of tags as \"names\".");
        }

        Set<String> names = new LinkedHashSet<>();
        for (String rawName : rawNames) {
            names.add(normalise(rawName));
        }
        if (names.size() > MAX_PER_USER) {
            throw tooMany();
        }

        List<UUID> ids = names.stream()
                .map(name -> create(selfId, name).interest().id())
                .toList();
        interestDao.replaceForUser(selfId, ids);
        userDao.refreshProfileCompleted(selfId);
        return interestDao.forUser(selfId);
    }

    public List<Interest> removeFromUser(UUID selfId, UUID interestId) {
        if (interestDao.removeFromUser(selfId, interestId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That tag is not on your profile.");
        }
        userDao.refreshProfileCompleted(selfId);
        return interestDao.forUser(selfId);
    }

    private Interest requireEditable(UUID selfId, UUID interestId) {
        Interest interest = get(interestId);
        if (!selfId.equals(interest.createdBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who created this tag can change it.");
        }
        if (interestDao.countUsersOtherThan(interestId, selfId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Other people are using #" + interest.name() + ", so it can no longer be changed.");
        }
        return interest;
    }

    private Interest findByNameOrFail(String name) {
        return interestDao.findByName(name)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "That tag changed; please try again."));
    }

    private static String normalise(String rawName) {
        String name = rawName == null ? "" : stripHash(rawName.trim()).toLowerCase(Locale.ROOT);
        if (!VALID_NAME.matcher(name).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Tags are 1 to 30 letters, digits, '-' or '_', with no spaces.",
                    Map.of("name", "Invalid tag."));
        }
        return name;
    }

    private static String stripHash(String value) {
        int start = 0;
        while (start < value.length() && value.charAt(start) == '#') {
            start++;
        }
        return value.substring(start);
    }

    private static ApiException tooMany() {
        return new ApiException(HttpStatus.BAD_REQUEST, "You can have at most " + MAX_PER_USER + " tags.");
    }
}

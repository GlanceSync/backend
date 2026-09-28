package com.glancesync.modules.user.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.user.entity.Picture;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.PictureDao;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Up to {@value #MAX_PER_USER} pictures per user, one of which is the profile picture.
 * Whenever a user has pictures, one of them is the profile picture: the first upload
 * becomes it, and deleting it promotes the oldest one left.
 */
@Singleton
public class PictureService {

    public static final int MAX_PER_USER = 5;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final PictureDao pictureDao;
    private final UserDao userDao;

    public PictureService(PictureDao pictureDao, UserDao userDao) {
        this.pictureDao = pictureDao;
        this.userDao = userDao;
    }

    public List<Picture> forUser(UUID userId) {
        requireUser(userId);
        return pictureDao.forUser(userId);
    }

    public Optional<PictureDao.PictureData> data(UUID userId, UUID pictureId) {
        return pictureDao.findData(userId, pictureId);
    }

    public List<Picture> add(UUID selfId, byte[] body) {
        String contentType = validate(body);
        if (pictureDao.countForUser(selfId) >= MAX_PER_USER) {
            throw tooMany();
        }
        pictureDao.add(selfId, contentType, body);
        pictureDao.ensureProfile(selfId);
        userDao.refreshProfileCompleted(selfId);
        return pictureDao.forUser(selfId);
    }

    public List<Picture> setProfile(UUID selfId, UUID pictureId) {
        if (pictureDao.setProfile(selfId, pictureId) == 0) {
            throw notFound();
        }
        return pictureDao.forUser(selfId);
    }

    public List<Picture> delete(UUID selfId, UUID pictureId) {
        if (pictureDao.delete(selfId, pictureId) == 0) {
            throw notFound();
        }
        pictureDao.ensureProfile(selfId);
        userDao.refreshProfileCompleted(selfId);
        return pictureDao.forUser(selfId);
    }

    public User replaceProfile(UUID selfId, byte[] body) {
        String contentType = validate(body);
        UUID previous = requireUser(selfId).profilePictureId();
        if (previous == null && pictureDao.countForUser(selfId) >= MAX_PER_USER) {
            throw tooMany();
        }
        UUID added = pictureDao.add(selfId, contentType, body);
        pictureDao.setProfile(selfId, added);
        if (previous != null) {
            pictureDao.delete(selfId, previous);
        }
        userDao.refreshProfileCompleted(selfId);
        return requireUser(selfId);
    }

    public User removeProfile(UUID selfId) {
        UUID current = requireUser(selfId).profilePictureId();
        if (current != null) {
            pictureDao.delete(selfId, current);
            pictureDao.ensureProfile(selfId);
            userDao.refreshProfileCompleted(selfId);
        }
        return requireUser(selfId);
    }

    private User requireUser(UUID userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists."));
    }

    private static String validate(byte[] body) {
        if (body == null || body.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an image to upload.");
        }
        if (body.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.REQUEST_ENTITY_TOO_LARGE, "That image is too large. The limit is 5 MB.");
        }
        String contentType = sniffImageType(body);
        if (contentType == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a JPEG, PNG or WebP image.");
        }
        return contentType;
    }

    private static ApiException tooMany() {
        return new ApiException(HttpStatus.CONFLICT,
                "You can have at most " + MAX_PER_USER + " pictures. Delete one first.");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "That picture is not on your profile.");
    }

    static String sniffImageType(byte[] data) {
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return MediaType.IMAGE_JPEG;
        }
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MediaType.IMAGE_PNG;
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}

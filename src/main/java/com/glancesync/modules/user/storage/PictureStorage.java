package com.glancesync.modules.user.storage;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;


@Singleton
public class PictureStorage {

    private final Path root;

    public PictureStorage(@Value("${glancesync.pictures.storage-path:./data/images}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create picture storage at " + this.root, e);
        }
    }

    public void write(UUID userId, UUID pictureId, byte[] data) {
        Path target = path(userId, pictureId);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), pictureId.toString(), ".tmp");
            try {
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store picture " + pictureId, e);
        }
    }

    public Optional<byte[]> read(UUID userId, UUID pictureId) {
        try {
            return Optional.of(Files.readAllBytes(path(userId, pictureId)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read picture " + pictureId, e);
        }
    }

    public void delete(UUID userId, UUID pictureId) {
        try {
            Files.deleteIfExists(path(userId, pictureId));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete picture " + pictureId, e);
        }
    }

    private Path path(UUID userId, UUID pictureId) {
        return root.resolve(userId.toString()).resolve(pictureId.toString());
    }
}

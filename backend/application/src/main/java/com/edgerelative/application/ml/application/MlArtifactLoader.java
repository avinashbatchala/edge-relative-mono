package com.edgerelative.application.ml.application;

import com.edgerelative.application.ml.domain.GbmModelArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads a frozen {@link GbmModelArtifact} from the registered URI and verifies its SHA-256 checksum
 * before use. A missing file or a checksum mismatch fails loudly; a corrupt artifact is never scored.
 */
@Component
public class MlArtifactLoader {

    private final MlModelRegistryService registry;
    private final JsonMapper json;

    public MlArtifactLoader(MlModelRegistryService registry, JsonMapper json) {
        this.registry = registry;
        this.json = json;
    }

    public GbmModelArtifact load(long modelVersionId) {
        MlModelRegistryService.ModelVersionView version = registry.find(modelVersionId);
        if (version == null) {
            throw new IllegalArgumentException("unknown model version: " + modelVersionId);
        }
        Path path = Path.of(version.artifactUri());
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("model artifact not found: " + version.artifactUri());
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("could not read model artifact: " + version.artifactUri(), exception);
        }
        if (version.artifactChecksum() != null && !version.artifactChecksum().isBlank()) {
            String actual = sha256(bytes);
            if (!actual.equalsIgnoreCase(version.artifactChecksum())) {
                throw new IllegalStateException("model artifact checksum mismatch for version " + modelVersionId);
            }
        }
        GbmModelArtifact artifact = json.readValue(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), GbmModelArtifact.class);
        if (!"er-gbm-v1".equals(artifact.format())) {
            throw new IllegalStateException("unsupported model artifact format: " + artifact.format());
        }
        return artifact;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

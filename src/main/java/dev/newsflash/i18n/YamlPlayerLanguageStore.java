package dev.newsflash.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class YamlPlayerLanguageStore implements PlayerLanguageStore {
    private final Path file;
    private final Logger logger;
    private YamlConfiguration config;

    public YamlPlayerLanguageStore(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("player-languages.yml");
        this.logger = logger;
        this.config = YamlConfiguration.loadConfiguration(file.toFile());
    }

    @Override
    public Optional<String> language(Player player) {
        String language = config.getString(key(player.getUniqueId()));
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(LanguageRegistry.normalize(language));
    }

    @Override
    public void setLanguage(Player player, String language) {
        config.set(key(player.getUniqueId()), LanguageRegistry.normalize(language));
        save();
    }

    @Override
    public void clearLanguage(Player player) {
        config.set(key(player.getUniqueId()), null);
        save();
    }

    @Override
    public void reload() {
        config = YamlConfiguration.loadConfiguration(file.toFile());
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            config.save(file.toFile());
        } catch (IOException exception) {
            logger.warning("Failed to save player language settings: " + exception.getMessage());
        }
    }

    private String key(UUID uuid) {
        return "players." + uuid;
    }
}

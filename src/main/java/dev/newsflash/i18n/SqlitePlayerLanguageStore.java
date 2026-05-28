package dev.newsflash.i18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

public final class SqlitePlayerLanguageStore implements PlayerLanguageStore {
    private final Logger logger;
    private final Connection connection;

    public SqlitePlayerLanguageStore(Path dataFolder, Logger logger) {
        this.logger = logger;
        try {
            Files.createDirectories(dataFolder);
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.resolve("newsflash.db"));
            initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SQLite player language store.", exception);
        }
    }

    @Override
    public synchronized Optional<String> language(Player player) {
        String sql = "SELECT language FROM player_languages WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.getUniqueId().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(LanguageRegistry.normalize(result.getString("language")));
            }
        } catch (SQLException exception) {
            logger.warning("Failed to load player language setting: " + exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void setLanguage(Player player, String language) {
        String sql = """
            INSERT INTO player_languages(uuid, language)
            VALUES(?, ?)
            ON CONFLICT(uuid) DO UPDATE SET language = excluded.language
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, LanguageRegistry.normalize(language));
            statement.executeUpdate();
        } catch (SQLException exception) {
            logger.warning("Failed to save player language setting: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void clearLanguage(Player player) {
        String sql = "DELETE FROM player_languages WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.getUniqueId().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            logger.warning("Failed to clear player language setting: " + exception.getMessage());
        }
    }

    @Override
    public void reload() {
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.warning("Failed to close player language database: " + exception.getMessage());
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_languages (
                    uuid TEXT PRIMARY KEY,
                    language TEXT NOT NULL
                )
                """);
        }
    }
}

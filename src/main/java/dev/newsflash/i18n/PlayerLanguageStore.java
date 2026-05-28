package dev.newsflash.i18n;

import java.util.Optional;
import org.bukkit.entity.Player;

public interface PlayerLanguageStore extends AutoCloseable {
    Optional<String> language(Player player);

    void setLanguage(Player player, String language);

    void clearLanguage(Player player);

    void reload();

    @Override
    default void close() {
    }
}

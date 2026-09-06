package dev.shvquu.betternpcs.storage.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import dev.shvquu.betternpcs.core.config.StorageSettings;
import dev.shvquu.betternpcs.core.config.StorageType;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Builds a {@link MongoNpcRepository} from the configured settings.
 *
 * @since 1.0.0
 */
public final class MongoRepositoryFactory {

    /**
     * How many threads run database work.
     *
     * <p>Fixed rather than configurable: unlike the JDBC backends there is no connection pool to
     * size here — the driver manages its own — and one more knob whose right value is "leave it
     * alone" is not worth putting in front of a server owner.
     */
    private static final int POOL_SIZE = 4;

    private MongoRepositoryFactory() {
        throw new AssertionError("No instances");
    }

    /**
     * Creates a repository for the configured MongoDB.
     *
     * @param settings the storage section of the configuration
     * @param logger   where the repository reports failures
     * @return the repository, not yet initialised
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if the configured type is not MongoDB
     */
    public static MongoNpcRepository create(StorageSettings settings, Logger logger) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(logger, "logger");

        if (settings.type() != StorageType.MONGODB) {
            throw new IllegalArgumentException(
                    "Storage type " + settings.type() + " is not served by this repository");
        }

        StorageSettings.Mongo mongo = settings.mongodb();

        MongoClientSettings client = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongo.connectionString()))
                .applicationName("BetterNPCs")
                .applyToClusterSettings(cluster ->
                        // Fail reasonably fast at startup rather than hanging the enable. The plugin
                        // catches this and falls back to in-memory storage with a loud warning,
                        // which is far better than a server that appears to boot and never finishes.
                        cluster.serverSelectionTimeout(10, TimeUnit.SECONDS))
                .build();

        return new MongoNpcRepository(
                MongoClients.create(client),
                mongo.database(),
                settings.describe(),
                logger,
                POOL_SIZE);
    }

}

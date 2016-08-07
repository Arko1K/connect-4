package common;


import com.mongodb.MongoClient;
import com.typesafe.config.ConfigFactory;
import models.entities.Game;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

/**
 * Utility class for Mongo.
 */
public class MongoUtils {

    private static final String KEY_MONGO_DB = "mongo.db";
    private static Datastore datastore = createDatastore();


    public static Datastore getDatastore() {
        return datastore;
    }


    public static Datastore createDatastore() {
        Morphia morphia = new Morphia();
        morphia.mapPackageFromClass(Game.class);
        Datastore datastore = morphia.createDatastore(new MongoClient(), ConfigFactory.load().getString(KEY_MONGO_DB));
        datastore.ensureIndexes();
        return datastore;
    }
}
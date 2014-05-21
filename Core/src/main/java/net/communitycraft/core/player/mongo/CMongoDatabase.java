package net.communitycraft.core.player.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import net.communitycraft.core.Core;
import net.communitycraft.core.player.CDatabase;
import net.communitycraft.core.player.DatabaseConnectException;

import java.net.UnknownHostException;

@Data
public final class CMongoDatabase implements CDatabase {
    @NonNull private final String host;
    @NonNull private final Integer port;
    @NonNull private final String database;
    private final String username;
    private final String password;
    private final String collectionPrefix;

    @Getter private DB mongoDatabase;
    @Getter private MongoClient client;

    @Override
    public void connect() throws DatabaseConnectException {
        try {
            MongoClientURI uri; //Create the URI
            if (this.password != null && this.username != null) {
                uri = new MongoClientURI("mongodb://" + username + ":" + password + "@" + host + ":"  + port + "/" + database);
                Core.getInstance().getLogger().info(uri.toString());
            }
            else uri = new MongoClientURI("mongodb://" + host + ":" + port + "/" + database);
            this.client = new MongoClient(uri); //Connect using it
        } catch (UnknownHostException e) {
            throw new DatabaseConnectException("Could not resolve mongo hostname!", e, this); //Could not connect!
        }
        this.mongoDatabase = this.client.getDB(database); //Grab the database
    }

    @Override
    public void disconnect() {
        this.mongoDatabase = null; //Memory management FTW
        this.client.close();
        this.client = null;
    }

    public DBCollection getCollection(String name) {
        return mongoDatabase.getCollection((collectionPrefix == null ? "" : collectionPrefix) + name);
    }
}

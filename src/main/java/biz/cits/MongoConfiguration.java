package biz.cits;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ClusterType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDbFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfiguration {

    @Value("${db.mongo.url}")
    private String DB_MONGO_URL;

    @Value("${db.mongo.name}")
    private String DB_MONGO_NAME;

    @Value("${db.mongo.user}")
    private String DB_MONGO_USER;

    @Value("${db.mongo.pswd}")
    private String DB_MONGO_PSWD;

    @Bean
    public MongoClient mongoClient() {
//        MongoCredential mongoCredential = MongoCredential.createCredential(DB_MONGO_USER, "admin", DB_MONGO_PSWD.toCharArray());
        MongoClient mongoClient = MongoClients.create(
                MongoClientSettings.builder()
                        .applyToClusterSettings(builder ->
                                builder
                                        .requiredReplicaSetName("fifo")
                                        .applyConnectionString(new ConnectionString(DB_MONGO_URL)))
//                        .credential(mongoCredential)
                        .applyToConnectionPoolSettings(b -> b
                                .maxConnectionIdleTime(5, TimeUnit.SECONDS)
                                .maxSize(100))
                        .build());
        return mongoClient;
    }

    @Bean
    @Autowired
    public MongoDatabase mongoDatabase(MongoClient mongoClient) {
        return mongoClient.getDatabase(DB_MONGO_NAME);
    }

    @Bean
    @Autowired
    public MongoDbFactory mongoDbFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDbFactory(mongoClient, DB_MONGO_NAME);
    }
}

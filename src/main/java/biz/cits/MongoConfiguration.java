package biz.cits;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDbFactory;

import java.util.Arrays;

@Configuration
public class MongoConfiguration {

    @Value("${db.mongo.host}")
    private String DB_MONGO_HOST;

    @Value("${db.mongo.port}")
    private Integer DB_MONGO_PORT;

    @Value("${db.mongo.name}")
    private String DB_MONGO_NAME;

    @Value("${db.mongo.user}")
    private String DB_MONGO_USER;

    @Value("${db.mongo.pswd}")
    private String DB_MONGO_PSWD;

    @Bean
    public MongoClient mongoClient() {
        MongoCredential mongoCredential = MongoCredential.createCredential(DB_MONGO_USER, "admin", DB_MONGO_PSWD.toCharArray());
        MongoClient mongoClient = MongoClients.create(
                MongoClientSettings.builder()
                        .applyToClusterSettings(builder ->
                                builder.hosts(Arrays.asList(new ServerAddress(DB_MONGO_HOST, DB_MONGO_PORT))))
                        .credential(mongoCredential)
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

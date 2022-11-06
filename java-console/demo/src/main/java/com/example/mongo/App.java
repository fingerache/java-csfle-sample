package com.example.mongo;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;


import com.mongodb.client.MongoClients;
import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.vault.DataKeyOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;


public class App 
{
    public static MongoClient getMongoClient()
    {
        MongoClient dbClient = MongoClients.create("mongodb+srv://MNG1:MNG001@mission23.s2ml3.mongodb.net/?authSource=admin");
        return dbClient;

    }

    public static void makeDataKey() throws Exception
    {
        Map<String, String> credentials = Credentials.getCredentials();
        Map<String, Map<String, Object>> kmsProviders = new HashMap<String, Map<String, Object>>();
        String kmsProvider = "aws";
        Map<String, Object> providerDetails = new HashMap<>();
        providerDetails.put("accessKeyId", credentials.get("AWS_ACCESS_KEY_ID"));
        providerDetails.put("secretAccessKey", credentials.get("AWS_SECRET_ACCESS_KEY"));
        kmsProviders.put(kmsProvider, providerDetails);
        // end-kmsproviders

        // start-datakeyopts
        BsonDocument masterKeyProperties = new BsonDocument();
        masterKeyProperties.put("provider", new BsonString(kmsProvider));
        masterKeyProperties.put("key", new BsonString(credentials.get("AWS_KEY_ARN")));
        masterKeyProperties.put("region", new BsonString(credentials.get("AWS_KEY_REGION")));        
        // end-datakeyopts

        // start-create-index
        String connectionString = credentials.get("MONGODB_URI");
        String keyVaultDb = "encryption";
        String keyVaultColl = "__keyVault";
        String keyVaultNamespace = keyVaultDb + "." + keyVaultColl;
        MongoClient keyVaultClient = MongoClients.create(connectionString);

        // Drop the Key Vault Collection in case you created this collection
        // in a previous run of this application.
        keyVaultClient.getDatabase(keyVaultDb).getCollection(keyVaultColl).drop();
        // Drop the database storing your encrypted fields as all
        // the DEKs encrypting those fields were deleted in the preceding line.
        keyVaultClient.getDatabase("medicalRecords").getCollection("patients").drop();


        MongoCollection keyVaultCollection = keyVaultClient.getDatabase(keyVaultDb).getCollection(keyVaultColl);
        IndexOptions indexOpts = new IndexOptions().partialFilterExpression(new BsonDocument("keyAltNames", new BsonDocument("$exists", new BsonBoolean(true) ))).unique(true);
        keyVaultCollection.createIndex(new BsonDocument("keyAltNames", new BsonInt32(1)), indexOpts);
        keyVaultClient.close();
        // end-create-index 

        // start-create-dek
        ClientEncryptionSettings clientEncryptionSettings = ClientEncryptionSettings.builder()
                .keyVaultMongoClientSettings(MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(connectionString))
                        .build())
                .keyVaultNamespace(keyVaultNamespace)
                .kmsProviders(kmsProviders)
                .build();
        
        MongoClient regularClient = MongoClients.create(connectionString);

        ClientEncryption clientEncryption = ClientEncryptions.create(clientEncryptionSettings);
        List keyAltNames = new ArrayList<String>();
        keyAltNames.add("demo-data-key");
        BsonBinary dataKeyId = clientEncryption.createDataKey(kmsProvider, new DataKeyOptions().masterKey(masterKeyProperties).keyAltNames(keyAltNames));
        String base64DataKeyId = Base64.getEncoder().encodeToString(dataKeyId.getData());
        System.out.println("DataKeyId [base64]: " + base64DataKeyId);
        clientEncryption.close();
        // end-create-dek
    }

    public static String recordsDb = "medicalRecords";
    public static String recordsColl = "patients";
    public static String keyVaultNamespace = "encryption.__keyVault";
    public static Map<String, Map<String, Object>> kmsProviders = new HashMap<String, Map<String, Object>>();
    public static Map<String, String> credentials = Credentials.getCredentials();

    public static void setKmsProviders(){
        Map<String, String> credentials = Credentials.getCredentials();

        try{
            String kmsProvider = "aws";
            Map<String, Object> providerDetails = new HashMap<>();
            providerDetails.put("accessKeyId", credentials.get("AWS_ACCESS_KEY_ID"));
            providerDetails.put("secretAccessKey", credentials.get("AWS_SECRET_ACCESS_KEY"));
            kmsProviders.put(kmsProvider, providerDetails);
        }catch(Exception e)
        {
            System.out.println(e.toString());
        }
    }

    public static HashMap<String, BsonDocument> getSchemaMap(){
        Document jsonSchema = new Document().append("bsonType", "object").append("encryptMetadata",
        new Document().append("keyId", new ArrayList<>((Arrays.asList(new Document().append("$binary", new Document()
        .append("base64", "xXqG+akeSDmvsovbBRqySg==")
        .append("subType", "04")))))))
        .append("properties", new Document()
                .append("ssn", new Document().append("encrypt", new Document()
                    .append("bsonType", "int")
                    .append("algorithm","AEAD_AES_256_CBC_HMAC_SHA_512-Random")))
                .append("bloodType", new Document().append("encrypt", new Document()
                    .append("bsonType", "string")
                    .append("algorithm","AEAD_AES_256_CBC_HMAC_SHA_512-Random")))
                .append("medicalRecords", new Document().append("encrypt", new Document()
                    .append("bsonType", "array")
                    .append("algorithm","AEAD_AES_256_CBC_HMAC_SHA_512-Random")))
                .append("insurance", new Document()
                        .append("bsonType", "object")
                        .append("properties",
                                new Document().append("policyNumber", new Document().append("encrypt", new Document()
                                .append("bsonType", "int")
                                .append("algorithm","AEAD_AES_256_CBC_HMAC_SHA_512-Random"))))));
        HashMap<String, BsonDocument> schemaMap = new HashMap<String, BsonDocument>();
        schemaMap.put("medicalRecords.patients", BsonDocument.parse(jsonSchema.toJson()));
        return schemaMap;
    }

    public static MongoClient getEncrytionEnabledClient(HashMap<String, BsonDocument> schemaMap, Map<String, Object> extraOptions){
        
        Map<String, String> credentials = Credentials.getCredentials();
        String connectionString = credentials.get("MONGODB_URI");
        
        //setup kms provider
        setKmsProviders();

        MongoClientSettings clientSettings = MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(credentials.get("MONGODB_URI")))
        .autoEncryptionSettings(AutoEncryptionSettings.builder()
            .keyVaultNamespace(keyVaultNamespace)
            .kmsProviders(kmsProviders)
            .schemaMap(schemaMap)
            .extraOptions(extraOptions)
            .build())
        .build();

        MongoClient mongoClientSecure = MongoClients.create(clientSettings);
        return mongoClientSecure;
    }

    public static void insertDoc(MongoClient mongoClientSecure){
        ArrayList<Document> medicalRecords = new ArrayList<>();
        medicalRecords.add(new Document().append("weight", "180"));
        medicalRecords.add(new Document().append("bloodPressure", "120/80"));
        
        Document insurance = new Document()
        .append("policyNumber", 123142)
        .append("provider",  "MaestCare");

        Document patient = new Document()
            .append("name", "Jon Doe")
            .append("ssn", 241014209)
            .append("bloodType", "AB+")
            .append("medicalRecords", medicalRecords)
            .append("insurance", insurance)
            .append("key-id", "demo-data-key");
        mongoClientSecure.getDatabase(recordsDb).getCollection(recordsColl).insertOne(patient);
    }

    public static void main( String[] args ) throws Exception
    {
        //makeDataKey();
        

        // setup schema
        HashMap<String, BsonDocument> schemaMap = getSchemaMap();

        // mongocryptd option
        Map<String, Object> extraOptions = new HashMap<String, Object>();
        extraOptions.put("mongocryptdSpawnPath", credentials.get("MONGOCRYPTD_PATH"));

        // get secure MongoClient
        var mongoClientSecure = getEncrytionEnabledClient(schemaMap, extraOptions);

        insertDoc(mongoClientSecure);

        System.out.println("Finding a document with encrypted client, searching on an encrypted field");
        Document docSecure = mongoClientSecure.getDatabase(recordsDb).getCollection(recordsColl).find(eq("name", "Jon Doe")).first();
        System.out.println(docSecure.toJson());
        // end-find 
        mongoClientSecure.close();

        // var dbClient = getMongoClient();
        // var db = dbClient.getDatabase("search");
        // var personCol = db.getCollection("person");

        //Document person = personCol.find(eq("middle_name", "Vince")).first();
        
        
        //System.out.println( "person: \n" +  person.toJson());
    }
}

package com.example.mongo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Credentials {
    private static Map<String, String> yourCredentials;
    static {
        yourCredentials = new HashMap<>();
        // Mongo Paths + URI
        yourCredentials.put("MONGODB_URI", "mongodb+srv://MNG1:MNG001@mission23.s2ml3.mongodb.net/?authSource=admin");
        yourCredentials.put("MONGOCRYPTD_PATH", "/Users/srinath.mishra/coffee/mongodb-enterprise/bin/mongocryptd");
        // AWS Credentials
        yourCredentials.put("AWS_ACCESS_KEY_ID", "AKIA2NQEHYYLXZRJVQF7");
        yourCredentials.put("AWS_SECRET_ACCESS_KEY", "ORTN+dhnCiTI4qe5Rz5PwrVkgLquCg/Xm8BF0ozR");
        yourCredentials.put("AWS_KEY_REGION", "ap-northeast-1");
        yourCredentials.put("AWS_KEY_ARN", "arn:aws:kms:ap-northeast-1:716194694679:key/a360fd81-69e8-460d-9aa7-83e7ae4f0ecb");
    }
    private static void checkPlaceholders() throws Exception {
        Pattern p = Pattern.compile("<.*>$");
        ArrayList<String> errorBuffer = new ArrayList<String>();
        for (Map.Entry<String,String> entry : yourCredentials.entrySet()) {
            if(p.matcher(String.valueOf(entry.getValue())).matches()){
                String message = String.format("The value for %s is empty. Please enter something for this value.", entry.getKey());
                errorBuffer.add(message);
            }
        }
        if (!errorBuffer.isEmpty()){
            String message = String.join("\n", errorBuffer);
            throw new Exception(message);
        }
    }
    public static Map<String, String> getCredentials() {
        try{
            checkPlaceholders();
        }catch(Exception e){
            System.out.println(e.toString());
        }
        
        return yourCredentials;
    }
}

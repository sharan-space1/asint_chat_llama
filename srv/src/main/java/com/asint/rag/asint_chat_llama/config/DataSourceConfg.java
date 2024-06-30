package com.asint.rag.asint_chat_llama.config;

import javax.sql.DataSource;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfg {
    
    @Bean
    public DataSource hanaDataSource() {

        try {
            JSONObject vcap_srv = new JSONObject(System.getenv("VCAP_SERVICES"));
            JSONArray up = vcap_srv.getJSONArray("user-provided");
            JSONObject target = new JSONObject();

            for (int i = 0; i < up.length(); ++i) {

                JSONObject srvObject = up.getJSONObject(i);

                if (srvObject.has("name") &&
                    srvObject.getString("name").equalsIgnoreCase("asint-silo-01-db-key")) {

                    target = srvObject.getJSONObject("credentials");
                    break;
                }
            }

            return DataSourceBuilder.create()
                    .url(target.getString("url"))
                    .driverClassName(target.getString("driver"))
                    .username(target.getString("username"))
                    .password(target.getString("password"))
                    .build();
        } catch (Exception exc) {

            return DataSourceBuilder.create()
                    .url("jdbc:sap://0b1a2bc5-bb7f-4581-b5e3-4ea3bb97097e.hna0.prod-us10.hanacloud.ondemand.com:443?encrypt=true&validateCertificate=true&currentschema=2BC7B824927A49E6A1B949B2E183F810")
                    .driverClassName("com.sap.db.jdbc.Driver")
                    .username("2BC7B824927A49E6A1B949B2E183F810_8X33EULRM43ZF200A73MYM0FK_RT")
                    .password("Pj3giPxu4l6pDyYArp1rlxzTAt-RWivrxfCu4Eq.2ITfa1y7OrCQlO1Hjly-jzlxcTbrOiUlzfxZykWWncR3ZF52Wj1ISHnT6UAutWZKft2mqXgHHc4Fgp7L4_iiqced")
                    .build();
        }
    }
}

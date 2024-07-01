package com.asint.rag.asint_chat_llama.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeEditor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.sap.cds.CdsVector;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnVector;
import com.sap.cds.services.persistence.PersistenceService;

import cds.gen.com.asint.asint_chat_llama.PromptEmbeddings;
import cds.gen.com.asint.asint_chat_llama.PromptEmbeddings_;
import io.netty.handler.codec.http.HttpContentEncoder.Result;

@Service
public class AiCoreWebClientService {
    
    private final WebClient webClient;

    private PersistenceService db;

    public AiCoreWebClientService(WebClient webClient, PersistenceService db) {
        this.webClient = webClient;
        this.db = db;
    }

    public void getTags() {

        try {
            String resp = webClient.get()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d05b2ef1b529d7e1/v1/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            System.out.println("***************Response is: " + resp);
        } catch (WebClientResponseException ex) {
            System.err.println("Error occurred while performing API Call: " + ex.getMessage());
        }
    }

    public boolean getEmbeddings(String prompt) {

        try {
            String body = "{ \"model\": \"phi3:latest\", \"prompt\": \"" + prompt + "\" }";

            String resp = webClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d05b2ef1b529d7e1/v1/api/embeddings")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JSONObject respObj = new JSONObject(resp);

            JSONArray aEmbedding = respObj.getJSONArray("embedding");


            float[] fEmbed = new float[aEmbedding.length()];

            for (int i = 0; i < aEmbedding.length(); ++i) {

                Float fValue = aEmbedding.getFloat(i);

                fEmbed[i] = fValue;

            }

            PromptEmbeddings newEmbed = PromptEmbeddings.create();

            CdsVector v1 = CdsVector.of(fEmbed);

            newEmbed.setPrompt(prompt);
            newEmbed.setEmbedding(v1);

            CqnInsert insert = Insert.into("com.asint.asint_chat_llama.PromptEmbeddings").entry(newEmbed);

            this.db.run(insert);

            return true;
        } catch (WebClientResponseException ex) {
            System.err.println("Error occurred while performing API Call: " + ex.getMessage());
        }

        return false;
    }

    public boolean determineIfPromptIsGoodToChatWithPhi3(String prompt) {

        try {
            String body = "{ \"model\": \"phi3:latest\", \"prompt\": \"" + prompt + "\" }";

            String resp = webClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d05b2ef1b529d7e1/v1/api/embeddings")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JSONObject respObj = new JSONObject(resp);

            JSONArray aEmbedding = respObj.getJSONArray("embedding");

            CqnVector v1 = CQL.vector(aEmbedding.toString());

            CqnSelect searchInDB = Select.from(PromptEmbeddings_.class).where(e ->
                CQL.cosineSimilarity(e.embedding(), v1).gt(0.8)
            );

            com.sap.cds.Result result = this.db.run(searchInDB);

            System.out.println(result.toString());

            if (result.rowCount() == 0) {

                return false;
            }

            return true;
            
        } catch (WebClientResponseException ex) {
            System.err.println("Error occurred while performing API Call: " + ex.getMessage());

            return false;
        }
    }

    public String askOllamaModel(String prompt) {

        try {
            String body = "{ " +
                "\"model\": \"phi3:latest\", " +
                "\"messages\": [ " +
                    "{" +
                        "\"role\": \"user\", " +
                        "\"content\": \"Why is the sky blue?\" " +
                    "}" +
                "]," +
                "\"stream\": true " +
            "}";

            String resp = webClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d28a51eb4dcaac8a/v1/api/chat")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JSONObject respObj = new JSONObject(resp);

            System.out.println(resp.toString());

            return resp;
            
        } catch (WebClientResponseException ex) {
            System.err.println("Error occurred while performing API Call: " + ex.getMessage());

            return "";
        }
    }
}

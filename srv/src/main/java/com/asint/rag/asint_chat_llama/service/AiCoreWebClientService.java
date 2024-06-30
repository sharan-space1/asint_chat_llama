package com.asint.rag.asint_chat_llama.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeEditor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import cds.gen.com.asint.asint_chat_llama.PromptEmbeddings;

@Service
public class AiCoreWebClientService {
    
    private final WebClient webClient;

    public AiCoreWebClientService(WebClient webClient) {
        this.webClient = webClient;
    }

    public void getTags() {

        try {
            String resp = webClient.get()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d82b89304e3a99a7/v1/api/tags")
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
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d82b89304e3a99a7/v1/api/embeddings")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            System.out.println("***************Response is: " + resp);

            return true;
        } catch (WebClientResponseException ex) {
            System.err.println("Error occurred while performing API Call: " + ex.getMessage());
        }

        return false;
    }
}

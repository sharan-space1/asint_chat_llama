package com.asint.rag.asint_chat_llama.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
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
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import reactor.core.publisher.Mono;

@Service
public class AiCoreWebClientService {

    private LocalDateTime tokenExpireTime;

	private String token;
    
    private final WebClient webClient;

    private WebClient mistralWebClient;

    private PersistenceService db;

    public boolean loadApi570Data;

    private final Logger LOGGER = LoggerFactory.getLogger(AiCoreWebClientService.class);

    public AiCoreWebClientService(WebClient webClient, PersistenceService db) {
        this.webClient = webClient;
        this.db = db;

        this.mistralWebClient = WebClient.builder()
                                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .filter(authorizeAndAddAiResourceGroup())
                                    .build();
    }

    public void getTags() {

        try {
            String resp = webClient.get()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d05b2ef1b529d7e1/v1/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            LOGGER.debug("*************** Response is: " + resp);
        } catch (WebClientResponseException ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());
        }
    }

    public void getTagsFromMistral() {

        try {

            String resp = mistralWebClient.get()
                            .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d2e3df758122a7ec/v1/api/tags")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                    
            LOGGER.debug("*************** Response is: " + resp);
        } catch (WebClientResponseException ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());
        }
    }

    public boolean getAndSaveEmbeddings(String prompt) {

        try {

            EmbeddingModel embeddingModel = MistralAiEmbeddingModel.withApiKey("7RjSy1qJWA8aUDXonvA3BfCkToJ3vMdb");

            float[] fEmbed = embeddingModel.embed(prompt).content().vector();

            PromptEmbeddings newEmbed = PromptEmbeddings.create();

            CdsVector v1 = CdsVector.of(fEmbed);

            newEmbed.setPrompt(prompt);
            newEmbed.setEmbedding(v1);

            CqnInsert insert = Insert.into(PromptEmbeddings_.class).entry(newEmbed);

            this.db.run(insert);

            return true;
        } catch (Exception ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());
        }

        return false;
    }

    public String determineIfPromptIsGoodToChatWithPhi3(String prompt) {

        try {

            EmbeddingModel embeddingModel = MistralAiEmbeddingModel.withApiKey("7RjSy1qJWA8aUDXonvA3BfCkToJ3vMdb");

            float[] fEmbed = embeddingModel.embed(prompt).content().vector();

            CqnVector v1 = CQL.vector(fEmbed);

            CqnSelect searchInDB = Select.from(PromptEmbeddings_.class).where(e ->
                CQL.cosineSimilarity(e.embedding(), v1).gt(0.8)
            );

            com.sap.cds.Result result = this.db.run(searchInDB);

            if (result.rowCount() == 0) {
                return "[{\"prompt\": \"Sorry I am trained to exclusively talk about piping inspection codes.\"}]";
            }

            return result.toString();
            
        } catch (Exception ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());

            return "[{\"prompt\": \"Sorry that does not seem right, maybe I need a break!\"}]";
        }
    }

    public String makeMistralBlockAfterEvaluatingNewPrompt(String prompt) {

        try {
            String body = "{ \"model\": \"mistral:latest\", \"prompt\": \"" + prompt + "\" }";

            String resp = mistralWebClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d2e3df758122a7ec/v1/api/embeddings")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JSONObject respObj = new JSONObject(resp);

            JSONArray aEmbedding = respObj.getJSONArray("embedding");

            CqnVector v1 = CQL.vector(aEmbedding.toString());

            CqnSelect searchInDB = Select.from(PromptEmbeddings_.class).where(e ->
                CQL.l2Distance(e.embedding(), v1).gt(0.9)
            );

            com.sap.cds.Result result = this.db.run(searchInDB);

            if (result.rowCount() == 0) {

                return "[{\"content\": \"Sorry I am trained to exclusively talk about piping inspection codes.\"}]";
            }

            return result.toString();
            
        } catch (WebClientResponseException ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());

            return "[{\"content\": \"Sorry I do not know how to answer that.\"}]";
        }
    }

    public String makeMistralReturnL2AfterGeneratingEmbeddings(String prompt1, String prompt2) {

        try {
            String body = "{ \"model\": \"mistral:latest\", \"prompt\": \"" + prompt1 + "\" }";

            String resp = mistralWebClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d2e3df758122a7ec/v1/api/embeddings")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JSONObject respObj = new JSONObject(resp);

            JSONArray aEmbedding = respObj.getJSONArray("embedding");

            CqnVector v1 = CQL.vector(aEmbedding.toString());

            body = "{ \"model\": \"mistral:latest\", \"prompt\": \"" + prompt2 + "\" }";

            resp = mistralWebClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d2e3df758122a7ec/v1/api/embeddings")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            respObj = new JSONObject(resp);

            aEmbedding = respObj.getJSONArray("embedding");

            CqnVector v2 = CQL.vector(aEmbedding.toString());

            return CQL.l2Distance(v1, v2).toString();
            
        } catch (WebClientResponseException ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());

            return "";
        }
    }

    public String askOllamaModel(String prompt) {

        try {
            String body = "{ " +
                "\"model\": \"phi3:latest\", " +
                "\"messages\": [ " +
                    "{" +
                        "\"role\": \"user\", " +
                        "\"content\": \"" + prompt + "\" " +
                    "}" +
                "]," +
                "\"stream\": true " +
            "}";

            String resp = webClient.post()
                .uri("https://api.ai.prod.eu-central-1.aws.ml.hana.ondemand.com/v2/inference/deployments/d05b2ef1b529d7e1/v1/api/chat")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            LOGGER.debug(resp.toString());

            return resp;
            
        } catch (WebClientResponseException ex) {
            LOGGER.error("Error occurred while performing API Call: " + ex.getMessage());

            return "";
        }
    }

    public boolean loadApi570Data() throws InterruptedException {

        Document d570Doc = FileSystemDocumentLoader.loadDocument("/home/user/projects/asint_chat_llama/srv/src/main/resources/API 570 2016.pdf");

        EmbeddingModel embeddingModel = MistralAiEmbeddingModel.withApiKey("7RjSy1qJWA8aUDXonvA3BfCkToJ3vMdb");

        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(1000, 200);

        String[] splits = splitter.split(d570Doc.text());

        Embedding queryEmbedding;
        List<PromptEmbeddings> allEmbeddings = new ArrayList<>();

        LOGGER.debug("Total: " + splits.length);

        for (int i = 0; i < splits.length; ++i) {

            PromptEmbeddings newEmbedding = PromptEmbeddings.create();

            queryEmbedding = embeddingModel.embed(splits[i]).content();

            CdsVector v1 = CdsVector.of(queryEmbedding.vector());

            LOGGER.debug("Processing: " + i);

            newEmbedding.setPrompt(splits[i]);
            newEmbedding.setEmbedding(v1);

            Thread.sleep(2000);

            allEmbeddings.add(newEmbedding);
        }

        CqnInsert insertBulk = Insert.into(PromptEmbeddings_.class).entries(allEmbeddings);

        this.db.run(insertBulk);

        return false;
    }

    private ExchangeFilterFunction authorizeAndAddAiResourceGroup() {

		return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {

			// If token is null or expired, retrieve a new one
			if (this.token == null || isTokenExpired()) {
				this.token = retrieveAccessToken();
			}

			ClientRequest authorizedRequest = ClientRequest.from(clientRequest)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
					.header("AI-Resource-Group", "default")
					.build();

			return Mono.just(authorizedRequest);
		});
	}

    private boolean isTokenExpired() {

		LocalDateTime now = LocalDateTime.now();
		return tokenExpireTime.isBefore(now);
	}

    private String retrieveAccessToken() {

		WebClient.ResponseSpec responseSpec = WebClient.create().post()
				.uri("https://dev-acc.authentication.eu10.hana.ondemand.com/oauth/token?grant_type=client_credentials")
				.headers(header -> header.setBasicAuth("sb-d33970e5-7f75-4344-9f78-781d41cbb624!b464181|aicore!b540", "28fc34bf-91f7-472f-8316-df9beeeec55a$ffM6AouBxFpAyEqwCKbQj7q3EXTyhTXPK7Fue9ROVWc="))
				.retrieve();

		String tokenResponse = responseSpec.bodyToMono(String.class).block();
		JSONObject tokenObj = new JSONObject(tokenResponse);
		int expireSeconds = tokenObj.getInt("expires_in");
		LocalDateTime datetime = LocalDateTime.now();
		this.tokenExpireTime = datetime.plusSeconds(expireSeconds);

		return tokenObj.getString("access_token");
	}
}

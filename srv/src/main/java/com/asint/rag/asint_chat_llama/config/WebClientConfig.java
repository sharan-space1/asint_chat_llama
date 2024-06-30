package com.asint.rag.asint_chat_llama.config;

import java.time.LocalDateTime;

import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {
    
    private LocalDateTime tokenExpireTime;

	private String token;

    @Bean
	public WebClient webClient() {

		return WebClient.builder()
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.filter(authorizeAndAddAiResourceGroup())
				.build();
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
				.uri("https://asint-payg-development.authentication.eu10.hana.ondemand.com/oauth/token?grant_type=client_credentials")
				.headers(header -> header.setBasicAuth("sb-b21750ba-89d5-42ac-801e-d23001ebc993!b244784|aicore!b540", "64b59b0a-5fe8-46d3-80ae-817f7d9d2c1b$fy3QaNtbDoPxqIyGgAsJXgrT3FJgavIZ4wPRN_wOByE="))
				.retrieve();

		String tokenResponse = responseSpec.bodyToMono(String.class).block();
		JSONObject tokenObj = new JSONObject(tokenResponse);
		int expireSeconds = tokenObj.getInt("expires_in");
		LocalDateTime datetime = LocalDateTime.now();
		this.tokenExpireTime = datetime.plusSeconds(expireSeconds);

		return tokenObj.getString("access_token");
	}
}

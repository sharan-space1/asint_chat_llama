package com.asint.rag.asint_chat_llama.evaluator;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.asint.rag.asint_chat_llama.service.AiCoreWebClientService;

@Service
public class MeasureL2AndCosForPrompts {
    
    private final AiCoreWebClientService aiCoreWebClientService;

    private Logger LOGGER = LoggerFactory.getLogger(MeasureL2AndCosForPrompts.class); 

    public MeasureL2AndCosForPrompts(AiCoreWebClientService aiCoreWebClientService) {
        this.aiCoreWebClientService = aiCoreWebClientService;
    }

    // @PostConstruct
    public void evaluateL2VsCos() {

        String l2 = this.aiCoreWebClientService.makeMistralReturnL2AfterGeneratingEmbeddings("Why are there 7 days in a week?", "There are 7 days in a week.");

        LOGGER.error("Close 1: ", l2);
    }
}

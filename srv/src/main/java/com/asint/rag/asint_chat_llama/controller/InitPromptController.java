package com.asint.rag.asint_chat_llama.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asint.rag.asint_chat_llama.service.AiCoreWebClientService;

@RestController
@RequestMapping("/rest/v1/api")
public class InitPromptController {

    private AiCoreWebClientService aiCoreWebClientService;

    public InitPromptController(AiCoreWebClientService aiCoreWebClientService) {
        this.aiCoreWebClientService = aiCoreWebClientService;
    }

    @PostMapping("/embed/prompt")
    public boolean savePromptEmbeddings(@RequestBody PromptDataEntity promptDataEntity) {

        return this.aiCoreWebClientService.getAndSaveEmbeddings(promptDataEntity.getPrompt());
    }

    @PostMapping("/new/prompt")
    public String evaluateNewPrompt(@RequestBody PromptDataEntity promptDataEntity) {

        return this.aiCoreWebClientService.determineIfPromptIsGoodToChatWithPhi3(promptDataEntity.getPrompt());
    }

    @PostMapping("/new/prompt/mistral/evaluate")
    public String evaluateNewPrompt2(@RequestBody PromptDataEntity promptDataEntity) {

        return this.aiCoreWebClientService.makeMistralBlockAfterEvaluatingNewPrompt(promptDataEntity.getPrompt());
    }

    @PostMapping("/load/api/570/data")
    public boolean loadApi570DataIntoHana() throws InterruptedException {
        
        return this.aiCoreWebClientService.loadApi570Data();
    }
    
}

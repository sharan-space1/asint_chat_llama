namespace com.asint.asint_chat_llama;

using {
  cuid,
  managed
} from '@sap/cds/common';

entity PromptEmbeddings : cuid, managed {
    
    prompt: LargeString;
    embedding: Vector(3);
}
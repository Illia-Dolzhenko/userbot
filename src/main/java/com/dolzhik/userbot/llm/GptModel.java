package com.dolzhik.userbot.llm;

import java.util.Optional;

import com.dolzhik.userbot.Utills;

import io.github.stefanbratanov.jvm.openai.ChatClient;
import io.github.stefanbratanov.jvm.openai.ChatCompletion;
import io.github.stefanbratanov.jvm.openai.ChatMessage;
import io.github.stefanbratanov.jvm.openai.CreateChatCompletionRequest;
import io.github.stefanbratanov.jvm.openai.OpenAI;
import io.github.stefanbratanov.jvm.openai.OpenAIException;
import io.github.stefanbratanov.jvm.openai.OpenAIModel;

public class GptModel implements LanguageModel {

    private final OpenAI openAI;
    private final ChatClient chatClient;

    private final static OpenAIModel EMOJI_MODEL = OpenAIModel.GPT_4o_MINI;
    private final static OpenAIModel TEXT_MODEL = OpenAIModel.GPT_4o_MINI;

    public GptModel(String token) {
        this.openAI = OpenAI.newBuilder(token).build();
        this.chatClient = openAI.chatClient();
    }

    @Override
    public Optional<String> llmWriteNewMessage(String systemPromt, String promt) {

        CreateChatCompletionRequest request = CreateChatCompletionRequest.newBuilder()
                // .model(OpenAIModel.GPT_3_5_TURBO)
                .model(TEXT_MODEL)
                .temperature(0.4)
                .presencePenalty(1.5)
                .frequencyPenalty(1.5)
                // .presencePenalty(0.75)
                // .model("accounts/fireworks/models/mixtral-8x22b-instruct")
                .message(ChatMessage.systemMessage(systemPromt))
                .message(
                        ChatMessage.userMessage(promt))
                .build();

        try {
            ChatCompletion completion = chatClient.createChatCompletion(request);
            return Utills.extractLlmResponse(completion).map(text -> Utills.removeSomeDotsAndCommas(text));
        } catch (OpenAIException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> llmReplyToMessage(String systemPromt, String promt) {

        CreateChatCompletionRequest request = CreateChatCompletionRequest.newBuilder()
                .presencePenalty(1.5)
                .frequencyPenalty(1.5)
                // .temperature(1.2)
                // .model(OpenAIModel.GPT_3_5_TURBO)
                .model(TEXT_MODEL)
                .temperature(0.4)
                // .model("accounts/fireworks/models/mixtral-8x22b-instruct")
                .message(ChatMessage.systemMessage(systemPromt))
                .message(
                        ChatMessage.userMessage(promt))
                .build();

        try {
            ChatCompletion completion = chatClient.createChatCompletion(request);
            return Utills.extractLlmResponse(completion).map(text -> Utills.removeSomeDotsAndCommas(text));
        } catch (OpenAIException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> llmChooseEmoji(String promt) {

        CreateChatCompletionRequest request = CreateChatCompletionRequest.newBuilder()
                // .model("accounts/fireworks/models/mixtral-8x22b-instruct")
                .temperature(0.5)
                .model(EMOJI_MODEL)
                .message(ChatMessage.systemMessage(promt))
                .build();

        try {
            ChatCompletion completion = chatClient.createChatCompletion(request);
            return Utills.extractLlmResponse(completion);
        } catch (OpenAIException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    // private Optional<String> messageRefiner(String message) {
    // System.out.println("Refining message: " + message);
    // CreateChatCompletionRequest request =
    // CreateChatCompletionRequest.newBuilder()
    // .model(OpenAIModel.GPT_4o)
    // .message(ChatMessage.systemMessage("You are an expert in Russian language.
    // Your goal is to refine the following message: \"" + message + "\" make sure
    // it looks like it was written by a native speaker in an informal speech. Slang
    // and swear words are allowed. Respond only with the corrected or original
    // version of the message."))
    // .build();

    // ChatCompletion completion = chatClient.createChatCompletion(request);

    // System.out.println("LLM reply: " + completion);

    // return Utills.extractLlmResponse(completion);
    // }

}

package com.dolzhik.userbot.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dolzhik.userbot.Utills;
import com.google.gson.Gson;

public class RestGemini implements LanguageModel {

    private final HttpClient httpClient;
    private final String token;
    private final String flash = "gemini-1.5-flash-latest";
    private final String pro = "gemini-1.5-pro-latest";
    private final Gson gson;

    public RestGemini(String token) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.token = token;
    }

    @Override
    public Optional<String> llmWriteNewMessage(String systemPromt, String promt) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + flash
                        + ":generateContent?key=" + token))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(systemPromt, promt, "1.0")))
                .build();
        try {
            var response = httpClient.send(request, BodyHandlers.ofString()).body();
            //System.out.println("LLM reply: " + response);
            return extractResponse(response).map(text -> Utills.removeSomeDotsAndCommas(text));

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> llmReplyToMessage(String systemPromt, String promt) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + flash
                        + ":generateContent?key=" + token))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(systemPromt, promt, "1.0")))
                .build();
        try {
            var response = httpClient.send(request, BodyHandlers.ofString()).body();
            //System.out.println("LLM reply: " + response);
            return extractResponse(response).map(text -> Utills.removeSomeDotsAndCommas(text));

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> llmChooseEmoji(String promt) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + flash
                        + ":generateContent?key=" + token))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody("You ara an expert of emoji captioning", promt, "1.0")))
                .build();
        try {
            var response = httpClient.send(request, BodyHandlers.ofString()).body();
            //System.out.println("LLM reply: " + response);
            return extractResponse(response).map(text -> text.trim());

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<String> extractResponse(String json) {
        try {
            Map<String, Object> responseMap = gson.fromJson(json, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
            Map<String, Object> content = (Map<String, Object>)((Map<String, Object>) candidates.get(0)).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");
            return Optional.of(text);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private String buildBody(String systemPromt, String promt, String temperature) {
        return """
                {
                "system_instruction":{
                    "parts":{
                        "text":"%s"
                    }
                },
                "contents":{
                    "parts":{
                        "text":"%s"
                    }
                },
                "safetySettings":[
                    {
                        "category":"HARM_CATEGORY_DANGEROUS_CONTENT",
                        "threshold":"BLOCK_NONE"
                    },
                    {
                        "category":"HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "threshold":"BLOCK_NONE"
                    },
                    {
                        "category":"HARM_CATEGORY_HATE_SPEECH",
                        "threshold":"BLOCK_NONE"
                    },
                    {
                        "category":"HARM_CATEGORY_HARASSMENT",
                        "threshold":"BLOCK_NONE"
                    }
                ],
                "generationConfig":{
                    "temperature":%s,
                    "maxOutputTokens":512,
                    "topP":0.8
                }
                }""".formatted(systemPromt.replace("\"", "\\\""), promt.replace("\"", "\\\""), temperature);
    }

}

package com.dolzhik.userbot.image;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;

public class RestGeminiCaptioner implements ImageCaptioner {

    private final HttpClient httpClient;
    private final String token;
    private final String flash = "gemini-1.5-flash-latest";
    private final Gson gson;


    public RestGeminiCaptioner(String token) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson(); 
        this.token = token;
    }

    @Override
    public Optional<String> caption(byte[] image) {
                var base64image = Base64.getEncoder().encodeToString(image);
                HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + flash
                        + ":generateContent?key=" + token))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody("Give me a short description of the most important elements of this picture: ", base64image, "1.0")))
                .build();
        try {
            var response = httpClient.send(request, BodyHandlers.ofString()).body();
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

    private String buildBody(String promt, String base64Image, String temperature) {
        return """
                {
                "contents":[
                    {
                        "parts":[
                            {"text":"%s"},
                                {
                                "inline_data": {
                                    "mime_type":"image/jpeg",
                                    "data": "%s"
                                }
                            }
                        ]
                    }
                ],
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
                }""".formatted(promt.replace("\"", "\\\""), base64Image, temperature);
    }
    
}

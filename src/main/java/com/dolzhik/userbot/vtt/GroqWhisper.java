package com.dolzhik.userbot.vtt;

import java.io.IOException;
import java.util.Optional;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;

public class GroqWhisper implements VoiceToText {

    private final CloseableHttpClient client;
    private final String token;

    // public static class ResponseHandler implements HttpClientResponseHandler<String> {

    //     @Override
    //     public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    //         System.out.println("Whisper response: " + response);
    //         System.out.println("headers: " + response.getHeaders());
    //         String content = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    //         System.out.println("content: " + content);
    //         return "";
    //     }
    
        
    // }
    public GroqWhisper(String token) {
        this.client = HttpClients.createDefault();
        this.token = token;
    }

    @Override
    public Optional<String> voiceToText(byte[] voice) {

        HttpPost post = new HttpPost("https://api.groq.com/openai/v1/audio/transcriptions");
        post.addHeader("Authorization", "bearer " + token);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("model", "whisper-large-v3");
        builder.addTextBody("language", "ru");
        builder.addTextBody("response_format", "text");
        builder.addBinaryBody("file", voice, ContentType.MULTIPART_FORM_DATA, "audio.mp3");
        post.setEntity(builder.build());

        try {
            var response = client.execute(post, new BasicHttpClientResponseHandler());
            return Optional.of(response);
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

}

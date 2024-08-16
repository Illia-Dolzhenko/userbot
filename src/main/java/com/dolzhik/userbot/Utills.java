package com.dolzhik.userbot;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.Random;

import io.github.stefanbratanov.jvm.openai.ChatCompletion;
import io.github.stefanbratanov.jvm.openai.ChatCompletion.Choice;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.Message;

public class Utills {

    public static Random random = new Random();

    public static String compilePromtForMessageReply(String historyContext, String message) {
        return "";
    }

    public static boolean chance(double chance) {
        var rand = random.nextDouble();
        // System.out.println("Random " + rand + " < " + chance + " = " + (rand <
        // chance));
        return rand < chance;
    }

    public static Optional<String> getTextFromMessage(Message message) {
        if (message.content instanceof TdApi.MessageText messageText) {
            return Optional.of(messageText.text.text);
        } else if (message.content instanceof TdApi.MessageSticker sticker) {
            return Optional.of(sticker.sticker.emoji);
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Long> getUserIdFromMessage(Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser user) {
            return Optional.of(user.userId);
        } else {
            return Optional.empty();
        }
    }

    public static long timeToType(String text) {
        long spaces = text.chars().filter(c -> c == ' ').count();
        long len = text.length();
        return spaces + len / 10;
    }

    public static Optional<String> extractLlmResponse(ChatCompletion completion) {
        return Optional.ofNullable(completion.choices()).map(choices -> choices.get(0)).map(Choice::message)
                .map(io.github.stefanbratanov.jvm.openai.ChatCompletion.Choice.Message::content);
    }

    public static Date timestampToDate(int timestamp) {
        return new Date((long) timestamp * 1000);
    }

    public static int getRandomFromRange(int min, int max) {
        return random.nextInt((max + 1) - min) + min;
    }

    public static String removeSomeDotsAndCommas(String text) {
        String filtered = text.trim().replace("—", "").replace("  ", " ").chars().filter(c -> {
            if (c == ',') {
                return chance(0.25);
            }
            return true;
        }).mapToObj(c -> String.valueOf((char) c)).reduce("", (a, b) -> a + b);
        if (filtered.charAt(filtered.length() - 1) == '.') {
            return filtered.substring(0, filtered.length() - 1);
        }
        return filtered;
    }

    public static boolean isInWorkingHours(Instant date) {
        return date.atZone(ZoneId.systemDefault()).getHour() > 8 || date.atZone(ZoneId.systemDefault()).getHour() < 1;
    }

    public static Optional<Long> getSenderId(Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser user) {
            return Optional.of(user.userId);
        } else if (message.senderId instanceof TdApi.MessageSenderChat chat) {
            return Optional.of(chat.chatId);
        }
        return Optional.empty();
    }

    public static boolean isPrivateChat(Chat chat) {
        return chat.type instanceof TdApi.ChatTypePrivate;
    }

}

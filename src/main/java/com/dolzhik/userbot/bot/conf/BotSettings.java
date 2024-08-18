package com.dolzhik.userbot.bot.conf;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import com.dolzhik.userbot.Utills;

import it.tdlight.jni.TdApi.Message;

public class BotSettings {

        public final SimpleDateFormat DATE_FORMAT;
        public final long USER_BOT_ID; // current Userbot id
        public final long TEST_CHAT_ID = -1002168781875L;
        public final long KURILKA_CHAT_ID = -1002097327825L; // kurilka: -1002097327825L // artem: 505803763L;
        public final long MAXIM_CHAT_ID = -1001923772833L;
        public final long MOPSIM = -1002045911703L;
        public final long CURRENT_CHAT;
        public final String USER_BOT_LOGIN; // current Userbot login
        public final String CHARACTER_NAME;
        public final List<Long> CHAT_IGNORE_LIST;

        public final List<String> EMOJI_LIST;
        public final String EMOJI;
        public final String BACKSTORY;
        public final String BACKSTORY_PRIVATE;

        public final double NEW_MESSAGE_CHANCE;
        public final double DIRECT_REPLY_CHANCE;
        public final double REACTION_CHANCE;
        public final double STICKER_CHANCE;
        public final double REPLY_NO_MENTION_CHANCE;
        public final double REPLY_TO_NAME_CHANCE;

        public final String SESSION_NAME;
        public final String TDLIB_API_HASH;
        public final int TDLIB_API_ID;
        public final String OPENAI_TOKEN;
        public final String GROQ_TOKEN;
        public final String GEMINI_TOKEN;
        public final long CACHE_CLEANING_INTERVAL;

        public BotSettings() throws IllegalStateException {
                try {
                        Properties properties = new Properties();
                        properties.load(new BufferedReader(
                                        new InputStreamReader(new FileInputStream("bot.properties"), "utf-8")));

                        USER_BOT_ID = Optional.ofNullable(properties.getProperty("user_bot_id"))
                                        .map(value -> Long.parseLong(value))
                                        .orElseThrow(() -> new IllegalStateException("user_bot_id property not found"));
                        CURRENT_CHAT = Optional.ofNullable(properties.getProperty("current_chat_id"))
                                        .map(value -> Long.parseLong(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "current_chat_id property not found"));
                        USER_BOT_LOGIN = Optional.ofNullable(properties.getProperty("user_bot_login"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "user_bot_login property not found"));
                        CHARACTER_NAME = Optional.ofNullable(properties.getProperty("character_name"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "character_name property not found"));
                        EMOJI = Optional.ofNullable(properties.getProperty("emoji"))
                                        .map(value -> value.replace(";", ""))
                                        .orElseThrow(() -> new IllegalStateException("emoji property not found"));
                        EMOJI_LIST = Collections.unmodifiableList(
                                        Optional.ofNullable(properties.getProperty("emoji"))
                                                        .map(value -> Arrays.asList(value.split(";")))
                                                        .orElseThrow(() -> new IllegalStateException(
                                                                        "emoji property not found")));

                        CHAT_IGNORE_LIST = Optional.ofNullable(properties.getProperty("chat_ignore_list"))
                                        .map(value -> Arrays.asList(value.split(";")))
                                        .map(list -> list.stream().map(Long::parseLong).collect(Collectors.toList()))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "chat_ignore_list property not found"));

                        BACKSTORY = Optional.ofNullable(properties.getProperty("backstory"))
                                        .orElseThrow(() -> new IllegalStateException("backstory property not found"));
                        BACKSTORY_PRIVATE = Optional.ofNullable(properties.getProperty("backstory_private"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "backstory_private property not found"));

                        NEW_MESSAGE_CHANCE = Optional.ofNullable(properties.getProperty("new_message_chance"))
                                        .map(value -> Double.parseDouble(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "new_message_chance property not found"));
                        DIRECT_REPLY_CHANCE = Optional.ofNullable(properties.getProperty("direct_reply_chance"))
                                        .map(value -> Double.parseDouble(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "direct_reply_chance property not found"));
                        REACTION_CHANCE = Optional.ofNullable(properties.getProperty("reaction_chance"))
                                        .map(value -> Double.parseDouble(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "reaction_chance property not found"));
                        STICKER_CHANCE = Optional.ofNullable(properties.getProperty("sticker_chance"))
                                        .map(value -> Double.parseDouble(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "sticker_chance property not found"));
                        REPLY_NO_MENTION_CHANCE = Optional.ofNullable(properties.getProperty("reply_no_mention_chance"))
                                        .map(value -> Double.parseDouble(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "reply_no_mention_chance property not found"));
                        REPLY_TO_NAME_CHANCE = Optional.ofNullable(properties.getProperty("reply_to_name_chance"))
                                        .map(value -> Double.parseDouble(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "reply_to_name_chance property not found"));
                        DATE_FORMAT = Optional.ofNullable(properties.getProperty("date_format"))
                                        .map(value -> new SimpleDateFormat(value))
                                        .orElseThrow(() -> new IllegalStateException("date_format property not found"));

                        SESSION_NAME = Optional.ofNullable(properties.getProperty("tdlib_session_name"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "session_name property not found"));
                        TDLIB_API_HASH = Optional.ofNullable(properties.getProperty("tdlib_api_hash"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "tdlib_api_hash property not found"));
                        OPENAI_TOKEN = Optional.ofNullable(properties.getProperty("openai_token"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "openai_token property not found"));
                        TDLIB_API_ID = Optional.ofNullable(properties.getProperty("tdlib_api_id"))
                                        .map(value -> Integer.parseInt(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "tdlib_api_id property not found"));
                        GROQ_TOKEN = Optional.ofNullable(properties.getProperty("groq_token"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "groq_token property not found"));
                        GEMINI_TOKEN = Optional.ofNullable(properties.getProperty("gemini_token"))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "gemini_token property not found"));
                        CACHE_CLEANING_INTERVAL = Optional.ofNullable(properties.getProperty("cache_cleaning_interval"))
                                        .map(value -> Long.parseLong(value))
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "cache_cleaning_interval property not found"));

                } catch (IOException e) {
                        e.printStackTrace();
                        throw new IllegalStateException("Error loading bot properties", e);
                }
        }

        public String compileChatEntry(String name, Message message,
                        Optional<String> inResponseTo) {
                var text = Utills.getTextFromMessage(message).orElse(" ")
                                .replace(USER_BOT_LOGIN, CHARACTER_NAME);
                var date = DATE_FORMAT.format(Utills.timestampToDate(message.date));
                var nameAndAddresedTo = (name + " " + inResponseTo.map(n -> "in response to " + n).orElse("")).trim();
                return date + " " + nameAndAddresedTo + ": " + text + "\n";
        }

        public String compileChatEntryVoice(String name, String text, int timestamp, Optional<String> inResponseTo) {
                var date = DATE_FORMAT.format(Utills.timestampToDate(timestamp));
                var nameAndAddresedTo = (name + " " + inResponseTo.map(n -> "in response to " + n).orElse("")).trim();
                return date + " " + nameAndAddresedTo + " (Voice message): " + text + "\n";
        }

        public String compileChatEntryVideo(String name, String text, int timestamp, Optional<String> inResponseTo) {
                var date = DATE_FORMAT.format(Utills.timestampToDate(timestamp));
                var nameAndAddresedTo = (name + " " + inResponseTo.map(n -> "in response to " + n).orElse("")).trim();
                return date + " " + nameAndAddresedTo + " (Video message): " + text + "\n";
        }

        public String compileChatEntryPhoto(String name, String description, String caption, int timestamp,
                        Optional<String> inResponseTo) {
                return String.format("%s %s (Message with picture): %s [Picture caption: %s]\n",
                                DATE_FORMAT.format(Utills.timestampToDate(timestamp)),
                                (name + " " + inResponseTo.map(n -> "in response to " + n).orElse("")).trim(),
                                description, caption);
        }

        public String buildPromtForReply(String message) {
                message = message.replace(USER_BOT_LOGIN, CHARACTER_NAME);
                return "New message received: \"" + message
                                + "\" As " + CHARACTER_NAME
                                + ", write a very short reply to it or to the one of the previous messges. Write the message in the informal style, with very simple structure. Follow the theme of the current discussion. Don't repeat your own messages. Don't start the message with your name.";
        }

        public String buildPromtForNewMessage() {
                return "As " + CHARACTER_NAME
                                + ", write a very short reply to the chat. Write the message in the informal style, with very simple structure. Follow the theme of the current discussion. Don't start the message with your name.";
        }

        public String buildPromtForChoosingEmoji(String message, String emojis) {
                return "Choose one emoji from the list (" + emojis
                                + ") to react to this message: \"" + message + "\" Respond with only ONE emoji.";
        }

        public String buildSystemPromt(String historyContext) {
                return BACKSTORY + historyContext;
        }

        public String buildSystemPromtPrivateChat(String historyContext) {
                return BACKSTORY_PRIVATE + historyContext;
        }

        public boolean isInIgnoreList(long chatId) {
                return CHAT_IGNORE_LIST.contains(chatId);
        }

}

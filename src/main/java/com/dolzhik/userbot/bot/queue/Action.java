package com.dolzhik.userbot.bot.queue;

import java.time.Instant;

import com.dolzhik.userbot.Utills;

import it.tdlight.jni.TdApi.UpdateNewMessage;

public class Action {
    private String type;
    private long chatId;
    private long messageId;
    private Instant date;

    public Action(String type, long chatId, long messageId, Instant date) {
        this.type = type;
        this.chatId = chatId;
        this.messageId = messageId;
        this.date = date;
    }

    public Action(UpdateNewMessage update, String type) {
        this(type, update.message.chatId, update.message.id, Utills.timestampToDate(update.message.date).toInstant());
    }

    public String getType() {
        return type;
    }

    public long getChatId() {
        return chatId;
    }

    public long getMessageId() {
        return messageId;
    }

    public Instant getDate() {
        return date;
    }

    public String toString() {
        return "Action{" +
                "type='" + type + '\'' +
                ", chatId=" + chatId +
                ", messageId=" + messageId +
                '}';
    }
}

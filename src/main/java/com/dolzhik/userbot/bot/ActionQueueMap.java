package com.dolzhik.userbot.bot;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.dolzhik.userbot.Action;
import com.dolzhik.userbot.Utills;

import it.tdlight.jni.TdApi;

public class ActionQueueMap {
    private final Map<Long, LinkedBlockingQueue<Action>> queues = new ConcurrentHashMap<>();

    public LinkedBlockingQueue<Action> getQueue(long chatId) {
        return queues.computeIfAbsent(chatId, id -> new LinkedBlockingQueue<>());
    }

    public void scheduleAction(TdApi.UpdateNewMessage message, String action) {
        scheduleAction(action, message.message.chatId, message.message.id,
                Utills.timestampToDate(message.message.date).toInstant());
    }

    public void scheduleAction(String action, long chatId, long messageId, Instant timestamp) {
        try {
            queues.get(chatId).put(new Action(action, chatId, messageId, timestamp));
            System.out.println("Action scheduled: " + action);
        } catch (InterruptedException e) {
            System.out.println("Can't schedule action: " + action);
            e.printStackTrace();
        }
    }
}

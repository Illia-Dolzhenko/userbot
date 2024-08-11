package com.dolzhik.userbot.bot;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import it.tdlight.jni.TdApi.User;

public class Cache {
    private Map<Long, User> users = new ConcurrentHashMap<>();
    private Map<Long, String> mediaCaptions = new ConcurrentHashMap<>();

    public Optional<User> getUser(long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    public void putUser(User user) {
        users.put(user.id, user);
    }

    public Optional<String> getMediaCaption(long messageId) {
        return Optional.ofNullable(mediaCaptions.get(messageId));
    }

    public void putMediaCaption(long messageId, String caption) {
        mediaCaptions.put(messageId, caption);

    }

    public void clear() {
        users.clear();
        mediaCaptions.clear();
    }
}

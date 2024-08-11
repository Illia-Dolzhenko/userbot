package com.dolzhik.userbot.bot;

public class CacheCleaner implements Runnable {

    private final Cache cache;

    public CacheCleaner(Cache cache) {
        this.cache = cache;
    }

    @Override
    public void run() {
        cache.clear();
    }
}

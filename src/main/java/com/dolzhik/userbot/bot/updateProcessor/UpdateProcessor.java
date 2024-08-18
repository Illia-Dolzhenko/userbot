package com.dolzhik.userbot.bot.updateProcessor;

import java.util.Optional;

import com.dolzhik.userbot.bot.conf.BotSettings;
import com.dolzhik.userbot.bot.queue.Action;

import it.tdlight.jni.TdApi;

public interface UpdateProcessor {
    Optional<Action> process(BotSettings settings, TdApi.UpdateNewMessage update);
}

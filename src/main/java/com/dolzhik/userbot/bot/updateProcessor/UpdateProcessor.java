package com.dolzhik.userbot.bot.updateProcessor;

import java.util.Optional;

import com.dolzhik.userbot.Action;
import com.dolzhik.userbot.conf.BotSettings;

import it.tdlight.jni.TdApi;

public interface UpdateProcessor {
    Optional<Action> process(BotSettings settings, TdApi.UpdateNewMessage update);
}

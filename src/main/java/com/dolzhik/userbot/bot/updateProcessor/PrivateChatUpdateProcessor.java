package com.dolzhik.userbot.bot.updateProcessor;

import java.util.Optional;

import com.dolzhik.userbot.Action;
import com.dolzhik.userbot.Utills;
import com.dolzhik.userbot.conf.BotSettings;

import it.tdlight.jni.TdApi;

public class PrivateChatUpdateProcessor implements UpdateProcessor {

    @Override
    public Optional<Action> process(BotSettings settings, TdApi.UpdateNewMessage update) {

        if (update.message.chatId != settings.CURRENT_CHAT) {
            var text = Utills.getTextFromMessage(update.message).orElse(update.message.content.getClass().getName());

            if (Utills.chance(settings.DIRECT_REPLY_CHANCE)) {
                System.out.println("Decided to reply to the message: " + text);
                if (Utills.chance(0.5)) {
                    return Optional.of(new Action(update, "reply"));
                } else {
                    return Optional.of(new Action(update, "new"));
                }
            }

            if (Utills.chance(settings.REACTION_CHANCE)) {
                System.out.println("Decided to add a reaction to the message: " + text);
                return Optional.of(new Action(update, "reaction"));
            }

            if (Utills.chance(settings.STICKER_CHANCE)) {
                System.out.println("Decided to reply with a sticker to the message: " + text);
                return Optional.of(new Action(update, "sticker"));
            }
        }

        return Optional.empty();
    }

}

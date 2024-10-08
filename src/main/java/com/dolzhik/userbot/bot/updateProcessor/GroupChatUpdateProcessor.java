package com.dolzhik.userbot.bot.updateProcessor;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dolzhik.userbot.Utills;
import com.dolzhik.userbot.bot.conf.BotSettings;
import com.dolzhik.userbot.bot.queue.Action;

import it.tdlight.jni.TdApi.UpdateNewMessage;

public class GroupChatUpdateProcessor implements UpdateProcessor {

    private final Logger logger = LoggerFactory.getLogger(GroupChatUpdateProcessor.class);

    @Override
    public Optional<Action> process(BotSettings settings, UpdateNewMessage update) {

        if (update.message.chatId == settings.CURRENT_CHAT) {
            var chatId = update.message.chatId;
            var text = Utills.getTextFromMessage(update.message).orElse(update.message.content.getClass().getName());

            if (update.message.containsUnreadMention && Utills.chance(settings.DIRECT_REPLY_CHANCE)) {
                logger.info("Chat {} contains unread mention", chatId);
                return Optional.of(new Action(update, "reply"));
            }

            if (Utills.chance(settings.STICKER_CHANCE)) {
                if (Utills.chance(0.5)) {
                    logger.info("Decided to reply with a sticker to the message: " + text);
                    return Optional.of(new Action(update, "sticker"));
                }
                logger.info("Decided to add a reaction to the message: " + text);
                return Optional.of(new Action(update, "reaction"));
            }

            if (Utills.chance(settings.NEW_MESSAGE_CHANCE)) {
                logger.info("Decided to write a new message to chat: " + chatId);
                return Optional.of(new Action(update, "new"));
            }

            if (Utills.chance(settings.REPLY_NO_MENTION_CHANCE)) {
                logger.info("Decided to reply (no mention) to the message: " + text);
                return Optional.of(new Action(update, "reply_no_mention"));
            }

            if (Utills.chance(settings.REPLY_TO_NAME_CHANCE) && text.toLowerCase().contains(settings.CHARACTER_NAME.toLowerCase().substring(0,
                            settings.CHARACTER_NAME.length() - 1))) {
                logger.info("Decided to reply (character name mentioned) to the message: " + text);
                return Optional.of(new Action(update, "reply_no_mention"));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

}

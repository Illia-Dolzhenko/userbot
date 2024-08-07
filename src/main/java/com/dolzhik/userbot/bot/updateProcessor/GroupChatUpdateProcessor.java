package com.dolzhik.userbot.bot.updateProcessor;

import java.util.Optional;

import com.dolzhik.userbot.Action;
import com.dolzhik.userbot.Utills;
import com.dolzhik.userbot.conf.BotSettings;

import it.tdlight.jni.TdApi.UpdateNewMessage;

public class GroupChatUpdateProcessor implements UpdateProcessor {

    @Override
    public Optional<Action> process(BotSettings settings, UpdateNewMessage update) {

        if (update.message.chatId == settings.CURRENT_CHAT) {
            var chatId = update.message.chatId;
            var text = Utills.getTextFromMessage(update.message).orElse(update.message.content.getClass().getName());

            boolean repliedToMetion = false;
            if (update.message.containsUnreadMention && Utills.chance(settings.DIRECT_REPLY_CHANCE)) {
                System.out.println("Chat " + chatId + " contains unread mention");
                Optional.of(new Action(update, "reply"));
                repliedToMetion = true;
            }

            boolean addedReaction = false;
            if (Utills.chance(settings.REACTION_CHANCE) && !repliedToMetion) {
                System.out.println("Decided to add a reaction to the message: " + text);
                Optional.of(new Action(update, "reaction"));
                addedReaction = true;
            }

            boolean repliedWithSticker = false;
            if (Utills.chance(settings.STICKER_CHANCE) && !repliedToMetion && !addedReaction) {
                System.out.println("Decided to reply with a sticker to the message: " + text);
                Optional.of(new Action(update, "sticker"));
                repliedWithSticker = true;
            }

            boolean newMessageSent = false;
            if (Utills.chance(settings.NEW_MESSAGE_CHANCE) && !repliedToMetion && !repliedWithSticker) {
                System.out.println("Decided to write a new message to chat: " + chatId);
                Optional.of(new Action(update, "new"));
                newMessageSent = true;
            }

            boolean repliedNoMention = false;
            if (Utills.chance(settings.REPLY_NO_MENTION_CHANCE) && !repliedToMetion && !newMessageSent) {
                System.out.println("Decided to reply to the message: " + text);
                Optional.of(new Action(update, "reply_no_mention"));
                repliedNoMention = true;
            }

            if (Utills.chance(settings.REPLY_TO_NAME_CHANCE) && !repliedToMetion && !newMessageSent
                    && !repliedNoMention
                    && text.toLowerCase().contains(settings.CHARACTER_NAME.toLowerCase().substring(0,
                            settings.CHARACTER_NAME.length() - 1))) {
                System.out.println("Decided to reply to the message: " + text);
                Optional.of(new Action(update, "reply_no_mention"));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

}

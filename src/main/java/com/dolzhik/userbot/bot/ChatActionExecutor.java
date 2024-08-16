package com.dolzhik.userbot.bot;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dolzhik.userbot.Action;
import com.dolzhik.userbot.Userbot.App;
import com.dolzhik.userbot.Utills;

public class ChatActionExecutor implements Runnable {

    protected final LinkedBlockingQueue<Action> actionQueue;
    protected final Map<Long, Instant> lastIsTypingActionMap;
    protected final App app;
    protected AtomicBoolean stopped;
    protected AtomicLong lastReceivedAction = new AtomicLong(Instant.now().toEpochMilli());
    private final Logger logger = LoggerFactory.getLogger(ChatActionExecutor.class);

    public ChatActionExecutor(LinkedBlockingQueue<Action> actionQueue, Map<Long, Instant> lastIsTypingActionMap, App app) {
        this.actionQueue = actionQueue;
        this.lastIsTypingActionMap = lastIsTypingActionMap;
        this.app = app;
        this.stopped = new AtomicBoolean(false);
        logger.info("ChatExecutor created");
    }

    @Override
    public void run() {
        while (!stopped.get()) {
            while (actionQueue.peek() != null) {
                try {
                    Action action = actionQueue.peek();
                    logger.info("ChatExecutor received an action: " + action);

                    if (action.getDate().isBefore(Instant.now().minus(2, ChronoUnit.MINUTES))) {
                        logger.info("Action {} is older than 2 minutes. Skipping", action);
                        break;
                    }

                    if (app.getChat(action.getChatId()).map(chat -> Utills.isPrivateChat(chat)).orElse(false)) {
                        //This is a private chat
                        var wasTypingLessThanMinuteAgo = Optional.ofNullable(lastIsTypingActionMap.get(action.getChatId())).map(chatActionDate -> {
                            return chatActionDate.isAfter(Instant.now().plusSeconds(60));
                        }).orElse(false);

                        if (wasTypingLessThanMinuteAgo) {
                            logger.debug("This is a private chat and the sender was typing less than 60 seconds ago. Not taking action: " + action);
                            Duration duration = Duration.between(lastIsTypingActionMap.get(action.getChatId()), Instant.now());
                            logger.info("Waiting after the last typing chat action for {} seconds", duration.getSeconds());
                            Thread.sleep(duration);
                            break;
                        }
                    }
                    //Actually take the action from the queue
                    action = actionQueue.take();
                    lastReceivedAction.set(Instant.now().toEpochMilli());

                    if ("reply".equals(action.getType())) {
                        if (!action.getDate().isBefore(Instant.now().minusSeconds(30))) { // if message was sent
                                                                                          // less than 30
                                                                                          // seconds ago wait
                                                                                          // for 10-60 seconds
                            var wait = Utills.getRandomFromRange(10, 60);
                            logger.info("Waiting {} seconds", wait);
                            Thread.sleep(Duration.ofSeconds(wait));
                        }
                        app.replyToMessage(action.getChatId(), action.getMessageId());
                    }

                    if ("new".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(10, 60);
                        logger.info("Waiting {} seconds", wait);
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.newMessage(action.getChatId());
                    }

                    if ("reaction".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(1, 5);
                        logger.info("Waiting {} seconds", wait);
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.reactToMessage(action.getChatId(), action.getMessageId());
                    }

                    if ("sticker".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(5, 10);
                        logger.info("Waiting {} seconds", wait);
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.replyWithSticker(action.getChatId(), action.getMessageId());
                    }

                    if ("reply_no_mention".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(5, 10);
                        logger.info("Waiting {} seconds", wait);
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.replyToMessage(action.getChatId(), action.getMessageId());
                    }

                    if ("chats".equals(action.getType())) {
                        app.test();
                    }

                    if ("test_read".equals(action.getType())) {
                        var messages = app.readChatHistory(30, action.getChatId(), 0);
                        var history = app.buildHistoryContext(messages);
                        logger.info(history);
                    }

                } catch (InterruptedException e) {
                    logger.error("Failed to take action from the chat specific action queue", e);
                }
            }

            if (Instant.ofEpochMilli(lastReceivedAction.get()).isBefore(Instant.now().minus(60, ChronoUnit.MINUTES))) {
                logger.info("Last action received more than 60 minutes ago. Stopping the executor");
                stop();
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error("Failed to sleep", e);
            }
        }
    }

    public void stop() {
        stopped.set(true);
    }

    public boolean isStopped() {
        return stopped.get();
    }

}

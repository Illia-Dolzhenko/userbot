package com.dolzhik.userbot.bot;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.LinkedBlockingQueue;

import com.dolzhik.userbot.Action;
import com.dolzhik.userbot.Userbot.App;
import com.dolzhik.userbot.Utills;

public class GroupChatExecutor extends ChatExecutor {

    public GroupChatExecutor(LinkedBlockingQueue<Action> actionQueue, App app) {
        super(actionQueue, app);
    }

    @Override
    public void run() {
       while (!stopped.get()) {
            while (actionQueue.peek() != null) {
                try {
                    Action action = actionQueue.take();
                    System.out.println("Received action: " + action);

                    if (action.getDate().isBefore(Instant.now().minus(2, ChronoUnit.MINUTES))) {
                        System.out.println("Action is older than 3 minutes. Skipping: " + action);
                        break;
                    }

                    if ("reply".equals(action.getType())) {
                        if (!action.getDate().isBefore(Instant.now().minusSeconds(30))) { // if message was sent
                                                                                          // less than 30
                                                                                          // seconds ago wait
                                                                                          // for 10-60 seconds
                            var wait = Utills.getRandomFromRange(10, 60);
                            System.out.println("Waiting " + wait + " seconds");
                            Thread.sleep(Duration.ofSeconds(wait));
                        }
                        app.replyToMessage(action.getChatId(), action.getMessageId());
                    }

                    if ("new".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(10, 60);
                        System.out.println("Waiting " + wait + " seconds");
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.newMessage(action.getChatId());
                    }

                    if ("reaction".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(1, 5);
                        System.out.println("Waiting " + wait + " seconds");
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.reactToMessage(action.getChatId(), action.getMessageId());
                    }

                    if ("sticker".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(5, 10);
                        System.out.println("Waiting " + wait + " seconds");
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.replyWithSticker(action.getChatId(), action.getMessageId());
                    }

                    if ("reply_no_mention".equals(action.getType())) {
                        var wait = Utills.getRandomFromRange(5, 10);
                        System.out.println("Waiting " + wait + " seconds");
                        Thread.sleep(Duration.ofSeconds(wait));
                        app.replyToMessage(action.getChatId(), action.getMessageId());
                    }

                    if ("chats".equals(action.getType())) {
                        app.test();
                    }

                    if ("test_read".equals(action.getType())) {
                        var messages = app.readChatHistory(30, action.getChatId(), 0);
                        var history = app.buildHistoryContext(messages);
                        System.out.println(history);
                    }

                } catch (InterruptedException e) {
                    System.out.println("Can't take action from the queue");
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
}

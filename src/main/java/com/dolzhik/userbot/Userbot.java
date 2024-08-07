package com.dolzhik.userbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.dolzhik.userbot.bot.ActionQueueMap;
import com.dolzhik.userbot.bot.Cache;
import com.dolzhik.userbot.bot.ChatExecutor;
import com.dolzhik.userbot.bot.updateProcessor.GroupChatUpdateProcessor;
import com.dolzhik.userbot.bot.updateProcessor.PrivateChatUpdateProcessor;
import com.dolzhik.userbot.bot.updateProcessor.UpdateProcessor;
import com.dolzhik.userbot.conf.BotSettings;
import com.dolzhik.userbot.image.ImageCaptioner;
import com.dolzhik.userbot.image.RestGeminiCaptioner;
import com.dolzhik.userbot.llm.GptModel;
import com.dolzhik.userbot.llm.LanguageModel;
import com.dolzhik.userbot.vtt.GroqWhisper;
import com.dolzhik.userbot.vtt.VoiceToText;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleAuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationState;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.ChatAction;
import it.tdlight.jni.TdApi.File;
import it.tdlight.jni.TdApi.FormattedText;
import it.tdlight.jni.TdApi.InputMessageReplyToMessage;
import it.tdlight.jni.TdApi.InputMessageText;
import it.tdlight.jni.TdApi.Message;
import it.tdlight.jni.TdApi.MessageContent;
import it.tdlight.jni.TdApi.MessageSenderUser;
import it.tdlight.jni.TdApi.SendMessage;
import it.tdlight.jni.TdApi.TextEntity;
import it.tdlight.jni.TdApi.User;

public final class Userbot {

	private static BotSettings botSettings;
	private static final ActionQueueMap actionQueueMap = new ActionQueueMap();
	private static final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
	private static final Map<Long, ChatExecutor> executors = new HashMap<>();

	public static void main(String[] args) throws Exception {
		long adminId = Integer.getInteger("it.tdlight.example.adminid", 894954498);
		try {
			botSettings = new BotSettings();
		} catch (IllegalStateException e) {
			e.printStackTrace();
			return;
		}

		// Initialize TDLight native libraries
		Init.init();

		// Set the log level
		Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());

		// Create the client factory (You can create more than one client,
		// BUT only a single instance of ClientFactory is allowed globally!
		// You must reuse it if you want to create more than one client!)
		try (SimpleTelegramClientFactory clientFactory = new SimpleTelegramClientFactory()) {
			// Obtain the API token
			//
			var apiToken = new APIToken(botSettings.TDLIB_API_ID, botSettings.TDLIB_API_HASH);
			//

			// Configure the client
			TDLibSettings settings = TDLibSettings.create(apiToken);

			// Configure the session directory.
			// After you authenticate into a session, the authentication will be skipped
			// from the next restart!
			// If you want to ensure to match the authentication supplier user/bot with your
			// session user/bot,
			// you can name your session directory after your user id, for example:
			// "tdlib-session-id12345"
			Path sessionPath = Paths.get(botSettings.SESSION_NAME);
			settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
			settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

			// Prepare a new client builder
			SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);

			// Configure the authentication info
			// Replace with AuthenticationSupplier.consoleLogin(), or .user(xxx), or
			// .bot(xxx);
			// SimpleAuthenticationSupplier<?> authenticationData =
			// AuthenticationSupplier.testUser(7381);
			SimpleAuthenticationSupplier<?> authenticationData = AuthenticationSupplier.qrCode();

			// This is an example, remove this line to use the real telegram datacenters!
			// settings.setUseTestDatacenter(true);

			// Create and start the client
			try (var app = new App(clientBuilder, authenticationData, adminId, new GptModel(botSettings.OPENAI_TOKEN),
					new GroqWhisper(botSettings.GROQ_TOKEN), new RestGeminiCaptioner(botSettings.GEMINI_TOKEN))) {

				while (true) {
					while (app.getActionQueue().peek() != null) {
						try {
							Action action = app.getActionQueue().take();
							System.out.println("Received action: " + action);

							actionQueueMap.getQueue(action.getChatId()).put(action);

							if ((executors.containsKey(action.getChatId())
									&& executors.get(action.getChatId()).isStopped())
									|| !executors.containsKey(action.getChatId())) {
								ChatExecutor executor = new ChatExecutor(actionQueueMap.getQueue(action.getChatId()),
										app);
								executors.put(action.getChatId(), executor);
								forkJoinPool.execute(executor);
							}
							// For the selected group chat, skip action if it was sent more than 2 minutes
							// ago
							// if ( action.getChatId() == botSettings.CURRENT_CHAT &&
							// action.getDate().isBefore(Instant.now().minus(2, ChronoUnit.MINUTES))) {
							// System.out.println("Action is older than 2 minutes. Skipping: " + action);
							// break;
							// }

							// if ("reply".equals(action.getType())) {
							// if (!action.getDate().isBefore(Instant.now().minusSeconds(30))) { // if
							// message was sent
							// // less than 30
							// // seconds ago wait
							// // for 10-60 seconds
							// var wait = Utills.getRandomFromRange(10, 60);
							// System.out.println("Waiting " + wait + " seconds");
							// Thread.sleep(Duration.ofSeconds(wait));
							// }
							// app.replyToMessage(action.getChatId(), action.getMessageId());
							// }

							// if ("new".equals(action.getType())) {
							// var wait = Utills.getRandomFromRange(10, 60);
							// System.out.println("Waiting " + wait + " seconds");
							// Thread.sleep(Duration.ofSeconds(wait));
							// app.newMessage(action.getChatId());
							// }

							// if ("reaction".equals(action.getType())) {
							// var wait = Utills.getRandomFromRange(1, 5);
							// System.out.println("Waiting " + wait + " seconds");
							// Thread.sleep(Duration.ofSeconds(wait));
							// app.reactToMessage(action.getChatId(), action.getMessageId());
							// }

							// if ("sticker".equals(action.getType())) {
							// var wait = Utills.getRandomFromRange(5, 10);
							// System.out.println("Waiting " + wait + " seconds");
							// Thread.sleep(Duration.ofSeconds(wait));
							// app.replyWithSticker(action.getChatId(), action.getMessageId());
							// }

							// if ("reply_no_mention".equals(action.getType())) {
							// var wait = Utills.getRandomFromRange(5, 10);
							// System.out.println("Waiting " + wait + " seconds");
							// Thread.sleep(Duration.ofSeconds(wait));
							// app.replyToMessage(action.getChatId(), action.getMessageId());
							// }

							// if ("chats".equals(action.getType())) {
							// app.test();
							// }

							// if ("test_read".equals(action.getType())) {
							// var messages = app.readChatHistory(30, action.getChatId(),
							// action.getMessageId());
							// var history = app.buildHistoryContext(messages);
							// System.out.println(history);
							// }

						} catch (InterruptedException e) {
							System.out.println("Can't take action from the queue");
							e.printStackTrace();
						}
					}

					// Chat specific action for Kurilka
					// Check if the last message was sent less than 60 minutes ago and in working
					// hours
					// if (Instant.ofEpochMilli(app.getLastMessageTimeStamp())
					// .isBefore(Instant.now().minus(60, ChronoUnit.MINUTES))
					// && Utills.isInWorkingHours(Instant.now())) {
					// try {
					// System.out.println(
					// "Chat is not active for at least 60 minutes. And it is working hours. Pausing
					// main thread for 60 seconds");
					// Thread.sleep(Duration.ofSeconds(60));
					// if (Utills.chance(0.01)) { // 1% chance to send new message
					// System.out.println("Sending new message due to inactivity");
					// app.updateLastMessageTimeStamp(Instant.now().toEpochMilli());
					// app.newMessage(botSettings.CURRENT_CHAT);
					// }
					// } catch (Exception e) {
					// e.printStackTrace();
					// }

					// }
					// Check for new tasks every 1 second
					Thread.sleep(1000);
				}
			}
		}
	}

	public static class App implements AutoCloseable {

		private final SimpleTelegramClient client;

		private final LinkedBlockingQueue<Action> actions = new LinkedBlockingQueue<>();
		private final AtomicLong lastMessageTimeStamp = new AtomicLong(Instant.now().toEpochMilli());

		private final LanguageModel llm;
		private final VoiceToText voiceToText;
		private final ImageCaptioner imageCaptioner;

		private final Cache cache = new Cache();
		private final List<UpdateProcessor> updateProcessors = Arrays.asList(new PrivateChatUpdateProcessor(),
				new GroupChatUpdateProcessor());

		// private HashMap<Long, User> userCache = new HashMap<>();
		// private HashMap<Long, String> voiceToTextCache = new HashMap<>();
		// private HashMap<Long, String> imageCaptionCache = new HashMap<>();

		/**
		 * Admin user id, used by the stop command example
		 */
		private final long adminId;

		public App(SimpleTelegramClientBuilder clientBuilder,
				SimpleAuthenticationSupplier<?> authenticationData,
				long adminId,
				LanguageModel model,
				VoiceToText voiceToText,
				ImageCaptioner imageCaptioner) {
			this.adminId = adminId;
			this.llm = model;
			this.voiceToText = voiceToText;
			this.imageCaptioner = imageCaptioner;

			// Add an example update handler that prints when the bot is started
			clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::onUpdateAuthorizationState);

			// Add an example command handler that stops the bot
			clientBuilder.addCommandHandler("stop", this::onStopCommand);

			// Add an example update handler that prints every received message
			clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onUpdateNewMessage);

			// Build the client
			this.client = clientBuilder.build(authenticationData);
		}

		@Override
		public void close() throws Exception {
			client.close();
		}

		public SimpleTelegramClient getClient() {
			return client;
		}

		/**
		 * Print the bot status
		 */
		private void onUpdateAuthorizationState(TdApi.UpdateAuthorizationState update) {
			AuthorizationState authorizationState = update.authorizationState;
			if (authorizationState instanceof TdApi.AuthorizationStateReady) {
				System.out.println("Logged in");
			} else if (authorizationState instanceof TdApi.AuthorizationStateClosing) {
				System.out.println("Closing...");
			} else if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
				System.out.println("Closed");
			} else if (authorizationState instanceof TdApi.AuthorizationStateLoggingOut) {
				System.out.println("Logging out...");
			}
		}

		/**
		 * Print new messages received via updateNewMessage
		 */
		private void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
			// Get the message content
			MessageContent messageContent = update.message.content;
			long chatId = update.message.chatId;

			if (botSettings.isInIgnoreList(chatId)) {
				System.out.println("Chat " + chatId + " is in ignore list. Ignoring message from this chat");
				return;
			}

			if (chatId == adminId) {
				// Received message from admin
				if (messageContent instanceof TdApi.MessageText messageText) {
					System.out.println("Received new message from admin: " + messageText.text.text);
					if ("new".equals(messageText.text.text)) {
						scheduleAction("new", botSettings.CURRENT_CHAT, 0, Instant.now());
						return;
					}

					if ("chats".equals(messageText.text.text)) {
						scheduleAction("chats", 0, 0, Instant.now());
						return;
					}

					if ("test_read".equals(messageText.text.text)) {
						scheduleAction("test_read", botSettings.CURRENT_CHAT, update.message.id, Instant.now());
						return;
					}
				}
			}

			// kurilka chatId == -1002097327825L
			if (update.message.senderId instanceof TdApi.MessageSenderUser user
					&& user.userId != botSettings.USER_BOT_ID && (update.message.content instanceof TdApi.MessageText
							|| update.message.content instanceof TdApi.MessageVoiceNote
							|| update.message.content instanceof TdApi.MessagePhoto
							|| update.message.content instanceof TdApi.MessageVideoNote)) {

				// Update last message timestamp
				lastMessageTimeStamp.set(Utills.timestampToDate(update.message.date).toInstant().toEpochMilli());

				// Get the message text
				String text;
				if (messageContent instanceof TdApi.MessageText messageText) {
					// Get the text of the text message
					text = messageText.text.text;
				} else {
					// We handle only text messages, the other messages will be printed as their
					// type
					text = String.format("(%s)", messageContent.getClass().getSimpleName());
				}

				System.out.printf("Received new message in chat (%s) from user (%s): %s%n", chatId, user.userId, text);

				if (Utills.timestampToDate(update.message.date).before(Date.from(Instant.now().minusSeconds(60)))) {
					System.out.println("Message is too old, Skipping");
					return;
				}

				updateProcessors.stream().map(processor -> processor.process(botSettings, update))
						.filter(Optional::isPresent).forEach(action -> {
							scheduleAction(action.get());
						});

				// if (update.message.chatId == botSettings.CURRENT_CHAT) {
				// // SELECTED GROUP CHAT FLOW
				// boolean repliedToMetion = false;
				// if (update.message.containsUnreadMention &&
				// Utills.chance(botSettings.DIRECT_REPLY_CHANCE)) {
				// System.out.println("Chat " + chatId + " contains unread mention");
				// scheduleAction(update, "reply");
				// repliedToMetion = true;
				// }

				// boolean addedReaction = false;
				// if (Utills.chance(botSettings.REACTION_CHANCE) && !repliedToMetion) {
				// System.out.println("Decided to add a reaction to the message: " + text);
				// scheduleAction(update, "reaction");
				// addedReaction = true;
				// }

				// boolean repliedWithSticker = false;
				// if (Utills.chance(botSettings.STICKER_CHANCE) && !repliedToMetion &&
				// !addedReaction) {
				// System.out.println("Decided to reply with a sticker to the message: " +
				// text);
				// scheduleAction(update, "sticker");
				// repliedWithSticker = true;
				// }

				// boolean newMessageSent = false;
				// if (Utills.chance(botSettings.NEW_MESSAGE_CHANCE) && !repliedToMetion &&
				// !repliedWithSticker) {
				// System.out.println("Decided to write a new message to chat: " + chatId);
				// scheduleAction(update, "new");
				// newMessageSent = true;
				// }

				// boolean repliedNoMention = false;
				// if (Utills.chance(botSettings.REPLY_NO_MENTION_CHANCE) && !repliedToMetion &&
				// !newMessageSent) {
				// System.out.println("Decided to reply to the message: " + text);
				// scheduleAction(update, "reply_no_mention");
				// repliedNoMention = true;
				// }

				// if (Utills.chance(botSettings.REPLY_TO_NAME_CHANCE) && !repliedToMetion &&
				// !newMessageSent
				// && !repliedNoMention
				// &&
				// text.toLowerCase().contains(botSettings.CHARACTER_NAME.toLowerCase().substring(0,
				// botSettings.CHARACTER_NAME.length() - 1))) {
				// System.out.println("Decided to reply to the message: " + text);
				// scheduleAction(update, "reply_no_mention");
				// }
				// } else if (update.message.chatId != -1002097327825L) {
				// // REGULAR CHATS FLOW INCLUDING PRIVATE

				// if (Utills.chance(botSettings.DIRECT_REPLY_CHANCE)) {
				// System.out.println("Decided to reply to the message: " + text);
				// if (Utills.chance(0.5)) {
				// scheduleAction(update, "reply");
				// } else {
				// scheduleAction(update, "new");
				// }
				// return;
				// }

				// if (Utills.chance(botSettings.REACTION_CHANCE)) {
				// System.out.println("Decided to add a reaction to the message: " + text);
				// scheduleAction(update, "reaction");
				// }

				// if (Utills.chance(botSettings.STICKER_CHANCE)) {
				// System.out.println("Decided to reply with a sticker to the message: " +
				// text);
				// scheduleAction(update, "sticker");
				// }
				// }

			}

		}

		/**
		 * Close the bot if the /stop command is sent by the administrator
		 */
		private void onStopCommand(TdApi.Chat chat, TdApi.MessageSender commandSender, String arguments) {
			// Check if the sender is the admin
			if (isAdmin(commandSender)) {
				// Stop the client
				System.out.println("Received stop command. closing...");
				client.sendClose();
			}
		}

		/**
		 * Check if the command sender is admin
		 */
		public boolean isAdmin(TdApi.MessageSender sender) {
			if (sender instanceof MessageSenderUser messageSenderUser) {
				return messageSenderUser.userId == adminId;
			} else {
				return false;
			}
		}

		public LinkedBlockingQueue<Action> getActionQueue() {
			return actions;
		}

		public List<Message> readChatHistory(int size, long chatId, long messageId) {
			System.out.println("Reading chat history from " + chatId);
			var offset = -5; // get 5 newer messages
			var numberOfMessages = size;
			var fromId = messageId;
			List<Message> messageList = new ArrayList<Message>();

			while (numberOfMessages > 0) {
				try {
					System.out.println("Reading chat history from: " + fromId + " offset: " + offset
							+ " numberOfMessages: " + numberOfMessages);
					var messages = client
							.send(new TdApi.GetChatHistory(chatId, fromId, offset, numberOfMessages, false))
							.get(1, TimeUnit.MINUTES);

					System.out.println("Got chunk of " + messages.totalCount + " messages");

					if (messages.totalCount == 0) {
						break;
					}

					if (offset < 0) {
						offset += messages.messages.length;
						if (offset > 0) {
							numberOfMessages -= offset;
							offset = 0;
						}
					} else {
						numberOfMessages -= messages.messages.length;
					}

					fromId = messages.messages[messages.messages.length - 1].id;
					messageList.addAll(Arrays.asList(messages.messages));
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					e.printStackTrace();
					break;
				}
			}
			System.out.println("Got total " + messageList.size() + " messages");
			// messageList.forEach(m -> System.out.println("Message: " + m.id));

			// if (messageList.size() >= 4) {
			// var subList = messageList.subList(0, 4);
			// var toRemove = subList.stream().filter(a -> subList.stream().filter(b -> a.id
			// == b.id).count() > 1).map(m -> m.id).toList();
			// System.out.println("Removing duplicates: " + toRemove.size());
			// toRemove.forEach(m -> System.out.println("Removing: " + m));
			// messageList.removeIf(m -> toRemove.contains(m.id));
			// }
			// if (messageList.size() >= 2) {
			// if (messageList.get(0).id == messageList.get(1).id) {
			// System.out.println("Removing duplicate message " + messageList.get(0).id);
			// messageList.removeFirst();
			// }
			// }
			messageList.removeIf(a -> messageList.stream().filter(b -> a.id == b.id).count() > 1);
			return messageList; // To remove duplicates
		}

		private void simulateStatusFor(long seconds, long chatId, ChatAction action) {
			System.out.println("Simulating action for " + seconds + " seconds");
			for (int i = 0; i < seconds; i += 2) {
				try {
					client.send(new TdApi.SendChatAction(chatId, 0, action));
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		public void replyToMessage(long chatId, long messageId) {
			System.out.println("Replying to a message " + messageId + " in chat " + chatId);

			var messages = readChatHistory(30, chatId, messageId);
			openChat(chatId, messages.stream().map(m -> m.id).toList());

			var historyContext = buildHistoryContext(messages);
			var message = getMessage(chatId, messageId);

			message.ifPresent(messageToReply -> {
				Utills.getTextFromMessage(messageToReply).or(() -> { // If the message is not a text message try to get
																		// voice transcript
					return switch (messageToReply.content) {
						case TdApi.MessageVoiceNote voice ->
							getVoice(voice).flatMap(file -> voiceToText(file, messageId));
						case TdApi.MessagePhoto photo ->
							getPhoto(photo).flatMap(file -> captionPhoto(file, messageId)).map(caption -> String
									.format("%s [Picture caption: %s]", photo.caption.text, caption).trim());
						case TdApi.MessageVideoNote video ->
							getVideoNote(video).flatMap(file -> voiceToText(file, messageId));
						default -> Optional.empty();
					};

				}).ifPresent(messageText -> {
					Utills.getUserIdFromMessage(messageToReply).flatMap(this::getUserInfo).ifPresent(user -> {

						String messageInfo = user.firstName + " " + user.lastName + ": " + messageText;
						var systemPromt = chatId == botSettings.CURRENT_CHAT
								? botSettings.buildSystemPromt(historyContext)
								: botSettings.buildSystemPromtPrivateChat(historyContext);

						llm.llmReplyToMessage(systemPromt,
								botSettings.buildPromtForReply(messageInfo))
								.ifPresent(llmResponse -> {
									System.out.println("Generated reply: " + llmResponse);
									simulateStatusFor(Utills.timeToType(llmResponse), chatId,
											new TdApi.ChatActionTyping());

									var request = new SendMessage();
									var replyTo = new InputMessageReplyToMessage();
									replyTo.chatId = chatId;
									replyTo.messageId = messageId;
									request.chatId = chatId;
									request.replyTo = replyTo;
									var inputMessageText = new InputMessageText();
									inputMessageText.text = new FormattedText(llmResponse, new TextEntity[0]);
									request.inputMessageContent = inputMessageText;
									client.sendMessage(request, true);
								});
					});
				});
			});

			//closeChat(chatId);
		}

		public void newMessage(long chatId) {
			System.out.println("Sending new message to chat " + chatId);

			var messages = readChatHistory(20, chatId, 0L);
			openChat(chatId, messages.stream().map(m -> m.id).toList());
			var historyContext = buildHistoryContext(messages);
			var systemPromt = chatId == botSettings.CURRENT_CHAT ? botSettings.buildSystemPromt(historyContext)
					: botSettings.buildSystemPromtPrivateChat(historyContext);

			llm.llmWriteNewMessage(systemPromt, botSettings.buildPromtForNewMessage())
					.ifPresent(message -> {
						System.out.println("Generated message: " + message);
						simulateStatusFor(Utills.timeToType(message), chatId, new TdApi.ChatActionTyping());

						var request = new SendMessage();

						request.chatId = chatId;
						var messageText = new InputMessageText();
						messageText.text = new FormattedText(message, new TextEntity[0]);
						request.inputMessageContent = messageText;
						client.sendMessage(request, true);
					});

			//closeChat(chatId);
		}

		private String chooseEmoji(String context, String message) {
			return chooseEmoji(context, message, null);
		}

		private String chooseEmoji(String context, String message, List<String> emojis) {

			// TODO: llm
			String emojiSelection = botSettings.EMOJI;
			if (emojis != null && !emojis.isEmpty()) {
				emojiSelection = emojis.stream().reduce("", (a, b) -> a + b);
			}

			return llm.llmChooseEmoji(botSettings.buildPromtForChoosingEmoji(message, emojiSelection))
					.orElseGet(() -> chooseRandomEmoji(emojis));
			// return chooseRandomEmoji(emojis);
		}

		private String chooseRandomEmoji() {
			return chooseRandomEmoji(null);
		}

		private String chooseRandomEmoji(List<String> emojis) {
			if (emojis != null && !emojis.isEmpty()) {
				return emojis.get(new Random().nextInt(emojis.size()));
			}
			return botSettings.EMOJI_LIST.get(new Random().nextInt(botSettings.EMOJI_LIST.size()));
		}

		public String buildHistoryContext(List<Message> messages) {
			StringBuilder stringBuilder = new StringBuilder();
			messages.reversed().stream()
					.filter(message -> message.content instanceof TdApi.MessageText
							|| message.content instanceof TdApi.MessageSticker
							|| message.content instanceof TdApi.MessageVoiceNote
							|| message.content instanceof TdApi.MessagePhoto
							|| message.content instanceof TdApi.MessageVideoNote)
					.forEach(message -> {
						getTitleOrName(message.senderId).ifPresent(name -> {
							switch (message.content) {
								case TdApi.MessageVoiceNote voiceNote -> getVoice(voiceNote)
										.flatMap(file -> voiceToText(file, message.id)).ifPresent(text -> {
											stringBuilder.append(
													botSettings.compileChatEntryVoice(name,
															text, message.date,
															getNameMessageUserOfMessageReplyTo(message)));
										});
								case TdApi.MessagePhoto photo -> getPhoto(photo)
										.flatMap(bytes -> captionPhoto(bytes, message.id)).ifPresent(caption -> {
											System.out.println("Caption: " + caption);
											stringBuilder
													.append(botSettings.compileChatEntryPhoto(name, photo.caption.text,
															caption, message.date,
															getNameMessageUserOfMessageReplyTo(message)));
										});
								case TdApi.MessageVideoNote videoNote -> getVideoNote(videoNote)
										.flatMap(file -> voiceToText(file, message.id)).ifPresent(text -> {
											stringBuilder.append(
													botSettings.compileChatEntryVideo(name,
															text, message.date,
															getNameMessageUserOfMessageReplyTo(message)));
										});
								default -> stringBuilder.append(
										botSettings.compileChatEntry(name, message,
												getNameMessageUserOfMessageReplyTo(message)));

							}
							;

							// if (message.content instanceof TdApi.MessageVoiceNote voice) {
							// getVoice(voice).flatMap(file -> voiceToText(file, message.id)).ifPresent(text
							// -> {
							// stringBuilder.append(
							// Utills.compileChatEntryVoice(name,
							// text, message.date));
							// });
							// } else if (message.content instanceof TdApi.MessageVideoNote videoNote) {
							// // videoNote.videoNote.speechRecognitionResult
							// } else if (message.content instanceof TdApi.MessagePhoto photo) {
							// getPhoto(photo).flatMap(bytes -> captionPhoto(bytes,
							// message.id)).ifPresent(caption -> {
							// System.out.println("Caption: " + caption);
							// stringBuilder.append(
							// Utills.compileChatEntryPhoto(name, photo.caption.text, caption,
							// message.date));
							// });
							// } else {
							// stringBuilder.append(
							// Utills.compileChatEntry(name, message,
							// getNameMessageUserOfMessageReplyTo(message)));
							// }
						});
					});
			System.out.println("History context: " + stringBuilder.toString());
			return stringBuilder.toString();
		}

		public void replyWithSticker(long chatId, long messageId) {
			try {
				System.out.println("Replying to a message " + messageId + " in chat " + chatId + " with sticker");

				var message = client.send(new TdApi.GetMessage(chatId, messageId)).get();
				openChat(chatId, Arrays.asList(message.id));

				String selectedEmoji = Utills.getTextFromMessage(message).map(text -> chooseEmoji("", text))
						.orElseGet(() -> chooseRandomEmoji());

				System.out.println("Selected emoji: " + selectedEmoji);

				var stickers = client
						.send(new TdApi.GetStickers(new TdApi.StickerTypeRegular(), selectedEmoji, 100, chatId)).get();
				System.out.println("Got " + stickers.stickers.length + " stickers");

				if (stickers.stickers.length > 0) {
					var sticker = stickers.stickers[new Random().nextInt(stickers.stickers.length)];

					simulateStatusFor(3, chatId, new TdApi.ChatActionChoosingSticker());

					var request = new SendMessage();
					var replyTo = new InputMessageReplyToMessage();
					replyTo.chatId = chatId;
					replyTo.messageId = messageId;
					request.chatId = chatId;
					request.replyTo = replyTo;
					var messageSticker = new TdApi.InputMessageSticker();
					messageSticker.sticker = new TdApi.InputFileRemote(sticker.sticker.remote.id);
					request.inputMessageContent = messageSticker;
					client.sendMessage(request, true);
				} else {
					System.out.println("No stickers found for emoji " + selectedEmoji);
				}

			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				//closeChat(chatId);
			}
			//closeChat(chatId);
		}

		public void reactToMessage(long chatId, long messageId) {
			System.out.println("Reacting to a message " + messageId + " in chat " + chatId);
			try {
				var reactions = client.send(new TdApi.GetMessageAvailableReactions(chatId, messageId, 15)).get();
				System.out.println("Got " + reactions.topReactions.length + " topReactions reactions");
				var availableReactions = Stream.of(reactions.topReactions)
						.filter(reaction -> reaction.type instanceof TdApi.ReactionTypeEmoji && !reaction.needsPremium)
						.map(reaction -> {
							var emoji = (TdApi.ReactionTypeEmoji) reaction.type;
							return emoji.emoji;
						}).collect(Collectors.toList());

				System.out.println("Available reactions: ");
				availableReactions.forEach(emoji -> System.out.print(emoji));
				System.out.println();

				var message = client.send(new TdApi.GetMessage(chatId, messageId)).get();
				openChat(chatId, Arrays.asList(message.id));

				// var history = readChatHistory(10, chatId, messageId);
				// var historyContext = buildHistoryContext(history);

				var selectedEmoji = chooseEmoji("", Utills.getTextFromMessage(message).orElse(" "),
						availableReactions);

				System.out.println("Selected emoji: " + selectedEmoji);

				var request = new TdApi.AddMessageReaction();
				request.chatId = chatId;
				request.messageId = messageId;
				request.reactionType = new TdApi.ReactionTypeEmoji(selectedEmoji);
				client.send(request).get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				//closeChat(chatId);
			}
			//closeChat(chatId);
		}

		private Optional<Message> getMessage(long chatId, long messageId) {
			try {
				var message = client.send(new TdApi.GetMessage(chatId, messageId)).get();
				return Optional.of(message);
			} catch (InterruptedException | ExecutionException e) {
				System.out.println("Can't get message " + messageId + " in chat " + chatId);
				e.printStackTrace();
				return Optional.empty();
			}
		}

		private Optional<User> getUserInfo(long userId) {
			return cache.getUser(userId).or(() -> {
				try {
					User userInfo = client.send(new TdApi.GetUser(userId)).get();
					cache.putUser(userInfo);
					return Optional.of(userInfo);
				} catch (ExecutionException | InterruptedException e) {
					e.printStackTrace();
					return Optional.empty();
				}
			});
		}

		private Optional<Chat> getChatInfo(long chatId) {
			try {
				Chat chatInfo = client.send(new TdApi.GetChat(chatId)).get();
				return Optional.of(chatInfo);

			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
				return Optional.empty();
			}
		}

		private void scheduleAction(TdApi.UpdateNewMessage message, String action) {
			scheduleAction(action, message.message.chatId, message.message.id,
					Utills.timestampToDate(message.message.date).toInstant());
		}

		private void scheduleAction(String action, long chatId, long messageId, Instant timestamp) {
			scheduleAction(new Action(action, chatId, messageId, timestamp));
		}

		private void scheduleAction(Action action) {
			try {
				actions.put(action);
				System.out.println("Action scheduled: " + action);
			} catch (InterruptedException e) {
				System.out.println("Can't schedule action: " + action);
				e.printStackTrace();
			}
		}

		public long getLastMessageTimeStamp() {
			return lastMessageTimeStamp.get();
		}

		public void updateLastMessageTimeStamp(long timestamp) {
			lastMessageTimeStamp.set(timestamp);
		}

		// Download voice note from remote and use ffmpeg to convert voice note to mp3
		private Optional<byte[]> getVoice(TdApi.MessageVoiceNote message) {
			int fileId = message.voiceNote.voice.id;
			return Optional.ofNullable(message.voiceNote.voice.local.path).filter(p -> !p.isEmpty())
					.or(() -> {
						System.out.println("Downloading voice note: " + fileId);
						return downloadFile(fileId).map(file -> file.local.path);
					}).flatMap(path -> {
						return convertToMp3(path, fileId);
					});
		}

		// Download voice note from remote and use ffmpeg to convert voice note to mp3
		private Optional<byte[]> getVideoNote(TdApi.MessageVideoNote message) {
			int fileId = message.videoNote.video.id;
			return Optional.ofNullable(message.videoNote.video.local.path).filter(p -> !p.isEmpty())
					.or(() -> {
						System.out.println("Downloading video note: " + fileId);
						return downloadFile(fileId).map(file -> file.local.path);
					}).flatMap(path -> {
						return convertToMp3(path, fileId);
					});
		}

		private Optional<File> downloadFile(int fileId) {
			try {
				System.out.println("Downloading file: " + fileId);
				return Optional.ofNullable(client.send(new TdApi.DownloadFile(fileId, 1, 0L, 0L, true))
						.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				System.out.println("Can't get file: " + fileId);
				return Optional.empty();
			}
		}

		private Optional<byte[]> convertToMp3(String filePath, int fileId) {
			try {
				var code = Runtime.getRuntime()
						.exec(new String[] { "ffmpeg", "-y", "-i", filePath,
								"./" + botSettings.SESSION_NAME + "/downloads/" + fileId + ".mp3" })
						.waitFor();
				System.out.println("FFmpeg exit code: " + code);
				System.out.println("Got voice/video note: " + filePath);
				return Optional.ofNullable(Files.readAllBytes(
						Paths.get("./" + botSettings.SESSION_NAME + "/downloads/" + fileId + ".mp3")));
			} catch (IOException | InterruptedException e) {
				System.out.println("Can't convert voice/video note: " + fileId);
				e.printStackTrace();
				return Optional.empty();
			}
		}

		private Optional<byte[]> getPhoto(TdApi.MessagePhoto message) {
			return Stream.of(message.photo.sizes)
					.filter(size -> "y".equals(size.type))
					.findFirst()
					.or(() -> Stream.of(message.photo.sizes)
							.filter(size -> "x".equals(size.type))
							.findFirst())
					.or(() -> Stream.of(message.photo.sizes)
							.filter(size -> "m".equals(size.type))
							.findFirst())
					.map(size -> {
						if (size.photo.local.path != null && !size.photo.local.path.isEmpty()) {
							System.out.println("Local path already exists: " + size.photo.local.path);
							return size.photo.local.path;
						}
						try {
							System.out.println("Downloading photo: " + size.photo.id);
							File file = client.send(new TdApi.DownloadFile(size.photo.id, 1, 0L, 0L, true)).get();
							return file.local.path;
						} catch (InterruptedException | ExecutionException e) {
							e.printStackTrace();
							return null;
						}
					}).map(path -> {
						try {
							System.out.println("Downloaded photo: " + path);
							return Files.readAllBytes(Paths.get(path));
						} catch (IOException e) {
							e.printStackTrace();
							return null;
						}
					});
		}

		private Optional<String> getTitleOrName(TdApi.MessageSender sender) {
			if (sender instanceof TdApi.MessageSenderUser user) {
				if (user.userId == botSettings.USER_BOT_ID) {
					return Optional.of("You");
				}
				return getUserInfo(user.userId).map(userInfo -> (userInfo.firstName + " " + userInfo.lastName).trim());
			} else if (sender instanceof TdApi.MessageSenderChat chat) {
				return getChatInfo(chat.chatId).map(chatInfo -> chatInfo.title.trim());
			} else {
				return Optional.empty();
			}
		}

		private Optional<String> getNameMessageUserOfMessageReplyTo(TdApi.Message message) {
			try {
				TdApi.MessageReplyToMessage reply = (TdApi.MessageReplyToMessage) message.replyTo;
				if (reply != null && reply.chatId != 0 && reply.messageId != 0) {
					return getMessage(reply.chatId, reply.messageId).flatMap(m -> getTitleOrName(m.senderId));
				}
				return Optional.empty();
			} catch (ClassCastException e) {
				System.out.println("Can't get name of message user of message reply to:" + message);
				e.printStackTrace();
				return Optional.empty();
			}
		}

		private Optional<String> voiceToText(byte[] voice, long messageId) {
			return cache.getMediaCaption(messageId).or(() -> {
				var caption = voiceToText.voiceToText(voice);
				caption.ifPresent(text -> {
					cache.putMediaCaption(messageId, text);
				});
				return caption;
			});
		}

		private Optional<String> captionPhoto(byte[] photo, long messageId) {
			return cache.getMediaCaption(messageId).or(() -> {
				var caption = imageCaptioner.caption(photo);
				caption.ifPresent(text -> {
					cache.putMediaCaption(messageId, text);
				});
				return caption;
			});
		}

		private void openChat(long chatId, List<Long> messageIds) {
			try {
				client.send(new TdApi.OpenChat(chatId)).get(60, TimeUnit.SECONDS);
				TdApi.ViewMessages viewMessages = new TdApi.ViewMessages();
				viewMessages.chatId = chatId;
				viewMessages.messageIds = messageIds.stream().mapToLong((l) -> l).toArray();
				viewMessages.forceRead = true;
				client.send(viewMessages).get(60, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				System.out.println("Can't open chat: " + chatId);
				e.printStackTrace();
			}
		}

		private void closeChat(long chatId) {
			try {
				client.send(new TdApi.CloseChat(chatId)).get(60, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				System.out.println("Can't close chat: " + chatId);
				e.printStackTrace();
			}
		}

		public void test() {
			System.out.println("Testing...");
			var request = new TdApi.GetChats();
			request.limit = 100;
			try {
				var chats = client.send(request).get();
				for (long chatId : chats.chatIds) {
					client.send(new TdApi.GetChat(chatId))
							.whenCompleteAsync((chatIdResult, error) -> {
								if (error != null) {
									System.err.printf("Can't get chat title of chat %s%n", chatId);
									error.printStackTrace(System.err);
								} else {
									String title = chatIdResult.title;
									System.out.printf("Chat %s -> (%s)%n", title, chatId);
								}
							});
				}
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
	}
}

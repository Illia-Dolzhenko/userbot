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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dolzhik.userbot.bot.cache.Cache;
import com.dolzhik.userbot.bot.cache.CacheCleaner;
import com.dolzhik.userbot.bot.conf.BotSettings;
import com.dolzhik.userbot.bot.executor.ChatActionExecutor;
import com.dolzhik.userbot.bot.queue.Action;
import com.dolzhik.userbot.bot.queue.ActionQueueMap;
import com.dolzhik.userbot.bot.updateProcessor.GroupChatUpdateProcessor;
import com.dolzhik.userbot.bot.updateProcessor.PrivateChatUpdateProcessor;
import com.dolzhik.userbot.bot.updateProcessor.UpdateProcessor;
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
import it.tdlight.jni.TdApi.SendMessage;
import it.tdlight.jni.TdApi.TextEntity;
import it.tdlight.jni.TdApi.User;

public final class Userbot {

	private static BotSettings botSettings;
	private static final ActionQueueMap actionQueueMap = new ActionQueueMap();
	private static final Map<Long, Instant> lastIsTypingActionMap = new ConcurrentHashMap<>();
	private static final ExecutorService executorService = Executors.newCachedThreadPool();
	private static final ScheduledExecutorService scheduledExecutorService = Executors
			.newSingleThreadScheduledExecutor();
	private static final Map<Long, ChatActionExecutor> executors = new HashMap<>();
	private static Logger logger = LoggerFactory.getLogger(Userbot.class);

	public static void main(String[] args) throws Exception {
		long adminId = Integer.getInteger("it.tdlight.example.adminid", 894954498);
		try {
			botSettings = new BotSettings();
		} catch (IllegalStateException e) {
			logger.error("Failed to load bot settings", e);
			return;
		}

		// Initialize TDLight native libraries
		Init.init();

		// Set the log level
		Log.setLogMessageHandler(-1, new Slf4JLogMessageHandler());
		Log.disable(); 

		try (SimpleTelegramClientFactory clientFactory = new SimpleTelegramClientFactory()) {
			var apiToken = new APIToken(botSettings.TDLIB_API_ID, botSettings.TDLIB_API_HASH);

			// Configure the client
			TDLibSettings settings = TDLibSettings.create(apiToken);
			Path sessionPath = Paths.get(botSettings.SESSION_NAME);
			settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
			settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

			// Prepare a new client builder
			SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);

			// Configure the authentication info
			SimpleAuthenticationSupplier<?> authenticationData = AuthenticationSupplier.qrCode();

			// Create and start the client
			try (var app = new App(clientBuilder, authenticationData, adminId, lastIsTypingActionMap,
					new GptModel(botSettings.OPENAI_TOKEN),
					new GroqWhisper(botSettings.GROQ_TOKEN), new RestGeminiCaptioner(botSettings.GEMINI_TOKEN))) {

				// Start the cache cleaner task, which will clean the cache every 24 hours
				scheduledExecutorService.scheduleAtFixedRate(new CacheCleaner(app.getCache()),
						botSettings.CACHE_CLEANING_INTERVAL, botSettings.CACHE_CLEANING_INTERVAL, TimeUnit.HOURS);

				while (true) {
					while (app.getActionQueue().peek() != null) {
						try {
							Action action = app.getActionQueue().take();
							logger.debug("Received action from the main queue: " + action);

							actionQueueMap.getQueue(action.getChatId()).put(action);

							if ((executors.containsKey(action.getChatId())
									&& executors.get(action.getChatId()).isStopped())
									|| !executors.containsKey(action.getChatId())) {
								ChatActionExecutor executor = new ChatActionExecutor(
										actionQueueMap.getQueue(action.getChatId()),
										lastIsTypingActionMap,
										app);
								executors.put(action.getChatId(), executor);
								executorService.execute(executor);
							}

						} catch (InterruptedException e) {
							logger.error("Can't take action from the main queue", e);
						}
					}

					// Check for new tasks every 0.1 second
					Thread.sleep(100);
				}
			}
		}
	}

	public static class App implements AutoCloseable {

		private final SimpleTelegramClient client;
		private final Logger logger = LoggerFactory.getLogger(App.class);

		private final LinkedBlockingQueue<Action> actions = new LinkedBlockingQueue<>();
		private final Map<Long,Instant> lastIsTypingActionMap;

		private final LanguageModel llm;
		private final VoiceToText voiceToText;
		private final ImageCaptioner imageCaptioner;

		private final Cache cache = new Cache();
		private final List<UpdateProcessor> updateProcessors = Arrays.asList(new PrivateChatUpdateProcessor(),
				new GroupChatUpdateProcessor());

		private final long adminId;

		public App(SimpleTelegramClientBuilder clientBuilder,
				SimpleAuthenticationSupplier<?> authenticationData,
				long adminId,
				Map<Long, Instant> lastIsTypingActionMap,
				LanguageModel model,
				VoiceToText voiceToText,
				ImageCaptioner imageCaptioner) {
			this.adminId = adminId;
			this.llm = model;
			this.voiceToText = voiceToText;
			this.imageCaptioner = imageCaptioner;
			this.lastIsTypingActionMap = lastIsTypingActionMap;

			// Add an update handler that prints when the bot is started
			clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::onUpdateAuthorizationState);

			// Add an update handler for received messages
			clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onUpdateNewMessage);

			// Add an update handler for received chat actions
			clientBuilder.addUpdateHandler(TdApi.UpdateChatAction.class, this::onUpdateChatAction);

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
				logger.info( "Logged in");
			} else if (authorizationState instanceof TdApi.AuthorizationStateClosing) {
				logger.info( "Closing...");
			} else if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
				logger.info( "Closed");
			} else if (authorizationState instanceof TdApi.AuthorizationStateLoggingOut) {
				logger.info( "Logging out...");
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
				logger.info("Chat " + chatId + " is in ignore list. Ignoring message from this chat");
				return;
			}

			if (chatId == adminId) {
				// Received message from admin
				if (messageContent instanceof TdApi.MessageText messageText) {
					logger.info("Received new message from admin: " + messageText.text.text);
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

			if (update.message.senderId instanceof TdApi.MessageSenderUser user
					&& user.userId != botSettings.USER_BOT_ID && (update.message.content instanceof TdApi.MessageText
							|| update.message.content instanceof TdApi.MessageVoiceNote
							|| update.message.content instanceof TdApi.MessagePhoto
							|| update.message.content instanceof TdApi.MessageVideoNote)) {

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

				logger.info("Received new message in chat {} from user {}: {}", chatId, user.userId, text);

				if (Utills.timestampToDate(update.message.date).before(Date.from(Instant.now().minusSeconds(60)))) {
					logger.info("Message is too old, Skipping");
					return;
				}

				updateProcessors.stream().map(processor -> processor.process(botSettings, update))
						.filter(Optional::isPresent).forEach(action -> {
							scheduleAction(action.get());
						});

			}

		}

		private void onUpdateChatAction(TdApi.UpdateChatAction update) {
			if (update.action instanceof TdApi.ChatActionTyping) {
				lastIsTypingActionMap.put(update.chatId, Instant.now());
			}
		}

		public LinkedBlockingQueue<Action> getActionQueue() {
			return actions;
		}

		public List<Message> readChatHistory(int size, long chatId, long messageId) {
			logger.debug("Reading chat history from " + chatId);
			var offset = -5; // get 5 newer messages
			var numberOfMessages = size;
			var fromId = messageId;
			List<Message> messageList = new ArrayList<Message>();

			while (numberOfMessages > 0) {
				try {
					logger.debug("Reading chat history from: {}, offset: {}, numberOfMessages: {}", fromId, offset, numberOfMessages);
					var messages = client
							.send(new TdApi.GetChatHistory(chatId, fromId, offset, numberOfMessages, false))
							.get(1, TimeUnit.MINUTES);

					logger.debug("Got chunk of {} messages", messages.totalCount);

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
			logger.debug("Got total {} messages", messageList.size());

			// Remove duplicates
			messageList.removeIf(a -> messageList.stream().filter(b -> a.id == b.id).count() > 1);
			return messageList;
		}

		private void simulateStatusFor(long seconds, long chatId, ChatAction action) {
			logger.info("Simulating action ({}) for {} seconds", action.getClass().getSimpleName(), seconds);
			
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
			logger.info("Replying to a message {} in chat {}", messageId, chatId);

			var messages = readChatHistory(30, chatId, messageId);
			openChat(chatId, messages.stream().map(m -> m.id).toList());

			var historyContext = buildHistoryContext(messages);
			var message = getMessage(chatId, messageId);

			message.ifPresent(messageToReply -> {
				Utills.getTextFromMessage(messageToReply).or(() -> { // If the message is not a text message try to get
																		// voice transcript or image caption
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
									logger.info("LLM generated reply: {}", llmResponse);
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

			// closeChat(chatId);
		}

		public void newMessage(long chatId) {
			logger.info("Sending new message to chat {}", chatId);

			var messages = readChatHistory(20, chatId, 0L);
			openChat(chatId, messages.stream().map(m -> m.id).toList());
			var historyContext = buildHistoryContext(messages);
			var systemPromt = chatId == botSettings.CURRENT_CHAT ? botSettings.buildSystemPromt(historyContext)
					: botSettings.buildSystemPromtPrivateChat(historyContext);

			llm.llmWriteNewMessage(systemPromt, botSettings.buildPromtForNewMessage())
					.ifPresent(message -> {
						logger.info("LLM generated new message: {}", message);
						simulateStatusFor(Utills.timeToType(message), chatId, new TdApi.ChatActionTyping());

						var request = new SendMessage();

						request.chatId = chatId;
						var messageText = new InputMessageText();
						messageText.text = new FormattedText(message, new TextEntity[0]);
						request.inputMessageContent = messageText;
						client.sendMessage(request, true);
					});

			// closeChat(chatId);
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
						});
					});
			logger.debug("History context: " + stringBuilder.toString());
			return stringBuilder.toString();
		}

		public void replyWithSticker(long chatId, long messageId) {
			try {
				logger.info("Replying to a message {} in chat {} with a sticker", messageId, chatId);

				var message = client.send(new TdApi.GetMessage(chatId, messageId)).get();
				openChat(chatId, Arrays.asList(message.id));

				String selectedEmoji = Utills.getTextFromMessage(message).map(text -> chooseEmoji("", text))
						.orElseGet(() -> chooseRandomEmoji());

				logger.info("Selected emoji: {}", selectedEmoji);

				var stickers = client
						.send(new TdApi.GetStickers(new TdApi.StickerTypeRegular(), selectedEmoji, 100, chatId)).get();
				logger.debug("Got {} stickers corresponding to {}", stickers.stickers.length, selectedEmoji);

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
					logger.warn("No stickers found for emoji {}", selectedEmoji);
				}

			} catch (InterruptedException | ExecutionException e) {
				logger.error("Failed to reply with sticker", e);
			}
		}

		public void reactToMessage(long chatId, long messageId) {
			logger.info("Reacting to a message {} in chat {}", messageId, chatId);
			try {
				var reactions = client.send(new TdApi.GetMessageAvailableReactions(chatId, messageId, 15)).get();
				logger.debug("Got {} top reactions", reactions.topReactions.length);
				var availableReactions = Stream.of(reactions.topReactions)
						.filter(reaction -> reaction.type instanceof TdApi.ReactionTypeEmoji && !reaction.needsPremium)
						.map(reaction -> {
							var emoji = (TdApi.ReactionTypeEmoji) reaction.type;
							return emoji.emoji;
						}).collect(Collectors.toList());

				logger.debug("Available reactions: {}", availableReactions.stream().reduce("", (a, b) -> a + b));

				var message = client.send(new TdApi.GetMessage(chatId, messageId)).get();
				openChat(chatId, Arrays.asList(message.id));

				var selectedEmoji = chooseEmoji("", Utills.getTextFromMessage(message).orElse(" "),
						availableReactions);

				logger.info("Selected emoji: {}", selectedEmoji);

				var request = new TdApi.AddMessageReaction();
				request.chatId = chatId;
				request.messageId = messageId;
				request.reactionType = new TdApi.ReactionTypeEmoji(selectedEmoji);
				client.send(request).get();
			} catch (InterruptedException | ExecutionException e) {
				logger.error("Failed to react to message", e);
			}
		}

		private Optional<Message> getMessage(long chatId, long messageId) {
			try {
				var message = client.send(new TdApi.GetMessage(chatId, messageId)).get();
				return Optional.of(message);
			} catch (InterruptedException | ExecutionException e) {
				logger.error("Failed to get message " + messageId + " in chat " + chatId, e);
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
					logger.error("Failed to get user " + userId, e);
					return Optional.empty();
				}
			});
		}

		private Optional<Chat> getChatInfo(long chatId) {
			try {
				Chat chatInfo = client.send(new TdApi.GetChat(chatId)).get();
				return Optional.of(chatInfo);

			} catch (ExecutionException | InterruptedException e) {
				logger.error("Failed to get chat " + chatId, e);
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
				logger.info("Scheduled an action: " + action);
			} catch (InterruptedException e) {
				logger.error("Failed to schedule an action: " + action, e);
			}
		}

		// Download voice note from remote and use ffmpeg to convert voice note to mp3
		private Optional<byte[]> getVoice(TdApi.MessageVoiceNote message) {
			int fileId = message.voiceNote.voice.id;
			return Optional.ofNullable(message.voiceNote.voice.local.path).filter(p -> !p.isEmpty())
					.or(() -> {
						logger.debug("Downloading voice note: " + fileId);
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
						logger.debug("Downloading video note: " + fileId);
						return downloadFile(fileId).map(file -> file.local.path);
					}).flatMap(path -> {
						return convertToMp3(path, fileId);
					});
		}

		private Optional<File> downloadFile(int fileId) {
			try {
				logger.debug("Downloading file: " + fileId);
				return Optional.ofNullable(client.send(new TdApi.DownloadFile(fileId, 1, 0L, 0L, true))
						.get());
			} catch (InterruptedException | ExecutionException e) {
				logger.error("Failed to download file: " + fileId, e);
				return Optional.empty();
			}
		}

		private Optional<byte[]> convertToMp3(String filePath, int fileId) {
			try {
				logger.debug("Converting voice/video note to mp3: " + filePath);
				var code = Runtime.getRuntime()
						.exec(new String[] { "ffmpeg", "-y", "-i", filePath,
								"./" + botSettings.SESSION_NAME + "/downloads/" + fileId + ".mp3" })
						.waitFor();
				logger.debug("FFmpeg exit code: " + code);

				return Optional.ofNullable(Files.readAllBytes(
						Paths.get("./" + botSettings.SESSION_NAME + "/downloads/" + fileId + ".mp3")));
			} catch (IOException | InterruptedException e) {
				logger.error("Failed to convert voice/video note to mp3: " + fileId, e);
				return Optional.empty();
			}
		}

		private Optional<byte[]> getPhoto(TdApi.MessagePhoto message) {
			logger.debug("Trying to download a photo");
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
							logger.debug("Local path already exists: " + size.photo.local.path);
							return size.photo.local.path;
						}
						try {
							logger.debug("Downloading photo: " + size.photo.id);
							File file = client.send(new TdApi.DownloadFile(size.photo.id, 1, 0L, 0L, true)).get();
							return file.local.path;
						} catch (InterruptedException | ExecutionException e) {
							e.printStackTrace();
							return null;
						}
					}).map(path -> {
						try {
							logger.debug("Photo downloaded: " + path);
							return Files.readAllBytes(Paths.get(path));
						} catch (IOException e) {
							logger.error("Failed to read photo: " + path, e);
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
				logger.error("Can't get name of message user of message reply to:" + message, e);
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
				logger.error("Failed to open chat: " + chatId, e);
			}
		}

		private void closeChat(long chatId) {
			try {
				client.send(new TdApi.CloseChat(chatId)).get(60, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				logger.error("Failed to close chat: " + chatId, e);
			}
		}

		public Cache getCache() {
			return cache;
		}

		public Optional<TdApi.Chat> getChat(long chatId) {
			try {
				return Optional.ofNullable(client.send(new TdApi.GetChat(chatId)).get(60, TimeUnit.SECONDS));
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				e.printStackTrace();
				return Optional.empty();
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

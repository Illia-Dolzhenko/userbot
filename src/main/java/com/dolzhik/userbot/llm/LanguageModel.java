package com.dolzhik.userbot.llm;

import java.util.Optional;

public interface LanguageModel {
    Optional<String> llmWriteNewMessage(String systemPromt,String promt);
    Optional<String> llmReplyToMessage(String systemPromt,String promt);
    Optional<String> llmChooseEmoji(String promt);
}

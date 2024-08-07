package com.dolzhik.userbot.vtt;

import java.util.Optional;

public interface VoiceToText {
    Optional<String> voiceToText(byte[] voice);
}

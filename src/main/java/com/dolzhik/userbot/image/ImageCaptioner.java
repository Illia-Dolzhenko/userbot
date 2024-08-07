package com.dolzhik.userbot.image;

import java.util.Optional;

public interface ImageCaptioner {
    Optional<String> caption(byte[] image);
}

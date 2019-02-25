package com.example.messages;

import java.io.Serializable;

public class AreYouFree implements Serializable {
    private static AreYouFree INSTANCE = new AreYouFree();

    private AreYouFree() {}

    public static AreYouFree getInstance() {
        return INSTANCE;
    }
}

package com.example.messages;

import java.io.Serializable;

public class IAmFree implements Serializable {
    private static IAmFree INSTANCE = new IAmFree();

    private IAmFree() {}

    public static IAmFree getInstance() {
        return INSTANCE;
    }
}

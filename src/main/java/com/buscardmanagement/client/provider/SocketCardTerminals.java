package com.buscardmanagement.client.provider;

import java.util.ArrayList;
import java.util.List;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;

/**
 * SocketCardTerminals - Triển khai CardTerminals interface
 * 
 * Quản lý danh sách card terminals
 */
public class SocketCardTerminals extends CardTerminals {

    private final List<CardTerminal> list;

    /**
     * Constructor
     * 
     * @param host Hostname
     * @param port Port number
     */
    public SocketCardTerminals(String host, int port) {
        list = new ArrayList<>();
        list.add(new SocketCardTerminal(host, port));
    }

    /**
     * Lấy danh sách terminals
     * 
     * @param state State filter (không sử dụng)
     * @return Danh sách terminals
     */
    @Override
    public List<CardTerminal> list(CardTerminals.State state) {
        return list;
    }

    /**
     * Đợi thay đổi trong danh sách terminals
     * 
     * @param timeout Timeout (milliseconds)
     * @return false (không có thay đổi)
     */
    @Override
    public boolean waitForChange(long timeout) {
        return false;
    }
}


package com.buscardmanagement.client.provider;

import java.io.IOException;
import java.net.Socket;
import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

/**
 * SocketCardTerminal - Triển khai CardTerminal interface cho kết nối socket
 * 
 * Đại diện cho một card reader ảo kết nối qua socket
 */
public class SocketCardTerminal extends CardTerminal {

    private final String host;
    private final int port;
    private boolean connected = false;

    /**
     * Constructor
     * 
     * @param host Hostname hoặc IP address
     * @param port Port number
     */
    public SocketCardTerminal(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Lấy tên terminal
     * 
     * @return Terminal name
     */
    @Override
    public String getName() {
        return "SocketCardTerminal[" + host + ":" + port + "]";
    }

    /**
     * Kết nối với card
     * 
     * @param protocol Protocol cần sử dụng (T=0 hoặc T=1)
     * @return Card object
     * @throws CardException Nếu không thể kết nối
     */
    @Override
    public Card connect(String protocol) throws CardException {
        try {
            Socket socket = new Socket(host, port);
            connected = true;
            return new SocketCard(socket);
        } catch (IOException e) {
            throw new CardException("Cannot connect to card simulator at " + 
                host + ":" + port + " - " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra card có present không
     * 
     * @return true nếu đã kết nối
     */
    @Override
    public boolean isCardPresent() {
        return connected;
    }

    /**
     * Đợi card present (luôn trả về true)
     * 
     * @param timeout Timeout (milliseconds)
     * @return true
     */
    @Override
    public boolean waitForCardPresent(long timeout) {
        return true;
    }

    /**
     * Đợi card absent (luôn trả về false)
     * 
     * @param timeout Timeout (milliseconds)
     * @return false
     */
    @Override
    public boolean waitForCardAbsent(long timeout) {
        return false;
    }
}


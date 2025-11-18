package com.buscardmanagement.client.provider;

/**
 * SocketProviderParameter - Tham số cấu hình cho SocketCardProvider
 * 
 * Chứa host và port để kết nối với card simulator
 */
public class SocketProviderParameter {

    public final String host;
    public final int port;

    /**
     * Constructor
     * 
     * @param host Hostname hoặc IP address (thường là "localhost")
     * @param port Port number (thường là 9025)
     */
    public SocketProviderParameter(String host, int port) {
        this.host = host;
        this.port = port;
    }
}


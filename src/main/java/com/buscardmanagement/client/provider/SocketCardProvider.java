package com.buscardmanagement.client.provider;

import java.security.Provider;

/**
 * SocketCardProvider - Security Provider cho kết nối socket với JavaCard simulator
 * 
 * Provider này cho phép kết nối với card simulator qua socket thay vì sử dụng
 * physical card reader.
 */
public class SocketCardProvider extends Provider {

    /**
     * Constructor - Đăng ký provider với Security framework
     */
    public SocketCardProvider() {
        super("SocketCardSim", 1.0, "Socket-based JavaCard Terminal Provider for Bus Card System");
        put("TerminalFactory.SocketCardSim", SocketCardProviderSpi.class.getName());
    }
}


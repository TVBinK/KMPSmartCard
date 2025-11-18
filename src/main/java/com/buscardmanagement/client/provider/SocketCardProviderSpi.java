package com.buscardmanagement.client.provider;

import java.util.ArrayList;
import java.util.List;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactorySpi;

/**
 * SocketCardProviderSpi - Service Provider Interface cho SocketCardProvider
 * 
 * Tạo và quản lý danh sách card terminals
 */
public class SocketCardProviderSpi extends TerminalFactorySpi {

    private final List<CardTerminal> terminals = new ArrayList<>();

    /**
     * Constructor
     * 
     * @param parameter SocketProviderParameter hoặc null (sẽ dùng localhost:9025)
     */
    public SocketCardProviderSpi(Object parameter) {
        if (parameter == null) {
            // Mặc định: localhost:9025
            terminals.add(new SocketCardTerminal("localhost", 9025));
        } else {
            SocketProviderParameter p = (SocketProviderParameter) parameter;
            terminals.add(new SocketCardTerminal(p.host, p.port));
        }
    }

    @Override
    protected CardTerminals engineTerminals() {
        return new CardTerminals() {
            @Override
            public List<CardTerminal> list(CardTerminals.State state) {
                return terminals;
            }

            @Override
            public boolean waitForChange(long timeout) {
                return false;
            }
        };
    }
}


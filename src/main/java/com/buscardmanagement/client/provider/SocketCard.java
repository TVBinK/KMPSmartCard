package com.buscardmanagement.client.provider;

import javax.smartcardio.*;
import java.io.*;
import java.net.Socket;

/**
 * SocketCard - Triển khai Card interface cho kết nối socket
 * 
 * Đại diện cho một smart card được kết nối qua socket
 */
public class SocketCard extends Card {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    /**
     * Constructor
     * 
     * @param socket Socket đã kết nối với card simulator
     * @throws IOException Nếu không thể tạo input/output streams
     */
    public SocketCard(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    /**
     * Lấy ATR (Answer To Reset) - Giá trị cơ bản, không sử dụng trong simulation
     * 
     * @return ATR object
     */
    @Override
    public ATR getATR() {
        return new ATR(new byte[]{0x3B, 0x00}); // Basic ATR
    }

    /**
     * Lấy protocol đang sử dụng
     * 
     * @return Protocol string (T=0 hoặc T=1)
     */
    @Override
    public String getProtocol() {
        return "T=1"; // T=1 protocol (block transmission)
    }

    /**
     * Lấy basic channel để giao tiếp với card
     * 
     * @return CardChannel object
     */
    @Override
    public CardChannel getBasicChannel() {
        return new SocketCardChannel(this);
    }

    /**
     * Ngắt kết nối với card
     * 
     * @param reset Có reset card trước khi disconnect không
     * @throws CardException Nếu không thể đóng socket
     */
    @Override
    public void disconnect(boolean reset) throws CardException {
        try {
            socket.close();
        } catch (IOException e) {
            throw new CardException("Error closing socket", e);
        }
    }

    /**
     * Lấy input stream (dùng bởi SocketCardChannel)
     */
    protected DataInputStream getIn() {
        return in;
    }

    /**
     * Lấy output stream (dùng bởi SocketCardChannel)
     */
    protected DataOutputStream getOut() {
        return out;
    }

    @Override
    public CardChannel openLogicalChannel() throws CardException {
        throw new UnsupportedOperationException("Logical channels not supported");
    }

    @Override
    public void beginExclusive() throws CardException {
        throw new UnsupportedOperationException("Exclusive mode not supported");
    }

    @Override
    public void endExclusive() throws CardException {
        throw new UnsupportedOperationException("Exclusive mode not supported");
    }

    @Override
    public byte[] transmitControlCommand(int controlCode, byte[] command) throws CardException {
        throw new UnsupportedOperationException("Control commands not supported");
    }
}


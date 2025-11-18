package com.buscardmanagement.client.provider;

import javax.smartcardio.*;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * SocketCardChannel - Triển khai CardChannel interface cho kết nối socket
 * 
 * Xử lý việc gửi/nhận APDU commands qua socket
 */
public class SocketCardChannel extends CardChannel {

    private final SocketCard card;

    /**
     * Constructor
     * 
     * @param card SocketCard object
     */
    public SocketCardChannel(SocketCard card) {
        this.card = card;
    }

    /**
     * Lấy Card object
     * 
     * @return Card object
     */
    @Override
    public Card getCard() {
        return card;
    }

    /**
     * Lấy channel number (basic channel = 0)
     * 
     * @return Channel number
     */
    @Override
    public int getChannelNumber() {
        return 0;
    }

    /**
     * Truyền APDU command đến card và nhận response
     * 
     * Protocol (Updated to support large APDU):
     * 1. Gửi: [length (2 bytes, big-endian)][APDU command data]
     * 2. Nhận: [length (2 bytes, big-endian)][Response data]
     * 
     * @param apdu CommandAPDU cần gửi
     * @return ResponseAPDU từ card
     * @throws CardException Nếu có lỗi trong quá trình truyền
     */
    @Override
    public ResponseAPDU transmit(CommandAPDU apdu) throws CardException {
        try {
            // Lấy byte array của APDU command
            byte[] data = apdu.getBytes();
            
            // Gửi: [length (2 bytes)][data]
            card.getOut().writeShort(data.length);  // 2 bytes, big-endian
            card.getOut().write(data);
            card.getOut().flush(); // Đảm bảo dữ liệu được gửi ngay
            
            // Nhận: [length (2 bytes)][response]
            int len = card.getIn().readUnsignedShort();  // 2 bytes, big-endian
            byte[] resp = new byte[len];
            card.getIn().readFully(resp);
            
            return new ResponseAPDU(resp);
        } catch (IOException e) {
            throw new CardException("APDU transmit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Đóng channel (không làm gì vì đây là basic channel)
     */
    @Override
    public void close() {
        // Basic channel không thể đóng riêng
    }

    /**
     * Truyền command sử dụng ByteBuffer (không được hỗ trợ)
     */
    @Override
    public int transmit(ByteBuffer command, ByteBuffer response) throws CardException {
        throw new UnsupportedOperationException("ByteBuffer transmit not supported");
    }
}


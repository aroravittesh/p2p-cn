package Msgs;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static Logging.PeerLogger.writeLog;

public class Msg {
    private String type;
    private int dataLength;
    private byte[] payload;
    private byte[] typeBytes;
    private byte[] lengthBytes;

    public Msg() {}

    public Msg(String messageType) {
        if (isValidMessageType(messageType)) {
            this.type = messageType.trim();
            setMessageLength(Constants.MESSAGE_TYPE);
            this.typeBytes = this.type.getBytes(StandardCharsets.UTF_8);
            this.payload = null;
        } else {
            writeLog("Invalid message type: " + messageType);
        }
    }

    public Msg(String messageType, byte[] payload) {
        this(); 
        this.type = messageType.trim();
        this.typeBytes = this.type.getBytes(StandardCharsets.UTF_8);
        if (payload != null) {
            setMessageLength(payload.length + 1);
            this.payload = payload;
        } else if (isValidMessageType(messageType)) {
            setMessageLength(Constants.MESSAGE_TYPE);
            this.payload = null;
        }
    }

    private boolean isValidMessageType(String messageType) {
        return messageType.equals(Constants.INTERESTED)
            || messageType.equals(Constants.NOT_INTERESTED)
            || messageType.equals(Constants.CHOKE)
            || messageType.equals(Constants.UNCHOKE)
            || messageType.equals(Constants.MESSAGE_DOWNLOADED);
    }

    public String getType() { return type; }
    public byte[] getPayload() { return payload; }
    public byte[] getLengthBytes() { return lengthBytes; }
    public byte[] getTypeBytes() { return typeBytes; }

    public void setMessageLength(int length) {
        this.dataLength = length;
        this.lengthBytes = ByteBuffer.allocate(Integer.BYTES).putInt(length).array();
    }

    public void setMessageLength(byte[] length) {
        int len = ByteBuffer.wrap(length).getInt();
        this.dataLength = len;
        this.lengthBytes = length;
    }

    public void setMessageType(byte[] type) {
        this.type = new String(type, StandardCharsets.UTF_8);
        this.typeBytes = type;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getDataLength() {
        return dataLength;
    }

    public static byte[] serializeMessage(Msg message) throws Exception {
        int code;
        try {
            code = Integer.parseInt(message.getType());
        } catch (NumberFormatException e) {
            writeLog("Invalid message type: " + message.getType());
            throw new Exception("Invalid message Type");
        }
        if (code <= 0 || code > 8) {
            System.out.println("Message Type: " + code);
            throw new Exception("Invalid message Type");
        }

        int total = Constants.MESSAGE_LENGTH
                  + Constants.MESSAGE_TYPE
                  + (message.getPayload() != null ? message.getPayload().length : 0);
        byte[] out = new byte[total];

        System.arraycopy(message.getLengthBytes(), 0, out, 0, Constants.MESSAGE_LENGTH);
        System.arraycopy(message.getTypeBytes(),  0, out, Constants.MESSAGE_LENGTH, Constants.MESSAGE_TYPE);
        if (message.getPayload() != null) {
            System.arraycopy(
                message.getPayload(), 
                0, 
                out, 
                Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE, 
                message.getPayload().length
            );
        }
        return out;
    }

    public static Msg deserializeMessage(byte[] message) {
        Msg msg = new Msg();
        byte[] lenBuf  = new byte[Constants.MESSAGE_LENGTH];
        byte[] typeBuf = new byte[Constants.MESSAGE_TYPE];

        System.arraycopy(message, 0, lenBuf,  0, Constants.MESSAGE_LENGTH);
        System.arraycopy(message, Constants.MESSAGE_LENGTH, typeBuf, 0, Constants.MESSAGE_TYPE);

        msg.setMessageLength(lenBuf);
        msg.setMessageType(typeBuf);

        int length = ByteBuffer.wrap(lenBuf).getInt();
        if (length > 1) {
            byte[] pay = new byte[length - 1];
            System.arraycopy(
                message,
                Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE,
                pay,
                0,
                pay.length
            );
            msg.setPayload(pay);
        }
        return msg;
    }
}

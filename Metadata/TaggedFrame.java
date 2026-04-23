package Metadata;

import Msgs.Msg;

/** One decoded wire frame plus the neighbour id that sourced it. */
public final class TaggedFrame {

    private Msg message;
    private String senderId;

    public TaggedFrame() {
        this.message = new Msg();
        this.senderId = "";
    }

    public Msg getMessage() {
        return message;
    }

    public void setMessage(Msg message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
}

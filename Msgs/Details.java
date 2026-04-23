package Msgs;

/**
 * Legacy DTO kept for parity with older checkpoints; live traffic uses {@code TaggedFrame}.
 */
public final class Details {

    private Msg payload;
    private String originPeer;

    public Details() {
        this.payload = new Msg();
        this.originPeer = null;
    }

    public Msg getMessage() {
        return payload;
    }

    public void setMessage(Msg message) {
        this.payload = message;
    }

    public String getFromPeerID() {
        return originPeer;
    }

    public void setFromPeerID(String fromPeerID) {
        this.originPeer = fromPeerID;
    }
}

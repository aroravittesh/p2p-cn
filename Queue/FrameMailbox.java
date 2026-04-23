package Queue;

import Metadata.TaggedFrame;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class FrameMailbox {

    private static final ConcurrentLinkedQueue<TaggedFrame> inbox = new ConcurrentLinkedQueue<>();

    public static void enqueue(TaggedFrame message) {
        inbox.offer(message);
    }

    public static TaggedFrame dequeue() {
        return inbox.poll();
    }

    public static boolean hasPending() {
        return !inbox.isEmpty();
    }

    private FrameMailbox() {}
}

package Logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** Renders one line for the console mirror of {@link PeerLogger}. */
public final class PeerLogLayout extends Formatter {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        return decorate(record.getMessage());
    }

    public static String decorate(String raw) {
        String when = CLOCK.format(LocalDateTime.now());
        String who = System.getProperty("peer.id");
        return String.format("%s - Peer %s: %s%n", when, who, raw);
    }
}

package online.manubook.sms;

import java.util.ArrayList;
import java.util.List;

/**
 * The periods the SMS permission was off, for the status's {@code smsOff} (contract
 * §1). Android sends no event when the permission is revoked, so the app looks at
 * every beat and every time the screen opens: a period starts at the last time the
 * app saw the permission granted and ends the first time it sees it granted again.
 * A period stays in the status until a beat that carried it closed answers 204.
 * <p>
 * Pure logic over a small text form ({@code from:to,from:to}, epoch millis, to 0
 * while still off), so it runs in JVM tests.
 */
public final class SmsOffPeriods {

    static final int MAX = 5;

    /** One period; {@code to} is 0 while the permission is still off. */
    public static final class Period {
        public final long from;
        public final long to;

        Period(long from, long to) {
            this.from = from;
            this.to = to;
        }

        boolean isOpen() {
            return to == 0;
        }
    }

    private final List<Period> periods;
    private long lastSeenGrantedAt;

    private SmsOffPeriods(List<Period> periods, long lastSeenGrantedAt) {
        this.periods = periods;
        this.lastSeenGrantedAt = lastSeenGrantedAt;
    }

    public static SmsOffPeriods parse(String stored, long lastSeenGrantedAt) {
        List<Period> periods = new ArrayList<>();
        if (stored != null && !stored.isEmpty()) {
            for (String item : stored.split(",")) {
                String[] parts = item.split(":");
                try {
                    periods.add(new Period(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
                } catch (RuntimeException e) {
                    // A damaged entry is dropped; the next look starts again.
                }
            }
        }
        return new SmsOffPeriods(periods, lastSeenGrantedAt);
    }

    public String format() {
        StringBuilder text = new StringBuilder();
        for (Period p : periods) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(p.from).append(':').append(p.to);
        }
        return text.toString();
    }

    public long getLastSeenGrantedAt() {
        return lastSeenGrantedAt;
    }

    public List<Period> list() {
        return new ArrayList<>(periods);
    }

    /**
     * Records one look at the permission. Before the app has ever seen it granted
     * there is no start time, so no period opens; the status's {@code sms:false}
     * says it is off.
     */
    public void observe(boolean granted, long now) {
        Period open = open();
        if (granted) {
            if (open != null) {
                periods.set(periods.indexOf(open), new Period(open.from, Math.max(now, open.from)));
            }
            lastSeenGrantedAt = now;
        } else if (open == null && lastSeenGrantedAt > 0) {
            periods.add(new Period(lastSeenGrantedAt, 0));
            // Keep the newest; an open period is always the newest.
            while (periods.size() > MAX) {
                periods.remove(0);
            }
        }
    }

    /** After a beat that carried {@code sent} answered 204: drops the closed periods it carried. */
    public void acknowledge(List<Period> sent) {
        for (Period s : sent) {
            if (s.isOpen()) {
                continue;
            }
            for (int i = periods.size() - 1; i >= 0; i--) {
                Period p = periods.get(i);
                if (p.from == s.from && p.to == s.to) {
                    periods.remove(i);
                }
            }
        }
    }

    private Period open() {
        for (Period p : periods) {
            if (p.isOpen()) {
                return p;
            }
        }
        return null;
    }
}

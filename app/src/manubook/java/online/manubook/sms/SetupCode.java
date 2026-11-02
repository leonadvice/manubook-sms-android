package online.manubook.sms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The combined setup code from Manubook's one-time key dialog: exactly three lines
 * joined by "\n", with no trailing line feed (contract §3):
 * <pre>
 * https://api.manubook.online/api/sms
 * Bearer &lt;43 base64url&gt;
 * https://api.manubook.online/api/sms/beat/&lt;43 base64url&gt;
 * </pre>
 * Anything else is refused. The release build accepts only the production API host;
 * a debug build also accepts another HTTPS host, the same on both address lines.
 */
public final class SetupCode {

    private static final String SECRET = "[A-Za-z0-9_-]{43}";
    private static final Pattern SMS_LINE = Pattern.compile("https://([a-z0-9.-]+)/api/sms");
    private static final Pattern KEY_LINE = Pattern.compile("Bearer (" + SECRET + ")");
    private static final Pattern BEAT_LINE = Pattern.compile("https://([a-z0-9.-]+)/api/sms/beat/" + SECRET);

    public final String smsUrl;
    public final String authorization;
    public final String beatUrl;

    private SetupCode(String smsUrl, String authorization, String beatUrl) {
        this.smsUrl = smsUrl;
        this.authorization = authorization;
        this.beatUrl = beatUrl;
    }

    /** The batch route every SMS is posted to, as a one-item batch (contract §2). */
    public String batchUrl() {
        return smsUrl + "/batch";
    }

    /** The key's last four characters, the only part of it the app ever shows. */
    public String keyTail() {
        return authorization.substring(authorization.length() - 4);
    }

    /**
     * @param releaseHost the only host accepted when {@code anyHttpsHost} is false
     * @return the parsed code, or null when the text is not exactly a setup code
     */
    public static SetupCode parse(String text, String releaseHost, boolean anyHttpsHost) {
        if (text == null) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        if (lines.length != 3) {
            return null;
        }
        Matcher sms = SMS_LINE.matcher(lines[0]);
        Matcher key = KEY_LINE.matcher(lines[1]);
        Matcher beat = BEAT_LINE.matcher(lines[2]);
        if (!sms.matches() || !key.matches() || !beat.matches()) {
            return null;
        }
        String host = sms.group(1);
        if (!host.equals(beat.group(1))) {
            return null;
        }
        if (!anyHttpsHost && !host.equals(releaseHost)) {
            return null;
        }
        return new SetupCode(lines[0], lines[1], lines[2]);
    }
}

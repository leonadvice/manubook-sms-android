package online.manubook.sms;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * *Gửi thử* (android review F13): posts a test text as a one-item batch with the
 * saved key, the same route every SMS takes. Manubook keeps a text that says
 * MANUBOOK TEST and that no bank format reads as the phone's *Tin thử*. The time in
 * the text makes each test new, so Manubook's "last received" moves every time
 * instead of answering duplicate.
 */
public final class TestMessage {

    private static final String TAG = "ManubookTest";
    private static final int TIMEOUT_MILLIS = 10000;

    private TestMessage() {
    }

    /** The test text: {@code MANUBOOK TEST <HH:mm:ss> TK <account> So du}. */
    static String text(long now, String account) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(now));
        return "MANUBOOK TEST " + time + " TK " + account + " So du";
    }

    static String body(String text, long now) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("text", text);
        item.put("receivedAt", BeatStatus.instant(now));
        return new JSONObject().put("messages", new JSONArray().put(item)).toString();
    }

    /**
     * Sends one test; call off the main thread.
     *
     * @return the HTTP status, or -1 when no answer arrived
     */
    static int send(String batchUrl, String authorization, String account) {
        long now = System.currentTimeMillis();
        int code = -1;
        HttpURLConnection connection = null;
        try {
            byte[] body = body(text(now, account), now).getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(batchUrl).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", authorization);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            code = connection.getResponseCode();
        } catch (IOException | JSONException e) {
            // Class name only: messages can quote the address.
            Log.w(TAG, "test failed: " + e.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        Log.i(TAG, "test answered " + code);
        return code;
    }
}

package tech.bogomolov.incomingsmsgateway;

/** Test access to upstream's package-private text filter, so tests run the real match. */
public final class ManubookFilterBridge {

    private ManubookFilterBridge() {
    }

    public static boolean matches(String filter, String content) {
        return SmsBroadcastReceiver.matchesFilter(filter, content);
    }
}

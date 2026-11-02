package online.manubook.sms;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the one text filter for all banks from the account numbers typed into the
 * app (bank-sms-import design 25, corrected 2026-11-02): any sender, and the SMS
 * must show a balance change on one of the accounts,
 * {@code (?s)TK (<n1>|<n2>)\b.*(So du|SD:)}. Input is digits only, so the regex is
 * always valid: upstream forwards every SMS when a filter fails to compile.
 */
public final class AccountFilter {

    private static final Pattern ACCOUNT = Pattern.compile("[0-9]{6,20}");

    private AccountFilter() {
    }

    /**
     * Trims each entry, drops empty ones and repeats, keeps the typed order.
     *
     * @return the account numbers, or null when any entry is not 6–20 digits
     */
    public static List<String> normalize(List<String> entries) {
        Set<String> accounts = new LinkedHashSet<>();
        for (String entry : entries) {
            String account = entry == null ? "" : entry.trim();
            if (account.isEmpty()) {
                continue;
            }
            if (!ACCOUNT.matcher(account).matches()) {
                return null;
            }
            accounts.add(account);
        }
        return new ArrayList<>(accounts);
    }

    /** @param accounts at least one normalized account number */
    public static String regex(List<String> accounts) {
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("no account");
        }
        return "(?s)TK (" + String.join("|", accounts) + ")\\b.*(So du|SD:)";
    }
}

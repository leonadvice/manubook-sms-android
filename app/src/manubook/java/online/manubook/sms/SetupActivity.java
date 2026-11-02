package online.manubook.sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.IntentCompat;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tech.bogomolov.incomingsmsgateway.BuildConfig;
import tech.bogomolov.incomingsmsgateway.DeliveryResult;
import tech.bogomolov.incomingsmsgateway.FailedMessage;
import tech.bogomolov.incomingsmsgateway.ForwardingConfig;
import tech.bogomolov.incomingsmsgateway.R;

/**
 * The app's one screen (S21+ probe; reconcile agreement B2): the status on top,
 * then the setup checklist the person setting up the phone works through. Each row
 * has a tick and *Sửa*: the setup code, the account numbers, the SMS permission,
 * notifications, battery *Không hạn chế* and *Xóa quyền nếu không dùng ứng dụng*
 * off, then *Gửi thử*.
 * <p>
 * Beat order: saving asks for the permissions first and beats only after the
 * answer, and every return to this screen beats when a setting changed since the
 * last beat, so Manubook sees each fix at once and never a setting the person was
 * about to allow.
 */
public class SetupActivity extends Activity {

    private static final int REQUEST_SETUP_PERMISSIONS = 1;
    private static final int REQUEST_SMS = 2;
    private static final int REQUEST_NOTIFY = 3;

    /** One checklist row: tick, label, detail and its Sửa button. */
    private static final class Row {
        final TextView mark;
        final TextView label;
        final TextView detail;
        final Button fix;

        Row(View view) {
            mark = view.findViewById(R.id.manubook_check_mark);
            label = view.findViewById(R.id.manubook_check_label);
            detail = view.findViewById(R.id.manubook_check_detail);
            fix = view.findViewById(R.id.manubook_check_fix);
        }
    }

    // One thread reads the settings; another sends Gửi thử and Gửi lại, so a slow
    // network never holds up the rows or the beat after a fix.
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private SetupCode scannedCode;
    private LinearLayout accountRows;
    private View editor;
    private TextView codeState;
    private TextView message;
    private Row codeRow;
    private Row accountsRow;
    private Row smsRow;
    private Row notifyRow;
    private Row batteryRow;
    private Row unusedRow;
    private Row testRow;
    // Between Lưu and the permission answer no beat is sent.
    private boolean awaitingPermissions;
    private boolean testing;
    // Until the first read, every row shows not done and the status makes no claim
    // about the settings.
    private PhoneChecks checks = unread();
    private boolean checksRead;
    private Integer pending;
    private int failed;

    // The stores a beat, an SMS delivery or a failure writes to: the screen follows them.
    private static final String[] WATCHED = {"manubook", "delivery_result", "failed_messages"};
    private final Runnable refreshSoon = this::refresh;
    // Held in a field: SharedPreferences keeps its listeners only weakly.
    private final SharedPreferences.OnSharedPreferenceChangeListener onStoreChange = (prefs, key) -> {
        View root = findViewById(R.id.manubook_root);
        root.removeCallbacks(refreshSoon);
        root.postDelayed(refreshSoon, 300);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The key's screens never appear in screenshots or the recent-apps list.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.manubook_activity_setup);

        View root = findViewById(R.id.manubook_root);
        // Target SDK 35 draws edge to edge: keep content clear of the system bars.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        accountRows = findViewById(R.id.manubook_accounts);
        editor = findViewById(R.id.manubook_editor);
        codeState = findViewById(R.id.manubook_code_state);
        message = findViewById(R.id.manubook_message);

        codeRow = row(R.id.manubook_row_code, R.string.manubook_row_code, v -> showEditor());
        accountsRow = row(R.id.manubook_row_accounts, R.string.manubook_row_accounts, v -> showEditor());
        smsRow = row(R.id.manubook_row_sms, R.string.manubook_row_sms, v -> fixSms());
        notifyRow = row(R.id.manubook_row_notify, R.string.manubook_row_notify, v -> fixNotify());
        batteryRow = row(R.id.manubook_row_battery, R.string.manubook_row_battery, v -> fixBattery());
        unusedRow = row(R.id.manubook_row_unused, R.string.manubook_row_unused, v -> fixUnused());
        testRow = row(R.id.manubook_row_test, R.string.manubook_row_test, v -> sendTest());
        testRow.fix.setText(R.string.manubook_send_test);

        findViewById(R.id.manubook_scan).setOnClickListener(v -> scan());
        findViewById(R.id.manubook_paste).setOnClickListener(v -> paste());
        findViewById(R.id.manubook_add_account).setOnClickListener(v -> addAccountRow(""));
        findViewById(R.id.manubook_save).setOnClickListener(v -> save());
        findViewById(R.id.manubook_licences).setOnClickListener(v -> showLicences());
        findViewById(R.id.manubook_resend).setOnClickListener(v -> resend());

        List<String> accounts = new ManubookSettings(this).getAccounts();
        if (accounts.isEmpty()) {
            addAccountRow("");
        }
        for (String account : accounts) {
            addAccountRow(account);
        }
        editor.setVisibility(new ManubookSettings(this).isSetUp() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (String store : WATCHED) {
            getSharedPreferences(store, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(onStoreChange);
        }
        refresh();
    }

    @Override
    protected void onPause() {
        for (String store : WATCHED) {
            getSharedPreferences(store, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(onStoreChange);
        }
        findViewById(R.id.manubook_root).removeCallbacks(refreshSoon);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        background.shutdown();
        network.shutdown();
        super.onDestroy();
    }

    private static PhoneChecks unread() {
        PhoneChecks c = new PhoneChecks();
        c.sms = false;
        c.notify = false;
        c.battery = false;
        c.unusedExempt = false;
        return c;
    }

    private Row row(int containerId, int labelId, View.OnClickListener fix) {
        ViewGroup container = findViewById(containerId);
        View view = getLayoutInflater().inflate(R.layout.manubook_check_row, container, false);
        container.addView(view);
        Row row = new Row(view);
        row.label.setText(labelId);
        row.fix.setOnClickListener(fix);
        return row;
    }

    // ---- Setup code and accounts ----

    private void showEditor() {
        editor.setVisibility(View.VISIBLE);
        showState();
    }

    private void scan() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener(barcode -> useCode(barcode.getRawValue()))
                .addOnFailureListener(e -> showMessage(getString(R.string.manubook_scan_failed)));
    }

    private void paste() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) {
            showMessage(getString(R.string.manubook_code_wrong));
            return;
        }
        String text = clip.getItemAt(0).getText().toString().trim();
        // The code holds the key: do not leave it on the clipboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip();
        }
        useCode(text);
    }

    private void useCode(String text) {
        SetupCode code = SetupCode.parse(text, BuildConfig.MANUBOOK_API_HOST, BuildConfig.DEBUG);
        if (code == null) {
            showMessage(getString(R.string.manubook_code_wrong));
            return;
        }
        scannedCode = code;
        showMessage(null);
        showState();
    }

    private void addAccountRow(String value) {
        EditText row = new EditText(this);
        row.setInputType(InputType.TYPE_CLASS_NUMBER);
        row.setHint(R.string.manubook_account_hint);
        row.setText(value);
        accountRows.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void save() {
        SetupCode code = scannedCode != null ? scannedCode : currentCode();
        if (code == null) {
            showMessage(getString(R.string.manubook_need_code));
            return;
        }
        List<String> typed = new ArrayList<>();
        for (int i = 0; i < accountRows.getChildCount(); i++) {
            typed.add(((EditText) accountRows.getChildAt(i)).getText().toString());
        }
        List<String> accounts = AccountFilter.normalize(typed);
        if (accounts == null) {
            showMessage(getString(R.string.manubook_account_wrong));
            return;
        }
        if (accounts.isEmpty()) {
            showMessage(getString(R.string.manubook_need_account));
            return;
        }
        ManubookSetup.apply(this, code, accounts);
        scannedCode = null;
        editor.setVisibility(View.GONE);
        showMessage(getString(R.string.manubook_saved));
        showState();
        // Ask first; the beat follows the answer (onRequestPermissionsResult).
        awaitingPermissions = true;
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        requestPermissions(wanted.toArray(new String[0]), REQUEST_SETUP_PERMISSIONS);
    }

    // The saved code, rebuilt from the stored rule, so the accounts can change
    // without a new scan.
    private SetupCode currentCode() {
        ManubookSettings settings = new ManubookSettings(this);
        if (!settings.isSetUp()) {
            return null;
        }
        for (ForwardingConfig rule : ForwardingConfig.getAll(this)) {
            try {
                String authorization = new JSONObject(rule.getHeaders()).getString("Authorization");
                String smsUrl = rule.getUrl().substring(0, rule.getUrl().length() - "/batch".length());
                return SetupCode.parse(smsUrl + "\n" + authorization + "\n" + settings.getBeatUrl(),
                        BuildConfig.MANUBOOK_API_HOST, BuildConfig.DEBUG);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // ---- Settings rows ----

    private void fixSms() {
        requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_SMS);
    }

    private void fixNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFY);
            return;
        }
        // Allowed but switched off in Settings: only Settings can turn it back on.
        openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()), R.string.manubook_row_notify_path);
    }

    // Outside Play: the Play policy on this permission does not apply (android review F12).
    @SuppressLint("BatteryLife")
    private void fixBattery() {
        if (!Boolean.TRUE.equals(checks.bgRestricted)) {
            // One system dialog puts the app on the battery exemption list, which is
            // *Không hạn chế* on Samsung (S21+ probe 8.1).
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.fromParts("package", getPackageName(), null)));
                return;
            } catch (ActivityNotFoundException e) {
                // Some phones have no such dialog: fall through to App info.
            }
        }
        openAppInfo(R.string.manubook_row_battery_path);
    }

    private void fixUnused() {
        try {
            startActivity(IntentCompat.createManageUnusedAppRestrictionsIntent(this, getPackageName()));
            showMessage(getString(R.string.manubook_open_settings, getString(R.string.manubook_row_unused_path)));
        } catch (ActivityNotFoundException | UnsupportedOperationException e) {
            openAppInfo(R.string.manubook_row_unused_path);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_SETUP_PERMISSIONS) {
            awaitingPermissions = false;
            // Always beat after saving: a beat still running with the old key may
            // have written its settings back after the save cleared them.
            Beat.now(this);
        } else if (permissions.length > 0 && results.length > 0
                && results[0] != PackageManager.PERMISSION_GRANTED
                && !shouldShowRequestPermissionRationale(permissions[0])) {
            // Refused for good (or blocked without a dialog): only Settings can allow it now.
            if (requestCode == REQUEST_SMS) {
                openAppInfo(R.string.manubook_row_sms_path);
            } else if (requestCode == REQUEST_NOTIFY) {
                openAppInfo(R.string.manubook_row_notify_path);
            }
        }
        refresh();
    }

    private void openAppInfo(int pathId) {
        openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)), pathId);
    }

    private void openSettings(Intent intent, int pathId) {
        showMessage(getString(R.string.manubook_open_settings, getString(pathId)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // The message still says where to go.
        }
    }

    // ---- Gửi thử ----

    private void sendTest() {
        SetupCode code = currentCode();
        List<String> accounts = new ManubookSettings(this).getAccounts();
        if (code == null || accounts.isEmpty()) {
            showMessage(getString(R.string.manubook_need_setup));
            return;
        }
        testing = true;
        showState();
        network.execute(() -> {
            int answer = TestMessage.send(code.batchUrl(), code.authorization, accounts.get(0));
            new ManubookSettings(this).recordTest(System.currentTimeMillis(), answer, code.keyTail());
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                testing = false;
                showState();
            });
        });
    }

    // ---- Gửi lại ----

    private void resend() {
        network.execute(() -> {
            int queued = FailedResend.run(this, true);
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                showMessage(getString(R.string.manubook_resent, queued));
                refresh();
            });
        });
    }

    // ---- Showing the state ----

    /**
     * Reads the settings off the main thread, shows them, the settings notice, and
     * beats when a setting changed since the last beat.
     */
    private void refresh() {
        showState();
        background.execute(() -> {
            PhoneChecks read = PhoneChecks.read(this);
            Integer queued = Beat.pendingDeliveries(this);
            int stored = FailedMessage.getCount(this);
            ManubookSettings settings = new ManubookSettings(this);
            if (settings.isSetUp()) {
                settings.observeSms(Boolean.TRUE.equals(read.sms), System.currentTimeMillis());
            }
            SettingsNotice.update(this, read);
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                checks = read;
                checksRead = true;
                pending = queued;
                failed = stored;
                if (settings.isSetUp() && !awaitingPermissions
                        && !read.signature().equals(settings.getLastBeatChecks())) {
                    Beat.now(this);
                }
                showState();
            });
        });
    }

    private void showState() {
        ManubookSettings settings = new ManubookSettings(this);
        boolean setUp = settings.isSetUp();
        TextView status = findViewById(R.id.manubook_status);
        if (!setUp) {
            status.setText(R.string.manubook_status_not_set_up);
        } else if (keyRevoked(settings)) {
            status.setText(R.string.manubook_key_revoked);
        } else if (!checksRead) {
            status.setText(null);
        } else if (Boolean.FALSE.equals(checks.sms)) {
            status.setText(R.string.manubook_status_no_sms);
        } else if (!checks.allOk()) {
            status.setText(R.string.manubook_status_fix);
        } else {
            status.setText(R.string.manubook_status_running);
        }

        if (scannedCode != null) {
            codeState.setText(getString(R.string.manubook_code_scanned, scannedCode.keyTail()));
        } else if (setUp) {
            codeState.setText(getString(R.string.manubook_code_saved, settings.getKeyTail()));
        } else {
            codeState.setText(R.string.manubook_code_none);
        }
        boolean editing = editor.getVisibility() == View.VISIBLE;
        show(codeRow, setUp, setUp ? getString(R.string.manubook_code_saved, settings.getKeyTail())
                : getString(R.string.manubook_code_none), setUp && !editing);
        List<String> accounts = settings.getAccounts();
        show(accountsRow, setUp && !accounts.isEmpty(), accounts.isEmpty()
                ? getString(R.string.manubook_accounts_none) : String.join(", ", accounts), setUp && !editing);
        // A setting row says where to fix it only while it is wrong; one the phone
        // could not read counts as right, as in PhoneChecks.allOk().
        boolean sms = !Boolean.FALSE.equals(checks.sms);
        show(smsRow, sms, sms ? null : getString(R.string.manubook_row_sms_path), !sms);
        boolean notify = !Boolean.FALSE.equals(checks.notify);
        show(notifyRow, notify, notify ? null : getString(R.string.manubook_row_notify_path), !notify);
        boolean battery = checks.batteryOk();
        show(batteryRow, battery, battery ? null : getString(R.string.manubook_row_battery_path), !battery);
        boolean unused = !Boolean.FALSE.equals(checks.unusedExempt);
        show(unusedRow, unused, unused ? null : getString(R.string.manubook_row_unused_path), !unused);
        show(testRow, setUp && settings.testPassed(), testResult(settings), setUp && !testing);

        ((TextView) findViewById(R.id.manubook_last)).setText(lastResults(settings));
        findViewById(R.id.manubook_failed).setVisibility(setUp && failed > 0 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.manubook_failed_count)).setText(getString(R.string.manubook_failed, failed));
    }

    // ok: the tick; detail: the second line, null for none; canFix: whether the button shows.
    private void show(Row row, boolean ok, String detail, boolean canFix) {
        row.mark.setText(ok ? "✓" : "✗");
        row.mark.setTextColor(getColor(ok ? R.color.manubook_ok : R.color.manubook_missing));
        row.detail.setText(detail);
        row.detail.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        row.fix.setVisibility(canFix ? View.VISIBLE : View.GONE);
    }

    private String testResult(ManubookSettings settings) {
        if (testing) {
            return getString(R.string.manubook_test_sending);
        }
        if (settings.getTestAt() == 0) {
            return getString(R.string.manubook_test_none);
        }
        String at = time(settings.getTestAt());
        int code = settings.getTestCode();
        if (code == 202) {
            return getString(R.string.manubook_test_ok, at);
        }
        if (code == 401) {
            return getString(R.string.manubook_key_revoked);
        }
        if (code < 0) {
            return getString(R.string.manubook_test_offline, at);
        }
        return getString(R.string.manubook_test_error, at, code);
    }

    // A 401 on the latest beat, or on an SMS sent after the latest accepted beat.
    private boolean keyRevoked(ManubookSettings settings) {
        if (settings.getLastBeatCode() == 401) {
            return true;
        }
        DeliveryResult delivery = DeliveryResult.load(this);
        return delivery.getLastCode() == 401 && delivery.getLastAt() > settings.getLastAckedBeatAt();
    }

    private String lastResults(ManubookSettings settings) {
        StringBuilder text = new StringBuilder();
        DeliveryResult delivery = DeliveryResult.load(this);
        if (delivery.getLastAt() > 0) {
            text.append(getString(R.string.manubook_last_sms, time(delivery.getLastAt()),
                    answer(delivery.getLastCode())));
        } else {
            text.append(getString(R.string.manubook_last_sms_none));
        }
        text.append('\n');
        if (settings.getLastBeatAt() > 0) {
            text.append(getString(R.string.manubook_last_beat, time(settings.getLastBeatAt()),
                    answer(settings.getLastBeatCode())));
        } else {
            text.append(getString(R.string.manubook_last_beat_none));
        }
        if (pending != null && pending > 0) {
            text.append('\n').append(getString(R.string.manubook_pending, pending));
        }
        text.append('\n').append(getString(R.string.manubook_version, BuildConfig.VERSION_NAME));
        return text.toString();
    }

    private String answer(int code) {
        if (code >= 200 && code < 300) {
            return getString(R.string.manubook_answer_ok);
        }
        return code < 0 ? getString(R.string.manubook_answer_none) : getString(R.string.manubook_answer_code, code);
    }

    private static String time(long epochMillis) {
        return new SimpleDateFormat("HH:mm dd/MM", Locale.US).format(new Date(epochMillis));
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void showLicences() {
        String text;
        try (InputStream in = getResources().openRawResource(R.raw.manubook_licences)) {
            byte[] bytes = new byte[in.available()];
            int read = in.read(bytes);
            text = new String(bytes, 0, Math.max(read, 0), StandardCharsets.UTF_8);
        } catch (IOException e) {
            text = "";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.manubook_licences)
                .setMessage(text)
                .setPositiveButton(R.string.manubook_close, null)
                .show();
    }
}

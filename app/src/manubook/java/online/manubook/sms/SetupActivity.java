package online.manubook.sms;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import tech.bogomolov.incomingsmsgateway.BuildConfig;
import tech.bogomolov.incomingsmsgateway.DeliveryResult;
import tech.bogomolov.incomingsmsgateway.ForwardingConfig;
import tech.bogomolov.incomingsmsgateway.R;

/**
 * The minimal setup screen (W6a): scan or paste the setup code, type the account
 * numbers, save. Saving writes the forwarding rule, asks for the SMS and
 * notification permissions and starts forwarding and the beat. W6b replaces it
 * with the setup checklist and status screen.
 */
public class SetupActivity extends Activity {

    private static final int REQUEST_PERMISSIONS = 1;

    private SetupCode scannedCode;
    private LinearLayout accountRows;
    private TextView codeState;
    private TextView message;
    private View restrictedHelp;

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
        codeState = findViewById(R.id.manubook_code_state);
        message = findViewById(R.id.manubook_message);
        restrictedHelp = findViewById(R.id.manubook_restricted_help);

        findViewById(R.id.manubook_scan).setOnClickListener(v -> scan());
        findViewById(R.id.manubook_paste).setOnClickListener(v -> paste());
        findViewById(R.id.manubook_add_account).setOnClickListener(v -> addAccountRow(""));
        findViewById(R.id.manubook_save).setOnClickListener(v -> save());
        findViewById(R.id.manubook_app_info).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.manubook_licences).setOnClickListener(v -> showLicences());

        List<String> accounts = new ManubookSettings(this).getAccounts();
        if (accounts.isEmpty()) {
            addAccountRow("");
        }
        for (String account : accounts) {
            addAccountRow(account);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        showMessage(getString(R.string.manubook_saved));
        requestPermissions();
        showState();
    }

    // The saved code, rebuilt from the stored rule, so the accounts can change
    // without a new scan.
    private SetupCode currentCode() {
        ManubookSettings settings = new ManubookSettings(this);
        if (!settings.isSetUp()) {
            return null;
        }
        for (ForwardingConfig rule : ForwardingConfig.getAll(this)) {
            if (!ManubookSetup.RULE_KEY.equals(rule.getKey()) || !rule.getUrl().endsWith("/batch")) {
                continue;
            }
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

    private void requestPermissions() {
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        requestPermissions(wanted.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        showState();
    }

    private boolean smsAllowed() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void showState() {
        ManubookSettings settings = new ManubookSettings(this);
        TextView status = findViewById(R.id.manubook_status);
        if (!settings.isSetUp()) {
            status.setText(R.string.manubook_status_not_set_up);
        } else if (!smsAllowed()) {
            status.setText(R.string.manubook_status_no_sms);
        } else {
            status.setText(R.string.manubook_status_running);
        }
        restrictedHelp.setVisibility(settings.isSetUp() && !smsAllowed() ? View.VISIBLE : View.GONE);

        if (scannedCode != null) {
            codeState.setText(getString(R.string.manubook_code_scanned, scannedCode.keyTail()));
        } else if (settings.isSetUp()) {
            codeState.setText(getString(R.string.manubook_code_saved, settings.getKeyTail()));
        } else {
            codeState.setText(R.string.manubook_code_none);
        }

        TextView last = findViewById(R.id.manubook_last);
        last.setText(lastResults(settings));
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
        if (delivery.getLastCode() == 401 || settings.getLastBeatCode() == 401) {
            text.append('\n').append(getString(R.string.manubook_key_revoked));
        }
        text.append('\n').append(getString(R.string.manubook_version, BuildConfig.VERSION_NAME));
        return text.toString();
    }

    private String answer(int code) {
        if (code >= 200 && code < 300) {
            return getString(R.string.manubook_answer_ok);
        }
        return code < 0 ? getString(R.string.manubook_answer_none) : String.valueOf(code);
    }

    private static String time(long epochMillis) {
        return new SimpleDateFormat("HH:mm dd/MM", Locale.US).format(new Date(epochMillis));
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void openAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
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

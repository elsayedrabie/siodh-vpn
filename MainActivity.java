package com.siodh.vpn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * SIODH VPN - main dashboard (SSH tunnel edition, built-in servers/configs).
 * Layout file: res/layout/main.xml, loaded with setContentView(R.layout.main).
 * No lambdas, no AndroidX. Needs the JSch library (see setup notes).
 */
public class MainActivity extends Activity {

    private static final int REQ_VPN = 4101;
    private static final int MAX_LOG_LINES = 16;

    // ---------- Palette ----------
    private static final int C_BG_TOP = 0xFF04050D;
    private static final int C_BG_BOTTOM = 0xFF140B30;
    private static final int C_CARD = 0xFF10132B;
    private static final int C_CARD_STROKE = 0xFF2B2F63;
    private static final int C_VIOLET = 0xFF7C4DFF;
    private static final int C_PURPLE = 0xFFB44DFF;
    private static final int C_CYAN = 0xFF22D3EE;
    private static final int C_BLUE = 0xFF3D7BFF;
    private static final int C_RED = 0xFFFF4D8D;
    private static final int C_GREEN = 0xFF2CF5B0;
    private static final int C_TEXT = 0xFFFFFFFF;

    // =====================================================================
    //  Built-in configs ("SELECT SERVICE"). A config with a non-empty header
    //  host makes the app send that Host header on connect, to try to get
    //  past firewalls that only look at the Host header on ports 80/443.
    //
    //  >>> ADD YOUR OWN CONFIGS BELOW THIS LINE <<<
    // =====================================================================
    private static final class ConfigDef {
        final String name;
        final String headerHost;

        ConfigDef(String name, String headerHost) {
            this.name = name;
            this.headerHost = headerHost;
        }
    }

    // =====================================================================
    //  اضافة كونفج جديد (خانة SELECT SERVICE):
    //  انسخ الاربع سطور اللي تحت (من new ConfigDef لحد "),") والصقهم
    //  فوق السطر المكتوب فيه "نهاية الكونفجات"، وغيّر الاسم والهيدر هوست.
    //
    //      new ConfigDef(
    //              "اكتب هنا اسم الكونفج",      // اسم الكونفج (بيظهر في البرنامج)
    //              "اكتب هنا الهيدر هوست"),     // الهيدر هوست (سيبها فاضية "" لو مش عايز)
    //
    //  مهم: لازم كل سطر ينتهي بفاصلة "," والنص يكون بين علامتين تنصيص " "
    // =====================================================================
    private static final ConfigDef[] CONFIGS = new ConfigDef[]{
            new ConfigDef(
                    "SIODH Default",     // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)
            new ConfigDef(
                    "SSL Tunnel",        // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)
            new ConfigDef(
                    "V2RAY",             // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)
            new ConfigDef(
                    "VMESS",             // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)
            new ConfigDef(
                    "VLESS",             // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)
            new ConfigDef(
                    "UDP Tunnel",        // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)
            new ConfigDef(
                    "Custom Config",     // اسم الكونفج
                    ""),                 // الهيدر هوست (فاضي)

            // ---- الصق الكونفجات الجديدة هنا (فوق نهاية الكونفجات) ----

            new ConfigDef(
                    "PlayStation Bypass 1\uD83C\uDFAE\u2705",   // اسم الكونفج
                    "ea.com"),                                    // الهيدر هوست

            // ---- نهاية الكونفجات ----
    };

    // =====================================================================
    //  Built-in servers ("SELECT SERVER"). "real = true" servers have working
    //  SSH credentials baked in below and can actually connect. "real = false"
    //  entries are placeholders kept for the look of the list; connecting to
    //  one just logs a message instead of trying to reach a server that
    //  doesn't exist.
    //
    //  >>> ADD YOUR OWN SERVERS BELOW THIS LINE <<<
    // =====================================================================
    private static final class ServerDef {
        final String flag;
        final String name;
        final String host;
        final int port;
        final String user;
        final String pass;
        final boolean real;

        ServerDef(String flag, String name, String host, int port,
                  String user, String pass, boolean real) {
            this.flag = flag;
            this.name = name;
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass;
            this.real = real;
        }
    }

    // =====================================================================
    //  اضافة سيرفر جديد (خانة SELECT SERVER):
    //  انسخ السطور اللي تحت (من new ServerDef لحد "true),") والصقهم
    //  فوق السطر المكتوب فيه "نهاية السيرفرات الشغالة"، وغيّر البيانات.
    //
    //      new ServerDef(
    //              "\uD83C\uDDF8\uD83C\uDDEC",   // العلم (ايموجي) - ممكن تكتب ايموجي عادي
    //              "اكتب هنا اسم السيرفر",         // اسم السيرفر (بيظهر في البرنامج)
    //              "اكتب هنا المضيف",              // المضيف (Host) يعني عنوان السيرفر
    //              22,                            // البورت (رقم من غير تنصيص)
    //              "اكتب هنا اليوزر نيم",          // اسم المستخدم (Username)
    //              "اكتب هنا الباسورد",            // كلمة السر (Password)
    //              true),                         // true = سيرفر شغال
    //
    //  مهم: البورت رقم من غير علامات تنصيص. الباقي بين تنصيص " ".
    //  وكل سطر لازم ينتهي بفاصلة "," (زي الامثلة).
    // =====================================================================
    private static final ServerDef[] SERVERS = new ServerDef[]{
            // ---- سيرفرات شغالة (فيها بيانات دخول حقيقية) ----
            new ServerDef(
                    "\uD83C\uDDF8\uD83C\uDDEC",   // العلم (سنغافورة)
                    "Pro Server 1\uD83C\uDFAE",    // اسم السيرفر
                    "ssh-sg1.sshkit.org",          // المضيف (Host)
                    22,                            // البورت
                    "sshkit-5950555678",           // اسم المستخدم (Username)
                    "sdfeccdec",                   // كلمة السر (Password)
                    true),                         // true = شغال

            // ---- الصق السيرفرات الجديدة هنا (فوق نهاية السيرفرات الشغالة) ----

            // ---- نهاية السيرفرات الشغالة ----

            // ---- سيرفرات شكلية (للعرض بس، مش شغالة) ----
            new ServerDef(
                    "\uD83C\uDDE9\uD83C\uDDEA",                       // العلم
                    "Germany Server",   // اسم السيرفر
                    "", 0,                          // المضيف + البورت (فاضيين)
                    "", "",                         // اليوزر + الباسورد (فاضيين)
                    false),                         // false = مش شغال
            new ServerDef(
                    "\uD83C\uDDEC\uD83C\uDDE7",                       // العلم
                    "United Kingdom Server",   // اسم السيرفر
                    "", 0,                          // المضيف + البورت (فاضيين)
                    "", "",                         // اليوزر + الباسورد (فاضيين)
                    false),                         // false = مش شغال
            new ServerDef(
                    "\uD83C\uDDFA\uD83C\uDDF8",                       // العلم
                    "United States Server",   // اسم السيرفر
                    "", 0,                          // المضيف + البورت (فاضيين)
                    "", "",                         // اليوزر + الباسورد (فاضيين)
                    false),                         // false = مش شغال
            new ServerDef(
                    "\uD83C\uDDF3\uD83C\uDDF1",                       // العلم
                    "Netherlands Server",   // اسم السيرفر
                    "", 0,                          // المضيف + البورت (فاضيين)
                    "", "",                         // اليوزر + الباسورد (فاضيين)
                    false),                         // false = مش شغال
            new ServerDef(
                    "\uD83C\uDF10",                       // العلم
                    "Free Server 01",   // اسم السيرفر
                    "", 0,                          // المضيف + البورت (فاضيين)
                    "", "",                         // اليوزر + الباسورد (فاضيين)
                    false),                         // false = مش شغال
            new ServerDef(
                    "\uD83C\uDF10",                       // العلم
                    "Free Server 02",   // اسم السيرفر
                    "", 0,                          // المضيف + البورت (فاضيين)
                    "", "",                         // اليوزر + الباسورد (فاضيين)
                    false),                         // false = مش شغال
    };

    // ---------- Views ----------
    private View svRoot, cardStatus, cardDownload, cardUpload, cardSelected, cardLogs;
    private View boxService, boxServer;
    private ImageView ivLogo, ivStatus;
    private TextView tvTitle, tvSubtitle, tvStatus, tvStatusInfo, tvNetwork;
    private TextView tvDownload, tvUpload, tvSelected, tvLog, btnSettings;
    private TextView tvDownloadLabel, tvUploadLabel, tvLblService, tvLblServer;
    private TextView tvCfgLabel, tvLogTitle, tvServerNote;
    private Button btnConnect, btnRandom;
    private Spinner spService, spServer;

    // ---------- State ----------
    private boolean connected = false;
    private boolean connecting = false;
    private long lastStopTime = 0;
    private long connectTime = 0;
    private int lastService = 0;
    private int lastServer = 0;

    private final ArrayList<String> logLines = new ArrayList<String>();
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            syncWithService();
            if (connected) {
                updateTraffic(SiodhVpnService.rxBytes.get(), SiodhVpnService.txBytes.get());
                updateStatusInfo();
            }
            handler.postDelayed(this, 1000);
        }
    };

    // =====================================================================
    //  Lifecycle
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (getActionBar() != null) {
            getActionBar().hide();
        }
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_BG_TOP);
            getWindow().setNavigationBarColor(C_BG_BOTTOM);
        }

        bindViews();
        applyStyles();
        setupSpinners();
        setupListeners();

        updateSelected();
        setConnectedUi(false);

        addLog("SIODH VPN initialized.");
        addLog("Ready to connect.");

        syncWithService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncWithService();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) {
                startVpn();
            } else {
                addLog("VPN permission denied.");
            }
        }
    }

    // =====================================================================
    //  Setup
    // =====================================================================

    private void bindViews() {
        svRoot = findViewById(R.id.svRoot);
        cardStatus = findViewById(R.id.cardStatus);
        cardDownload = findViewById(R.id.cardDownload);
        cardUpload = findViewById(R.id.cardUpload);
        cardSelected = findViewById(R.id.cardSelected);
        cardLogs = findViewById(R.id.cardLogs);
        boxService = findViewById(R.id.boxService);
        boxServer = findViewById(R.id.boxServer);

        ivLogo = (ImageView) findViewById(R.id.ivLogo);
        ivStatus = (ImageView) findViewById(R.id.ivStatus);

        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvStatusInfo = (TextView) findViewById(R.id.tvStatusInfo);
        tvNetwork = (TextView) findViewById(R.id.tvNetwork);
        tvDownload = (TextView) findViewById(R.id.tvDownload);
        tvUpload = (TextView) findViewById(R.id.tvUpload);
        tvSelected = (TextView) findViewById(R.id.tvSelected);
        tvLog = (TextView) findViewById(R.id.tvLog);
        btnSettings = (TextView) findViewById(R.id.btnSettings);
        tvDownloadLabel = (TextView) findViewById(R.id.tvDownloadLabel);
        tvUploadLabel = (TextView) findViewById(R.id.tvUploadLabel);
        tvLblService = (TextView) findViewById(R.id.tvLblService);
        tvLblServer = (TextView) findViewById(R.id.tvLblServer);
        tvCfgLabel = (TextView) findViewById(R.id.tvCfgLabel);
        tvLogTitle = (TextView) findViewById(R.id.tvLogTitle);
        tvServerNote = (TextView) findViewById(R.id.tvServerNote);

        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnRandom = (Button) findViewById(R.id.btnRandom);

        spService = (Spinner) findViewById(R.id.spService);
        spServer = (Spinner) findViewById(R.id.spServer);
    }

    private void applyStyles() {
        setBg(svRoot, box(new int[]{C_BG_TOP, C_BG_BOTTOM},
                GradientDrawable.Orientation.TOP_BOTTOM, 0, 0, 0));

        setBg(cardDownload, box(new int[]{0xFF0E1A33, 0xFF10132B},
                GradientDrawable.Orientation.TOP_BOTTOM, 20, C_CARD_STROKE, 1));
        setBg(cardUpload, box(new int[]{0xFF1A1236, 0xFF10132B},
                GradientDrawable.Orientation.TOP_BOTTOM, 20, C_CARD_STROKE, 1));
        setBg(cardSelected, box(new int[]{C_CARD}, GradientDrawable.Orientation.TOP_BOTTOM,
                20, C_CARD_STROKE, 1));
        setBg(cardLogs, box(new int[]{0xFF0A0C1F}, GradientDrawable.Orientation.TOP_BOTTOM,
                20, C_CARD_STROKE, 1));
        setBg(boxService, box(new int[]{C_CARD}, GradientDrawable.Orientation.TOP_BOTTOM,
                16, C_CARD_STROKE, 1));
        setBg(boxServer, box(new int[]{C_CARD}, GradientDrawable.Orientation.TOP_BOTTOM,
                16, C_CARD_STROKE, 1));
        setBg(btnSettings, box(new int[]{C_CARD}, GradientDrawable.Orientation.TOP_BOTTOM,
                14, C_CARD_STROKE, 1));
        setBg(btnRandom, box(new int[]{0x00000000}, GradientDrawable.Orientation.TOP_BOTTOM,
                18, C_PURPLE, 2));

        ivLogo.setImageBitmap(makeShieldIcon(dp(92), C_VIOLET, C_CYAN, 0, false));

        spacing(tvTitle, 0.08f);
        spacing(tvSubtitle, 0.25f);
        spacing(tvLblService, 0.15f);
        spacing(tvLblServer, 0.15f);
        spacing(tvCfgLabel, 0.12f);
        spacing(tvLogTitle, 0.15f);
        spacing(tvDownloadLabel, 0.12f);
        spacing(tvUploadLabel, 0.12f);
        spacing(tvStatus, 0.1f);
        spacing(btnConnect, 0.1f);

        if (Build.VERSION.SDK_INT >= 21) {
            btnConnect.setStateListAnimator(null);
            btnRandom.setStateListAnimator(null);
        }
        addPressEffect(btnConnect);
        addPressEffect(btnRandom);
        addPressEffect(btnSettings);
    }

    private void setupSpinners() {
        String[] configLabels = new String[CONFIGS.length];
        for (int i = 0; i < CONFIGS.length; i++) {
            configLabels[i] = CONFIGS[i].name;
        }

        String[] serverLabels = new String[SERVERS.length];
        for (int i = 0; i < SERVERS.length; i++) {
            String ready = SERVERS[i].real ? "" : " (not configured)";
            serverLabels[i] = SERVERS[i].flag + "  " + SERVERS[i].name + ready;
        }

        spService.setAdapter(makeAdapter(configLabels));
        spServer.setAdapter(makeAdapter(serverLabels));

        if (Build.VERSION.SDK_INT >= 16) {
            spService.setPopupBackgroundDrawable(box(new int[]{0xFF141838},
                    GradientDrawable.Orientation.TOP_BOTTOM, 14, C_CARD_STROKE, 1));
            spServer.setPopupBackgroundDrawable(box(new int[]{0xFF141838},
                    GradientDrawable.Orientation.TOP_BOTTOM, 14, C_CARD_STROKE, 1));
        }

        // Start on the server that actually has credentials, if there is one.
        int startServer = 0;
        for (int i = 0; i < SERVERS.length; i++) {
            if (SERVERS[i].real) {
                startServer = i;
                break;
            }
        }
        spService.setSelection(0);
        spServer.setSelection(startServer);
        lastService = 0;
        lastServer = startServer;
    }

    private void setupListeners() {

        spService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != lastService) {
                    lastService = position;
                    addLog("Config selected: " + CONFIGS[position].name + ".");
                    if (connected) {
                        addLog("Reconnect to apply the new config.");
                    }
                }
                updateSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spServer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != lastServer) {
                    lastServer = position;
                    addLog("Server selected: " + SERVERS[position].name + ".");
                    if (connected) {
                        addLog("Reconnect to apply the new server.");
                    }
                }
                updateSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connected || connecting) {
                    stopVpn();
                } else if (SystemClock.elapsedRealtime() - lastStopTime < 2500
                        || SiodhVpnService.isTunnelOpen()) {
                    addLog("Still closing the previous connection, try again in a moment.");
                } else {
                    requestConnect();
                }
            }
        });

        btnRandom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickRandom();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        // Quiet automatic check: only shows a dialog if an update actually exists,
        // and even then nothing downloads or installs without the user tapping "Update now".
        UpdateChecker.check(this, true);
    }

    // =====================================================================
    //  VPN control
    // =====================================================================

    private void requestConnect() {
        ServerDef srv = SERVERS[spServer.getSelectedItemPosition()];
        if (!srv.real) {
            addLog(srv.name + " has no credentials configured yet. Pick a ready server.");
            return;
        }

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            addLog("VPN permission requested.");
            try {
                startActivityForResult(prepare, REQ_VPN);
            } catch (Exception e) {
                addLog("Cannot open the VPN permission dialog.");
            }
        } else {
            startVpn();
        }
    }

    private void startVpn() {
        ServerDef srv = SERVERS[spServer.getSelectedItemPosition()];
        ConfigDef cfg = CONFIGS[spService.getSelectedItemPosition()];

        if (!srv.real) {
            addLog(srv.name + " has no credentials configured yet.");
            return;
        }

        String label = srv.user + "@" + srv.host + ":" + srv.port;

        Intent svc = new Intent(this, SiodhVpnService.class);
        svc.setAction(SiodhVpnService.ACTION_START);
        svc.putExtra(SiodhVpnService.EXTRA_HOST, srv.host);
        svc.putExtra(SiodhVpnService.EXTRA_PORT, srv.port);
        svc.putExtra(SiodhVpnService.EXTRA_USER, srv.user);
        svc.putExtra(SiodhVpnService.EXTRA_PASS, srv.pass);
        svc.putExtra(SiodhVpnService.EXTRA_HEADER_HOST, cfg.headerHost);
        svc.putExtra(SiodhVpnService.EXTRA_LABEL, label);

        SiodhVpnService.lastError = "";
        SiodhVpnService.state = SiodhVpnService.STATE_CONNECTING;
        try {
            startService(svc);
        } catch (Exception e) {
            SiodhVpnService.state = SiodhVpnService.STATE_IDLE;
            addLog("Failed to start VPN service: " + e.getMessage());
            return;
        }

        connectTime = SystemClock.elapsedRealtime();
        connecting = true;
        setConnectingUi();
        addLog("VPN service started.");
        if (cfg.headerHost.length() > 0) {
            addLog("Connecting to " + srv.host + ":" + srv.port
                    + " (header host: " + cfg.headerHost + ")...");
        } else {
            addLog("Connecting to " + srv.host + ":" + srv.port + " over SSH...");
        }
    }

    private void stopVpn() {
        boolean wasConnecting = connecting && !connected;
        lastStopTime = SystemClock.elapsedRealtime();
        // Shut the running service down directly (instant), then also send the STOP
        // command in case the service object was not reachable.
        try {
            SiodhVpnService.stopNow();
        } catch (Throwable ignored) {
        }
        try {
            Intent stopIntent = new Intent(this, SiodhVpnService.class);
            stopIntent.setAction(SiodhVpnService.ACTION_STOP);
            startService(stopIntent);
        } catch (Exception e) {
            addLog("Failed to stop VPN service: " + e.getMessage());
        }
        // Marking the service idle here (instead of waiting for onDestroy to report
        // back) is what makes the status-bar VPN key disappear right away.
        SiodhVpnService.state = SiodhVpnService.STATE_IDLE;
        connecting = false;
        setConnectedUi(false);
        addLog(wasConnecting ? "Connection cancelled." : "VPN disconnected.");

        // Safety net: if the VPN interface is somehow still open a moment later, close it.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (SiodhVpnService.isTunnelOpen() && !connecting) {
                    SiodhVpnService.stopNow();
                    addLog("The VPN was still open - closed it now.");
                }
            }
        }, 1500);
    }

    /** Keeps the screen in step with what the service is really doing. */
    private void syncWithService() {
        long now = SystemClock.elapsedRealtime();
        int st = SiodhVpnService.state;

        if (st == SiodhVpnService.STATE_FAILED) {
            String err = SiodhVpnService.lastError;
            SiodhVpnService.state = SiodhVpnService.STATE_IDLE;
            connecting = false;
            setConnectedUi(false);
            addLog("Connection failed: " + err);
            return;
        }

        if (st == SiodhVpnService.STATE_CONNECTED && !connected) {
            connecting = false;
            connectTime = SystemClock.elapsedRealtime();
            setConnectedUi(true);
            addLog("Connected successfully.");
            showConnectedNotification();
            addLog(SiodhVpnService.TUNNEL_ALL_APPS
                    ? "Full tunnel: all apps go through SSH (TCP + DNS). UDP games/calls and ping do not."
                    : "Traffic goes through SSH for apps that use the system proxy (e.g. Chrome).");
        } else if (st == SiodhVpnService.STATE_CONNECTING && !connecting && !connected) {
            connecting = true;
            connectTime = SystemClock.elapsedRealtime();
            setConnectingUi();
        } else if (st == SiodhVpnService.STATE_IDLE && (connected || connecting)
                && (now - connectTime) > 3000) {
            boolean wasConnected = connected;
            connecting = false;
            setConnectedUi(false);
            addLog(wasConnected ? "VPN disconnected." : "Connection ended.");
        }
    }

    // =====================================================================
    //  UI updates
    // =====================================================================

    private void setConnectingUi() {
        connected = false;
        tvStatus.setText("CONNECTING...");
        tvStatus.setTextColor(C_CYAN);
        setBg(cardStatus, box(new int[]{0xFF0C1E38, 0xFF0D1030},
                GradientDrawable.Orientation.TOP_BOTTOM, 28, 0xAA22D3EE, 2));
        ivStatus.setImageBitmap(makeShieldIcon(dp(240), C_VIOLET, C_CYAN, 0, true));
        String label = SiodhVpnService.currentLabel.length() > 0
                ? SiodhVpnService.currentLabel : "the selected server";
        tvStatusInfo.setText("Opening SSH connection to " + label + "\nTap CANCEL to stop.");
        btnConnect.setText("CANCEL");
        setBg(btnConnect, box(new int[]{0xFF2B2F63, 0xFF3A2A6B},
                GradientDrawable.Orientation.LEFT_RIGHT, 22, 0, 0));
        setNetworkPill("NETWORK: CONNECTING", C_CYAN);
    }

    private void setConnectedUi(boolean c) {
        connected = c;
        int accent = c ? C_GREEN : C_RED;

        tvStatus.setText(c ? "CONNECTED" : "DISCONNECTED");
        tvStatus.setTextColor(accent);

        if (c) {
            setBg(cardStatus, box(new int[]{0xFF0A2A33, 0xFF0D1030},
                    GradientDrawable.Orientation.TOP_BOTTOM, 28, 0xAA2CF5B0, 2));
            ivStatus.setImageBitmap(makeShieldIcon(dp(240), C_CYAN, C_GREEN, 1, true));
            btnConnect.setText("DISCONNECT");
            setBg(btnConnect, box(new int[]{C_RED, C_PURPLE},
                    GradientDrawable.Orientation.LEFT_RIGHT, 22, 0, 0));
            setNetworkPill("NETWORK: SSH TUNNEL ACTIVE", C_GREEN);
            updateStatusInfo();
        } else {
            setBg(cardStatus, box(new int[]{0xFF1E1033, 0xFF0D1030},
                    GradientDrawable.Orientation.TOP_BOTTOM, 28, 0xAAFF4D8D, 2));
            ivStatus.setImageBitmap(makeShieldIcon(dp(240), C_RED, C_VIOLET, 0, true));
            btnConnect.setText("START CONNECTION");
            setBg(btnConnect, box(new int[]{C_VIOLET, C_BLUE},
                    GradientDrawable.Orientation.LEFT_RIGHT, 22, 0, 0));
            setNetworkPill("NETWORK: READY", C_CYAN);
            tvStatusInfo.setText("Pick a ready server, then tap START CONNECTION.");
            updateTraffic(0, 0);
        }
    }

    private void setNetworkPill(String text, int color) {
        tvNetwork.setText(text);
        tvNetwork.setTextColor(color);
        int fill = (color & 0x00FFFFFF) | 0x22000000;
        int stroke = (color & 0x00FFFFFF) | 0x88000000;
        setBg(tvNetwork, box(new int[]{fill}, GradientDrawable.Orientation.TOP_BOTTOM,
                20, stroke, 1));
    }

    private void updateStatusInfo() {
        long sec = (SystemClock.elapsedRealtime() - connectTime) / 1000;
        String time = String.format(Locale.US, "%02d:%02d:%02d",
                sec / 3600, (sec % 3600) / 60, sec % 60);
        int p = SiodhVpnService.proxyPort;
        String header = SiodhVpnService.currentHeaderHost;
        StringBuilder info = new StringBuilder();
        info.append("SSH: ").append(SiodhVpnService.currentLabel);
        if (header != null && header.length() > 0) {
            info.append("\nHeader host: ").append(header);
            if (SiodhVpnService.headerHostIgnored) {
                info.append(" (not used: this port is plain SSH)");
            }
        }
        if (SiodhVpnService.transport != null && SiodhVpnService.transport.length() > 0) {
            info.append("\nTransport: ").append(SiodhVpnService.transport);
        }
        info.append("\nTime: ").append(time);
        if (SiodhVpnService.TUNNEL_ALL_APPS) {
            info.append("\nMode: full tunnel (all apps)");
        } else {
            info.append("\nLocal proxy: 127.0.0.1:").append(p > 0 ? String.valueOf(p) : "-");
        }
        tvStatusInfo.setText(info.toString());
    }

    /** Download / upload numbers: bytes that really went through the SSH tunnel. */
    public void updateTraffic(long rxBytes, long txBytes) {
        tvDownload.setText(formatBytes(rxBytes));
        tvUpload.setText(formatBytes(txBytes));
    }

    private void updateSelected() {
        int s = spService.getSelectedItemPosition();
        int v = spServer.getSelectedItemPosition();
        if (s < 0) s = 0;
        if (v < 0) v = 0;

        ConfigDef cfg = CONFIGS[s];
        ServerDef srv = SERVERS[v];

        String text = cfg.name + " \u2022 " + srv.name;
        tvSelected.setText(text);
    }

    private void pickRandom() {
        int s = random.nextInt(CONFIGS.length);
        int v = random.nextInt(SERVERS.length);
        lastService = s;
        lastServer = v;
        spService.setSelection(s);
        spServer.setSelection(v);
        updateSelected();
        addLog("Random pick: " + CONFIGS[s].name + " / " + SERVERS[v].name + ".");
        if (connected) {
            addLog("Reconnect to apply the new selection.");
        }
    }

    private void showSettings() {
        final String[] items = {"Open Android VPN settings", "Clear logs",
                "Check for updates", "About SIODH VPN"};
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            try {
                                startActivity(new Intent("android.settings.VPN_SETTINGS"));
                            } catch (ActivityNotFoundException e) {
                                addLog("VPN settings screen not available on this device.");
                            } catch (Exception e) {
                                addLog("Could not open VPN settings.");
                            }
                        } else if (which == 1) {
                            logLines.clear();
                            addLog("Logs cleared.");
                        } else if (which == 2) {
                            UpdateChecker.check(MainActivity.this, false);
                        } else {
                            showAbout();
                        }
                    }
                })
                .show();
    }

    // =====================================================================
    //  اشعار "تم التفعيل" لما الاتصال ينجح
    // =====================================================================

    private static final String NOTIF_CHANNEL_ID = "siodh_vpn_status";
    private static final int NOTIF_ID = 501;

    private void showConnectedNotification() {
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = nm.getNotificationChannel(NOTIF_CHANNEL_ID);
                if (ch == null) {
                    ch = new NotificationChannel(NOTIF_CHANNEL_ID, "SIODH VPN",
                            NotificationManager.IMPORTANCE_DEFAULT);
                    ch.setDescription("SIODH VPN connection status");
                    nm.createNotificationChannel(ch);
                }
            }

            Intent open = new Intent(this, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int piFlags = Build.VERSION.SDK_INT >= 23
                    ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, NOTIF_CHANNEL_ID)
                    : new Notification.Builder(this);
            b.setContentTitle("SIODH VPN")
                    .setContentText("تم التفعيل")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true);

            nm.notify(NOTIF_ID, b.build());
        } catch (Throwable ignored) {
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About SIODH VPN")
                .setMessage("SIODH VPN 1.3 (SSH full tunnel)\n\n"
                        + "Connects to a built-in SSH server and sends the phone's TCP "
                        + "traffic and DNS through it, for every app (Android 5 or newer).\n\n"
                        + "Not supported through SSH: UDP (games, voice/video calls, QUIC), "
                        + "ping and direct IPv6. Host keys are not verified. V2RAY, VMESS, "
                        + "VLESS, SSL and UDP are labels only; the real connection is always "
                        + "SSH.\n\n"
                        + "The header-host configs try a Host-header bypass; this only works "
                        + "if the server supports it on that port (usually 80/443).")
                .setPositiveButton("OK", null)
                .show();
    }

    private void addLog(String msg) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logLines.add("[" + t + "] " + msg);
        while (logLines.size() > MAX_LOG_LINES) {
            logLines.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < logLines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(logLines.get(i));
        }
        tvLog.setText(sb.toString());
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private String formatBytes(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void spacing(TextView t, float value) {
        if (Build.VERSION.SDK_INT >= 21) {
            t.setLetterSpacing(value);
        }
    }

    private void setBg(View v, Drawable d) {
        if (Build.VERSION.SDK_INT >= 16) {
            v.setBackground(d);
        } else {
            v.setBackgroundDrawable(d);
        }
    }

    private GradientDrawable box(int[] colors, GradientDrawable.Orientation orientation,
                                 int radiusDp, int strokeColor, int strokeDp) {
        int[] c = colors;
        if (c.length == 1) {
            c = new int[]{colors[0], colors[0]};
        }
        GradientDrawable g = new GradientDrawable(orientation, c);
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(strokeDp), strokeColor);
        }
        return g;
    }

    private void addPressEffect(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int a = event.getAction();
                if (a == MotionEvent.ACTION_DOWN) {
                    view.setAlpha(0.75f);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    view.setAlpha(1f);
                }
                return false; // let the click listener run
            }
        });
    }

    private ArrayAdapter<String> makeAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                MainActivity.this, android.R.layout.simple_spinner_item, items) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView t = (TextView) v;
                    t.setTextColor(C_TEXT);
                    t.setTextSize(15);
                    t.setSingleLine(true);
                    t.setPadding(dp(16), 0, dp(40), 0);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView t = (TextView) v;
                    t.setTextColor(C_TEXT);
                    t.setTextSize(15);
                    t.setBackgroundColor(0xFF141838);
                    t.setPadding(dp(16), dp(12), dp(16), dp(12));
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    /**
     * Draws the SIODH shield icon in code (no image files needed).
     * glyph: 0 = keyhole, 1 = check mark. ring: adds glow rings around the shield.
     */
    private Bitmap makeShieldIcon(int size, int c1, int c2, int glyph, boolean ring) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float w = size;

        if (ring) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.025f);
            paint.setColor(c1);
            paint.setAlpha(150);
            canvas.drawCircle(w / 2f, w / 2f, w * 0.47f, paint);
            paint.setAlpha(60);
            canvas.drawCircle(w / 2f, w / 2f, w * 0.40f, paint);
            canvas.save();
            canvas.scale(0.62f, 0.62f, w / 2f, w / 2f);
        }

        Path shield = new Path();
        shield.moveTo(0.50f * w, 0.05f * w);
        shield.lineTo(0.88f * w, 0.18f * w);
        shield.lineTo(0.88f * w, 0.50f * w);
        shield.cubicTo(0.88f * w, 0.75f * w, 0.68f * w, 0.88f * w, 0.50f * w, 0.96f * w);
        shield.cubicTo(0.32f * w, 0.88f * w, 0.12f * w, 0.75f * w, 0.12f * w, 0.50f * w);
        shield.lineTo(0.12f * w, 0.18f * w);
        shield.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0, 0, w, w, c1, c2, Shader.TileMode.CLAMP));
        canvas.drawPath(shield, paint);
        paint.setShader(null);

        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setColor(0xFF060814);
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setStrokeJoin(Paint.Join.ROUND);
        g.setStrokeWidth(w * 0.075f);

        if (glyph == 1) {
            g.setStyle(Paint.Style.STROKE);
            Path check = new Path();
            check.moveTo(0.33f * w, 0.50f * w);
            check.lineTo(0.45f * w, 0.62f * w);
            check.lineTo(0.68f * w, 0.36f * w);
            canvas.drawPath(check, g);
        } else {
            g.setStyle(Paint.Style.FILL);
            canvas.drawCircle(0.50f * w, 0.42f * w, 0.075f * w, g);
            g.setStyle(Paint.Style.STROKE);
            canvas.drawLine(0.50f * w, 0.45f * w, 0.50f * w, 0.62f * w, g);
        }

        if (ring) {
            canvas.restore();
        }
        return bmp;
    }
}

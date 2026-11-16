package com.yunus.hourlyrecorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.FileOutputStream;

/** Shows the Hourly Recorder page, which is packed inside the app, in a full-screen web view. */
public class MainActivity extends AppCompatActivity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) {
                    return false;
                }
                // Outside links, such as WhatsApp, open in their own app.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // No app can open this link.
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new ShareBridge(), "AndroidShare");
        webView.addJavascriptInterface(new BiometricBridge(), "AndroidBiometric");
        webView.addJavascriptInterface(new UpdateBridge(), "AndroidUpdate");

        if (savedInstanceState == null) {
            webView.loadUrl(START);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Lets the page hand a picture to the phone's share sheet. */
    private class ShareBridge {
        @JavascriptInterface
        public void shareImage(String base64Png, String text) {
            try {
                byte[] data = Base64.decode(base64Png, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) {
                    return;
                }
                File file = new File(dir, "hourly-graph.png");
                FileOutputStream out = new FileOutputStream(file);
                try {
                    out.write(data);
                } finally {
                    out.close();
                }
                Uri uri = FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".fileprovider", file);
                final Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/png");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_TEXT, text);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(Intent.createChooser(send, "Share graph"));
                    }
                });
            } catch (Exception ignored) {
                // Sharing failed; the page stays as it is.
            }
        }
    }

    /**
     * Fingerprint or face unlock for the page. The page stores nothing secret here except the same
     * login token it already keeps on the phone; the fingerprint is the lock that releases it.
     */
    private class BiometricBridge {
        private static final int AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK;

        private SharedPreferences prefs() {
            return getSharedPreferences("bio", Context.MODE_PRIVATE);
        }

        @JavascriptInterface
        public boolean available() {
            return BiometricManager.from(MainActivity.this).canAuthenticate(AUTH)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public boolean has(String role) {
            return prefs().contains("token_" + role);
        }

        @JavascriptInterface
        public void forget(String role) {
            prefs().edit().remove("token_" + role).apply();
        }

        @JavascriptInterface
        public void enroll(final String role, final String token, final int callbackId) {
            prompt("Turn on fingerprint login", "Confirm it is you", new Result() {
                @Override
                public void done(boolean ok) {
                    if (ok) {
                        prefs().edit().putString("token_" + role, token).apply();
                    }
                    reply(callbackId, ok, "");
                }
            });
        }

        @JavascriptInterface
        public void login(final String role, final int callbackId) {
            prompt("Log in", "Use your fingerprint or face", new Result() {
                @Override
                public void done(boolean ok) {
                    String token = ok ? prefs().getString("token_" + role, "") : "";
                    reply(callbackId, ok && token.length() > 0, token);
                }
            });
        }

        private void prompt(final String title, final String subtitle, final Result result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle(title)
                            .setSubtitle(subtitle)
                            .setNegativeButtonText("Cancel")
                            .setAllowedAuthenticators(AUTH)
                            .build();
                    BiometricPrompt p = new BiometricPrompt(MainActivity.this,
                            ContextCompat.getMainExecutor(MainActivity.this),
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                                    result.done(true);
                                }

                                @Override
                                public void onAuthenticationError(int code, CharSequence message) {
                                    result.done(false);
                                }
                            });
                    p.authenticate(info);
                }
            });
        }

        private void reply(final int callbackId, final boolean ok, final String token) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("window.__bio(" + callbackId + "," + ok + ","
                            + JSONObject.quote(token) + ")", null);
                }
            });
        }
    }


    /** Checks GitHub for a newer version of this app, downloads it and hands it to Android's installer. */
    private class UpdateBridge {
        private static final String REPO = "rehmanytml-hue/Small-Parts-GF-";
        private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
        private static final String DOWNLOADS = "https://github.com/" + REPO + "/releases/download/";

        @JavascriptInterface
        public String version() {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                return new JSONObject().put("code", info.versionCode).put("name", info.versionName).toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void check(final int callbackId) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                        c.setRequestProperty("Accept", "application/vnd.github+json");
                        c.setRequestProperty("User-Agent", "HourlyRecorder");
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(15000);
                        if (c.getResponseCode() != 200) {
                            reply(callbackId, new JSONObject().put("state", "error").put("msg", "No release found yet."));
                            return;
                        }
                        String body = readAll(c.getInputStream());
                        JSONObject rel = new JSONObject(body);
                        int latest = Integer.parseInt(rel.getString("tag_name").replaceAll("[^0-9]", ""));
                        int current = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                        String url = "";
                        org.json.JSONArray assets = rel.getJSONArray("assets");
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject a = assets.getJSONObject(i);
                            if (a.getString("name").endsWith(".apk")) {
                                url = a.getString("browser_download_url");
                            }
                        }
                        if (latest > current && url.length() > 0) {
                            reply(callbackId, new JSONObject().put("state", "available").put("code", latest).put("url", url));
                        } else {
                            reply(callbackId, new JSONObject().put("state", "latest"));
                        }
                    } catch (Exception e) {
                        reply(callbackId, jsonError("Could not check for updates. Check the internet connection."));
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void install(final String url, final int callbackId) {
            if (url == null || !url.startsWith(DOWNLOADS)) {
                reply(callbackId, jsonError("That download is not allowed."));
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }
                });
                reply(callbackId, jsonState("permission"));
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setRequestProperty("User-Agent", "HourlyRecorder");
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                        int total = c.getContentLength();
                        File dir = new File(getCacheDir(), "shared");
                        if (!dir.exists() && !dir.mkdirs()) {
                            reply(callbackId, jsonError("Could not save the update."));
                            return;
                        }
                        File apk = new File(dir, "update.apk");
                        InputStream in = c.getInputStream();
                        FileOutputStream out = new FileOutputStream(apk);
                        try {
                            byte[] buf = new byte[32768];
                            long done = 0;
                            int last = -1;
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                out.write(buf, 0, n);
                                done += n;
                                int pct = total > 0 ? (int) (done * 100 / total) : 0;
                                if (pct / 5 != last) {
                                    last = pct / 5;
                                    reply(callbackId, new JSONObject().put("state", "downloading").put("pct", pct));
                                }
                            }
                        } finally {
                            out.close();
                            in.close();
                        }
                        Uri uri = FileProvider.getUriForFile(
                                MainActivity.this, getPackageName() + ".fileprovider", apk);
                        final Intent go = new Intent(Intent.ACTION_VIEW);
                        go.setDataAndType(uri, "application/vnd.android.package-archive");
                        go.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(go);
                            }
                        });
                        reply(callbackId, jsonState("ready"));
                    } catch (Exception e) {
                        reply(callbackId, jsonError("The download failed. Try again."));
                    }
                }
            }).start();
        }

        private String readAll(InputStream in) throws Exception {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            return out.toString("UTF-8");
        }

        private JSONObject jsonState(String state) {
            try {
                return new JSONObject().put("state", state);
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        private JSONObject jsonError(String msg) {
            try {
                return new JSONObject().put("state", "error").put("msg", msg);
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        private void reply(final int callbackId, final JSONObject json) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("window.__upd(" + callbackId + ","
                            + JSONObject.quote(json.toString()) + ")", null);
                }
            });
        }
    }

    private interface Result {
        void done(boolean ok);
    }
}

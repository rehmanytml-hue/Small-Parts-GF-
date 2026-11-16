package com.yunus.hourlyrecorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private interface Result {
        void done(boolean ok);
    }
}

package com.forbiddenvod.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Forbidden VOD — a thin Android TV shell around the web interface in assets/index.html.
 * The interface talks to Stremio-compatible addons directly; this class adds what a web page
 * can't do on its own: the TV launcher entry, the remote's Back / media keys, keeping the
 * screen awake during playback, and handing streams to an external player like VLC.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/index.html";

    private WebView webView;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private WebChromeClient chromeClient;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(Color.rgb(10, 19, 17));

        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(10, 19, 17));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemUi();

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // Lets the bundled page call addons that don't send CORS headers.
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString() + " ForbiddenVOD/1.0");

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.addJavascriptInterface(new Bridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
        });

        chromeClient = new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                // Avoids the grey "play" placeholder WebView shows before video starts.
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                hideSystemUi();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        };
        webView.setWebChromeClient(chromeClient);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
        webView.requestFocus();
    }

    /** Links inside the app stay in the app; anything else opens in the right TV app. */
    private boolean handleUrl(String url) {
        if (url.startsWith("file:")) return false;
        try {
            Intent intent;
            if (url.startsWith("intent:")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app on this TV can open that link", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    /** Remote media buttons go straight to the player in the page. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        String key = null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK: key = "MediaPlayPause"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY: key = "MediaPlay"; break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: key = "MediaPause"; break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: key = "MediaFastForward"; break;
            case KeyEvent.KEYCODE_MEDIA_REWIND: key = "MediaRewind"; break;
            case KeyEvent.KEYCODE_MEDIA_STOP: key = "MediaStop"; break;
        }
        if (key != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                webView.evaluateJavascript("window.fvMedia&&window.fvMedia('" + key + "')", null);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Back walks back through the app's screens; on the Home screen it exits. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            chromeClient.onHideCustomView();
            return;
        }
        webView.evaluateJavascript("(window.fvBack&&window.fvBack())?'1':'0'", value -> {
            if (value == null || !value.contains("1")) finish();
        });
    }

    @Override
    protected void onPause() {
        webView.evaluateJavascript("window.fvPause&&window.fvPause()", null);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }

    /** Functions the web page can call as window.Android.xxx(...) */
    public class Bridge {
        @JavascriptInterface
        public void openExternal(final String url, final String title) {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(url), "video/*");
                intent.putExtra("title", title);                 // VLC, MX Player, Just Player
                intent.putExtra(Intent.EXTRA_TITLE, title);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(Intent.createChooser(intent, "Play with"));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this,
                            "No video player installed. Install VLC or Just Player from the Play Store.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(() -> handleUrl(url));
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public String version() {
            return "1.0 (" + Build.MODEL + ")";
        }
    }
}

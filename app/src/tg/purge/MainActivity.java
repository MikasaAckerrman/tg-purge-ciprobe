package tg.purge;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * TG PURGE = WebView-шелл над движком (http://127.0.0.1:8790/).
 *
 * Почему ровно 1 файл и ни строки бизнес-логики (PLAN 1.10):
 *   - движок (Pyrogram + state.json + live-чистка) живёт в песочнице Minis на этом же
 *     телефоне; UI раздаётся ИМ, поэтому same-origin fetch работает, а доступ из
 *     Android-процесса в loopback песочницы подтверждён probe'ом (25.11 00:14);
 *   - предыдущая версия этого APK несла UI на Java (Api.java + MainActivity.java, ~500 строк)
 *     и породила класс дефектов, которые нельзя отладить на устройстве (root/adb/shizuku
 *     отсутствуют): optString-on-null («@null»), молчаливые кнопки, диалоги из фонового
 *     потока. Весь этот код вынесен в HTML/JS, который проверяется кликами в WebView.
 *   - отсюда: никаких org.json, никаких HttpURLConnection, никаких кнопок в нативе.
 *
 * Единственное, что здесь есть: загрузить страницу, показать внятную ошибку если движок
 * лежит, и отдать «назад» WebView.
 */
public class MainActivity extends Activity {

    private static final String URL = "http://127.0.0.1:8790/";
    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);   // движок отдаёт свежий UI; кэш врал бы
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                // наружу не ходим: всё взаимодействие только с резидентом
                return r.getUrl().getHost() != null && !r.getUrl().getHost().equals("127.0.0.1");
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r != null && r.isForMainFrame()) {
                    showDown(String.valueOf(e.getErrorCode()),
                             e.getDescription() == null ? "?" : e.getDescription().toString());
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView v, int code, String desc, String url) {
                showDown(String.valueOf(code), desc);
            }
        });
        setContentView(web);
        web.loadUrl(URL);
    }

    /** Страница «движок не отвечает» с живым retry: юзер видит причину, а не пустой экран. */
    private void showDown(String code, String desc) {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box}"
                + "body{background:#000;color:#ededf0;font:15px/1.45 -apple-system,Roboto,sans-serif;"
                + "padding:22px 18px}"
                + "h1{font-size:16px;font-weight:650;margin-bottom:10px}"
                + "p{color:#7c7c86;font-size:13px;margin-bottom:8px}"
                + "code{font-family:ui-monospace,Menlo,monospace;font-size:12px;color:#9fd9b4}"
                + ".c{border:1px solid #1c1c21;background:#0a0a0c;border-radius:6px;padding:12px;margin:14px 0}"
                + "a{display:block;text-align:center;margin-top:18px;padding:15px;border-radius:6px;"
                + "background:#ededf0;color:#000;font-weight:700;text-decoration:none;letter-spacing:.02em}"
                + "</style></head><body>"
                + "<h1>Движок не отвечает</h1>"
                + "<p>UI и вся логика живут в песочнице Minis на этом телефоне "
                + "(<code>" + URL + "</code>). Приложение — только экран над ним.</p>"
                + "<div class='c'><code>err " + esc(code) + " · " + esc(desc) + "</code></div>"
                + "<p>Запусти движок в терминале Minis:</p>"
                + "<div class='c'><code>tg up</code></div>"
                + "<p>Держать его живым дольше, пока свёрнуто Minis — канал <code>bg_run</code>.</p>"
                + "<a href='reload'>ПОПРОБОВАТЬ СНОВА</a>"
                + "</body></html>";
        web.loadDataWithBaseURL(URL, html, "text/html", "utf-8", URL);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.resumeTimers();
        // после возврата из фона страница могла протухнуть: один мягкий reload
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (web != null) web.loadUrl(URL);
            }
        }, 400);
    }

    @Override
    protected void onPause() {
        if (web != null) web.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

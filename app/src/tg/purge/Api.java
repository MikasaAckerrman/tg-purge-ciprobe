package tg.purge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/** Минимальный транспорт: движок живёт в песочнице Minis на 127.0.0.1:8790. */
public final class Api {

    public static final String BASE = "http://127.0.0.1:8790";
    /** движок мог умереть — тогда UI пишет это слово и кнопка «поднять» бесполезна */
    public static final String DEAD = "ENGINE_DOWN";

    private Api() {}

    public static String get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(120000);
        c.setRequestMethod("GET");
        return drain(c);
    }

    public static String post(String path, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(600000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] out = body.toString().getBytes(Charset.forName("UTF-8"));
        OutputStream os = c.getOutputStream();
        os.write(out);
        os.close();
        return drain(c);
    }

    private static String drain(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
        }
        if (code == 502 || code == 503 || code == 504) throw new java.net.SocketTimeoutException("gateway " + code);
        if (sb.length() == 0) throw new Exception("пустой ответ, code=" + code);
        return sb.toString();
    }

    /** state без исключений: null, если движок не отвечает. */
    public static JSONObject stateSafe() {
        try {
            return new JSONObject(get("/api/state"));
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONArray search(String q) {
        try {
            JSONObject o = new JSONObject(get("/api/search?q=" + enc(q)));
            return o.optJSONArray("chats") == null ? new JSONArray() : o.getJSONArray("chats");
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONObject postSimple(String path, JSONObject body) {
        try {
            return new JSONObject(post(path, body));
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            try {
                err.put("ok", false);
                err.put("error", e.getClass().getSimpleName() + ": " + str(e.getMessage()));
            } catch (Exception ignore) {}
            return err;
        }
    }

    public static String str(String s) {
        return s == null ? "?" : s;
    }
}

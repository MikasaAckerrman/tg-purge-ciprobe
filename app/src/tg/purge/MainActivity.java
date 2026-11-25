package tg.purge;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Чёрный минимум: поиск + 3 кнопки. Всё состояние держит движок в песочнице (127.0.0.1:8790). */
public class MainActivity extends Activity {

    static final int BG = Color.BLACK;
    static final int CARD = Color.parseColor("#0C0C0E");
    static final int LINE = Color.parseColor("#212127");
    static final int TXT = Color.parseColor("#E9E9EC");
    static final int DIM = Color.parseColor("#6B6B75");
    static final int GREEN = Color.parseColor("#2BD97A");
    static final int RED = Color.parseColor("#B3261E");
    static final int WHITE = Color.parseColor("#EDEDF0");

    private ExecutorService io = Executors.newSingleThreadExecutor();
    private Handler ui = new Handler(Looper.getMainLooper());

    private TextView headTitle, headMeta, powerBtn, modeBtn, peopleBtn, logView, foundView;
    private LinearLayout results;
    private EditText search;

    private volatile JSONObject lastState = null;
    private volatile String mode = "new";
    private volatile boolean running = false;
    private volatile boolean canDelete = false;
    private final List<Long> picked = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------- UI ----------
    private View buildUi() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        col.setPadding(pad, dp(18), pad, dp(24));
        sc.addView(col, new ScrollView.LayoutParams(-1, -1));

        TextView brand = tv("TG · PURGE", 11, DIM);
        brand.setTypeface(Typeface.MONOSPACE);
        brand.setLetterSpacing(0.14f);
        col.addView(brand);

        headTitle = tv("загрузка…", 19, TXT);
        headTitle.setTypeface(headTitle.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        col.addView(headTitle, lp);

        headMeta = tv("—", 11, DIM);
        headMeta.setTypeface(Typeface.MONOSPACE);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        col.addView(headMeta, lp);

        // поиск
        search = new EditText(this);
        search.setHint("поиск: чат · канал · бот · @ник · id");
        search.setHintTextColor(DIM);
        search.setTextColor(GREEN);
        search.setBackgroundColor(CARD);
        search.setSingleLine(true);
        search.setPadding(dp(14), dp(14), dp(14), dp(14));
        search.setTypeface(Typeface.MONOSPACE);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        col.addView(search, lp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { scheduleSearch(s.toString()); }
        });

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        col.addView(results, lp);

        // 3 кнопки
        powerBtn = bigBtn("▶  НАЧАТЬ", WHITE, Color.BLACK);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(18);
        col.addView(powerBtn, lp);
        powerBtn.setOnClickListener(v -> onPower());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        col.addView(row, lp);

        modeBtn = bigBtn("РЕЖИМ: НОВЫЕ", CARD, TXT);
        modeBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        modeBtn.setOnClickListener(v -> onMode());
        row.addView(modeBtn);

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        row.addView(sp);

        peopleBtn = bigBtn("ЛЮДИ: 0", CARD, TXT);
        peopleBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        peopleBtn.setOnClickListener(v -> onPeople());
        row.addView(peopleBtn);

        foundView = tv("", 11, DIM);
        foundView.setTypeface(Typeface.MONOSPACE);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        col.addView(foundView, lp);

        logView = tv("—", 10.5f, Color.parseColor("#767680"));
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setBackgroundColor(Color.parseColor("#050506"));
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        col.addView(logView, lp);

        return sc;
    }

    private TextView tv(String s, float sp, int col) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(col);
        return t;
    }

    private TextView bigBtn(String label, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15.5f);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setTextColor(fg);
        t.setBackgroundColor(bg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(17), dp(16), dp(17));
        t.setOnClickListener(v -> {});
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- действия ----------
    private void onPower() {
        powerBtn.setEnabled(false);
        io.execute(() -> {
            JSONObject r;
            if (running) {
                r = Api.postSimple("/api/stop", new JSONObject());
            } else {
                r = Api.postSimple("/api/start", new JSONObject());
            }
            post(() -> {
                powerBtn.setEnabled(true);
                applyState(Api.stateSafe());
                String err = r.optString("error", "");
                if (!r.optBoolean("ok", false) && err.length() > 0) toast(err);
            });
        });
    }

    private void onMode() {
        final String next = "new".equals(mode) ? "all" : "new";
        io.execute(() -> {
            if ("all".equals(next)) {
                // сначала счётчик, потом подтверждение — никогда не удаляем молча
                String cnt;
                try { cnt = Api.get("/api/count?all=false&limit=200"); }
                catch (Exception e) { post(() -> toast("движок не отвечает")); return; }
                int foundTmp = 0, scannedTmp = 0;
                try {
                    JSONObject co = new JSONObject(cnt);
                    foundTmp = co.optInt("found", 0);
                    scannedTmp = co.optInt("scanned", 0);
                } catch (Exception e) {
                    final String em = Api.str(e.getMessage());
                    post(() -> toast("счётчик не читается: " + em));
                    return;
                }
                final int found = foundTmp, scanned = scannedTmp;
                post(() -> confirmWipe(found, scanned, next));
            } else {
                JSONObject r = Api.postSimple("/api/mode", jobj("mode", "new"));
                post(() -> { applyState(Api.stateSafe()); String e = r.optString("error", ""); if (e.length() > 0) toast(e); });
            }
        });
    }

    private void confirmWipe(final int found, final int scanned, final String next) {
        AlertDialog.Builder bd = new AlertDialog.Builder(this);
        bd.setTitle("РЕЖИМ ВСЁ")
          .setMessage("В последних " + scanned + " сообщениях целей ждут удаления: " + found
                  + ".\n\nИстория сотрётся безвозвратно (в Telegram отката нет).\n"
                  + "Live-чистка новых сообщений включится сразу.")
          .setNegativeButton("ОТМЕНА", (d, w) -> refresh())
          .setPositiveButton(found > 0 ? "УДАЛИТЬ " + found : "ТОЛЬКО LIVE", (d, w) -> {
                io.execute(() -> {
                    JSONObject r = Api.postSimple("/api/mode", jobj("mode", "all"));
                    if (found > 0) Api.postSimple("/api/wipe", jobjb("confirm", true, "all", false, "limit", 200));
                    Api.postSimple("/api/start", new JSONObject());
                    post(() -> { applyState(Api.stateSafe()); refresh(); });
                });
          });
        bd.show();
    }

    private void onPeople() {
        io.execute(() -> {
            JSONObject st = Api.stateSafe();
            post(() -> {
                if (st == null) { toast("движок не отвечает"); return; }
                JSONObject c = st.optJSONObject("chat");
                if (c == null) { toast("нет активного чата — найди его в поиске"); return; }
                JSONArray ms = c.optJSONArray("members");
                if (ms == null || ms.length() == 0) { toast("людей нет — обнови чат поиском"); return; }
                final String[] names = new String[ms.length()];
                final boolean[] chk = new boolean[ms.length()];
                picked.clear();
                JSONArray t = c.optJSONArray("targets");
                List<Long> cur = new ArrayList<>();
                if (t != null) for (int i = 0; i < t.length(); i++) cur.add(t.optLong(i));
                long me = st.optLong("me_id");
                for (int i = 0; i < ms.length(); i++) {
                    JSONObject u = ms.optJSONObject(i);
                    String nm = u.optString("name", "");
                    String un = u.optString("username", "");
                    String tail = (un.length() > 0 ? "@" + un : "без ника")
                            + " · " + optStatus(u)
                            + " · " + u.optInt("msgs", 0) + " сообщ.";
                    names[i] = nm + "\n" + tail;
                    chk[i] = cur.contains(u.optLong("id"));
                    if (chk[i]) picked.add(u.optLong("id"));
                    if (u.optLong("id") == me) names[i] = names[i] + "  ← ЭТО ТЫ";
                }
                AlertDialog.Builder bd = new AlertDialog.Builder(MainActivity.this);
                bd.setTitle("Люди · " + ms.length())
                  .setMultiChoiceItems(names, chk, (d, which, isChecked) -> {
                        long id = 0;
                        try { id = ms.getJSONObject(which).getLong("id"); } catch (Exception ignore) {}
                        if (isChecked) { if (!picked.contains(id)) picked.add(id); }
                        else picked.remove(Long.valueOf(id));
                  })
                  .setPositiveButton("СОХРАНИТЬ", (d, w) -> {
                        io.execute(() -> {
                            JSONArray arr = new JSONArray();
                            for (Long x : picked) arr.put(x);
                            JSONObject body = new JSONObject();
                            try { body.put("ids", arr); } catch (Exception ignore) {}
                            JSONObject r = Api.postSimple("/api/targets", body);
                            post(() -> { applyState(Api.stateSafe()); String e = r.optString("note", r.optString("error", "")); if (e.length() > 0) toast(e); });
                        });
                  })
                  .setNegativeButton("ОТМЕНА", (d, w) -> refresh())
                  .show();
            });
        });
    }

    private String optStatus(JSONObject u) {
        String s = u.optString("status", "");
        if ("ADMINISTRATOR".equals(s)) return "админ";
        if ("OWNER".equals(s)) return "владелец";
        if ("MEMBER".equals(s)) return "участник";
        return s.isEmpty() ? "?" : s.toLowerCase();
    }

    private JSONObject jobj(String k, Object v) {
        JSONObject o = new JSONObject();
        try { o.put(k, v); } catch (Exception ignore) {}
        return o;
    }

    private JSONObject jobjb(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        JSONObject o = jobj(k1, v1);
        try { o.put(k2, v2); o.put(k3, v3); } catch (Exception ignore) {}
        return o;
    }

    // ---------- данные ----------
    private void refresh() {
        io.execute(() -> applyState(Api.stateSafe()));
    }

    private void applyState(final JSONObject st) {
        ui.post(() -> {
            lastState = st;
            if (st == null) {
                headTitle.setText("движок не отвечает");
                headMeta.setText("открой Minis и запусти: tg serve");
                powerBtn.setText("ДВИЖОК ВЫКЛЮЧЕН");
                powerBtn.setBackgroundColor(CARD);
                powerBtn.setTextColor(DIM);
                logView.setText("API 127.0.0.1:8790 недоступен.\nНужно поднять движок в песочнице (tg serve).");
                return;
            }
            JSONObject c = st.optJSONObject("chat");
            JSONObject d = st.optJSONObject("demon");
            if (c == null) {
                headTitle.setText("чат не выбран");
                headMeta.setText("напиши название или @ник в поиске");
                canDelete = false; running = false; mode = "new";
            } else {
                headTitle.setText(c.optString("title", "?"));
                canDelete = c.optBoolean("can_delete", false);
                running = c.optBoolean("running", false);
                mode = c.optString("mode", "new");
                String role = c.optString("role", "?");
                headMeta.setText(c.optString("type", "?") + " · " + role
                        + " · удаление: " + (canDelete ? "ЕСТЬ" : "НЕТ")
                        + " · людей " + c.optInt("members", 0));
                // members — массив, optInt не подходит
                JSONArray ms = c.optJSONArray("members");
                JSONArray ts = c.optJSONArray("targets");
                int mn = ms == null ? 0 : ms.length();
                int tn = ts == null ? 0 : ts.length();
                headMeta.setText(c.optString("type", "?") + " · " + role
                        + " · удаление: " + (canDelete ? "ЕСТЬ" : "НЕТ") + " · людей " + mn);
                peopleBtn.setText("ЛЮДИ: " + tn);
                peopleBtn.setTextColor(tn > 0 ? GREEN : TXT);
            }
            if ("all".equals(mode)) {
                modeBtn.setText("РЕЖИМ: ВСЁ");
                modeBtn.setTextColor(RED);
            } else {
                modeBtn.setText("РЕЖИМ: НОВЫЕ");
                modeBtn.setTextColor(TXT);
            }
            if (!canDelete) {
                powerBtn.setText("НЕТ ПРАВ АДМИНА");
                powerBtn.setBackgroundColor(CARD);
                powerBtn.setTextColor(DIM);
            } else if (running) {
                powerBtn.setText("■  ОСТАНОВИТЬ");
                powerBtn.setBackgroundColor(RED);
                powerBtn.setTextColor(Color.WHITE);
            } else {
                powerBtn.setText("▶  НАЧАТЬ (" + ("all".equals(mode) ? "ВСЁ" : "НОВЫЕ") + ")");
                powerBtn.setBackgroundColor(WHITE);
                powerBtn.setTextColor(Color.BLACK);
            }
            StringBuilder sb = new StringBuilder();
            if (d != null) {
                sb.append("демон: ").append(d.optBoolean("alive") ? "pid " + d.optLong("pid") : "НЕ РАБОТАЕТ")
                  .append(" · hb ").append(d.optString("heartbeat", "—"))
                  .append(" · удалено ").append(d.optInt("deleted", 0)).append('\n');
                JSONArray last = d.optJSONArray("last");
                if (last != null) for (int i = last.length() - 1; i >= 0; i--) {
                    JSONObject e = last.optJSONObject(i);
                    if (e == null) continue;
                    sb.append(e.optString("t", "")).append(" ").append(e.optString("why", ""))
                      .append(" n=").append(e.optInt("n", 0)).append(' ').append(e.optString("ids", ""))
                      .append(e.optString("err", "")).append('\n');
                }
                JSONArray er = d.optJSONArray("errors");
                if (er != null) for (int i = 0; i < er.length(); i++) {
                    JSONObject e = er.optJSONObject(i);
                    if (e != null) sb.append("ошибка: ").append(e.optString("e", "")).append('\n');
                }
            }
            JSONObject inv = st.optJSONObject("inventory_counts");
            if (inv != null) sb.append("база: каналы ").append(inv.optInt("channels", 0))
                    .append(" · группы ").append(inv.optInt("groups", 0))
                    .append(" · боты ").append(inv.optInt("bots", 0))
                    .append(" · личные ").append(inv.optInt("users", 0));
            logView.setText(sb.toString());
        });
    }

    // ---------- поиск ----------
    private final Handler searchH = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private void scheduleSearch(final String q) {
        if (pendingSearch != null) searchH.removeCallbacks(pendingSearch);
        pendingSearch = () -> io.execute(() -> {
            JSONArray hits = Api.search(q);
            post(() -> renderSearch(q, hits));
        });
        searchH.postDelayed(pendingSearch, 350);
    }

    private void renderSearch(final String q, final JSONArray hits) {
        results.removeAllViews();
        foundView.setText("");
        if (q.trim().isEmpty()) return;
        if (hits.length() == 0) {
            TextView t = tv("ничего не найдено по «" + q + "»", 12, DIM);
            t.setPadding(dp(14), dp(10), dp(14), dp(10));
            results.addView(t);
            return;
        }
        int n = Math.min(hits.length(), 12);
        for (int i = 0; i < n; i++) {
            final JSONObject h = hits.optJSONObject(i);
            if (h == null) continue;
            String kind = h.optString("kind", "?");
            String title = h.optString("title", "(без названия)");
            String un = h.optString("username", "");
            TextView row = tv(title + (un.length() > 0 ? "   @" + un : ""), 14, TXT);
            row.setTypeface(row.getTypeface(), Typeface.BOLD);
            row.setBackgroundColor(CARD);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView sub = tv(kind + " · id " + h.optString("id", "?") + " · непрочит. " + h.optInt("unread", 0), 10.5f, DIM);
            sub.setTypeface(Typeface.MONOSPACE);
            sub.setBackgroundColor(CARD);
            sub.setPadding(dp(14), 0, dp(14), dp(12));
            LinearLayout holder = new LinearLayout(this);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.setBackgroundColor(CARD);
            holder.addView(row);
            holder.addView(sub);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            results.addView(holder, lp);
            holder.setOnClickListener(v -> {
                row.setTextColor(GREEN);
                final String id = h.optString("id", "");
                io.execute(() -> {
                    JSONObject r = Api.postSimple("/api/open", jobj("q", id));
                    post(() -> {
                        results.removeAllViews();
                        search.setText("");
                        foundView.setText(r.optString("error", "").length() > 0
                                ? "не открылось: " + r.optString("error")
                                : "открыт чат — смотри кнопки ниже");
                        applyState(r.optBoolean("ok", false) ? r : Api.stateSafe());
                    });
                });
            });
        }
        if (hits.length() > n) {
            TextView more = tv("… ещё " + (hits.length() - n) + " — уточни запрос", 11, DIM);
            more.setPadding(dp(14), dp(8), dp(14), dp(8));
            results.addView(more);
        }
    }

    private void post(Runnable r) { ui.post(r); }

    private void toast(final String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }
}

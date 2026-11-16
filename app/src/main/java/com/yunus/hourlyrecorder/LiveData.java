package com.yunus.hourlyrecorder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Reads today's figures from the shared database (public read) and works out the latest hour's efficiency. */
final class LiveData {
    static final String[] LABEL = {"", "9–10", "10–11", "11–12", "12–1", "2–3", "3–4", "4–5", "5–6", "Late hours"};
    private static final String BASE = "https://firestore.googleapis.com/v1/projects/small-parts-gf/databases/(default)/documents/";

    boolean ok;
    String section = "Small Parts GF";
    String hourLabel = "";
    long pieces = -1;
    double eff = -1;
    int target = 70;
    long total;
    int hoursDone;
    String prevLine = "";

    private static JSONObject get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        try {
            if (c.getResponseCode() == 404) return null;
            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } finally {
            c.disconnect();
        }
    }

    private static double num(JSONObject v, double def) {
        if (v == null) return def;
        if (v.has("integerValue")) return Double.parseDouble(v.optString("integerValue"));
        if (v.has("doubleValue")) return v.optDouble("doubleValue", def);
        return def;
    }

    private static JSONObject fields(JSONObject doc) {
        return doc == null ? new JSONObject() : doc.optJSONObject("fields") == null ? new JSONObject() : doc.optJSONObject("fields");
    }

    private static String slug(String s) {
        String t = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return t.isEmpty() ? "section" : t;
    }

    static LiveData load() {
        LiveData d = new LiveData();
        try {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            JSONObject live = fields(get("live/main"));
            String date = live.has("date") ? live.getJSONObject("date").optString("stringValue") : "";
            if (date.equals(today) && live.has("section")) d.section = live.getJSONObject("section").optString("stringValue", d.section);

            JSONObject st = fields(get("settings/main"));
            double std = 2.67;
            if (st.has("sam")) std = num(st.getJSONObject("sam"), std);
            if (st.has("bottleneck")) d.target = (int) Math.round(num(st.getJSONObject("bottleneck"), 70));
            if (st.has("operations")) {
                JSONArray arr = st.getJSONObject("operations").optJSONObject("arrayValue") == null ? null
                        : st.getJSONObject("operations").getJSONObject("arrayValue").optJSONArray("values");
                if (arr != null && arr.length() > 0) {
                    double sum = 0;
                    boolean any = false;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i).getJSONObject("mapValue").optJSONObject("fields");
                        if (o == null || !o.has("sam") || !o.has("name")) continue;
                        double s = num(o.getJSONObject("sam"), 0);
                        if (s > 0) { sum += Math.round(s * 100) / 100.0; any = true; }
                    }
                    if (any) std = Math.round(sum * 100) / 100.0;
                }
            }

            JSONObject day = fields(get("days/" + today + "_" + slug(d.section)));
            double ops = day.has("ops") ? num(day.getJSONObject("ops"), 27) : 27;
            JSONObject hours = day.has("hours") ? day.getJSONObject("hours").optJSONObject("mapValue") : null;
            hours = hours == null ? null : hours.optJSONObject("fields");
            int last = 0, prev = 0;
            if (hours != null) {
                for (int k = 1; k <= 9; k++) {
                    JSONObject h = hours.optJSONObject(String.valueOf(k));
                    if (h == null) continue;
                    JSONObject hf = h.getJSONObject("mapValue").optJSONObject("fields");
                    if (hf == null || !hf.has("out")) continue;
                    d.total += (long) num(hf.getJSONObject("out"), 0);
                    d.hoursDone++;
                    prev = last;
                    last = k;
                }
                if (last > 0) {
                    d.pieces = (long) num(hours.getJSONObject(String.valueOf(last)).getJSONObject("mapValue").getJSONObject("fields").getJSONObject("out"), 0);
                    d.hourLabel = LABEL[last];
                    d.eff = ops > 0 ? d.pieces * std / (60 * ops) * 100 : -1;
                }
                if (prev > 0) {
                    long p = (long) num(hours.getJSONObject(String.valueOf(prev)).getJSONObject("mapValue").getJSONObject("fields").getJSONObject("out"), 0);
                    double pe = ops > 0 ? p * std / (60 * ops) * 100 : -1;
                    d.prevLine = LABEL[prev] + ": " + p + " pcs" + (pe >= 0 ? " · " + Math.round(pe) + "%" : "");
                }
            }
            d.ok = true;
        } catch (Exception e) {
            d.ok = false;
        }
        return d;
    }
}

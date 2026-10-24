package org.mingtiao.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

public final class Store {
  public final SharedPreferences prefs;

  public Store(Context c) {
    prefs = c.getSharedPreferences("local", Context.MODE_PRIVATE);
  }

  public Set<String> apps() {
    return new HashSet<>(prefs.getStringSet("apps", Collections.emptySet()));
  }

  public void allow(String pkg, boolean value) {
    Set<String> a = apps();
    if (value) a.add(pkg);
    else a.remove(pkg);
    prefs.edit().putStringSet("apps", a).apply();
  }

  public List<Rule> rules() {
    try {
      return parse(prefs.getString("rules", "{\"version\":1,\"rules\":[]}"));
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  public static List<Rule> parse(String input) throws JSONException {
    if (input.length() > 262144) throw new IllegalArgumentException("规则文件超过 256 KiB");
    JSONObject doc = new JSONObject(input);
    if (doc.getInt("version") != 1) throw new IllegalArgumentException("不支持的规则版本");
    JSONArray array = doc.getJSONArray("rules");
    if (array.length() > 200) throw new IllegalArgumentException("最多 200 条规则");
    List<Rule> out = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      JSONObject o = array.getJSONObject(i);
      Rule r = new Rule();
      r.name = o.getString("name");
      r.pkg = o.getString("package");
      r.activity = o.optString("activity");
      r.trigger = o.optString("trigger");
      r.target = o.optString("target");
      r.action = o.optString("action", "click");
      r.enabled = o.optBoolean("enabled", true);
      r.popup = o.optBoolean("popup", false);
      r.x = o.optDouble("x", 0.9);
      r.y = o.optDouble("y", 0.08);
      r.windowSeconds = o.optInt("windowSeconds", 8);
      r.validate();
      out.add(r);
    }
    return out;
  }

  public static String encode(List<Rule> rules) throws JSONException {
    JSONArray a = new JSONArray();
    for (Rule r : rules) {
      r.validate();
      JSONObject o = new JSONObject();
      o.put("name", r.name)
          .put("package", r.pkg)
          .put("activity", r.activity)
          .put("trigger", r.trigger)
          .put("target", r.target)
          .put("action", r.action)
          .put("enabled", r.enabled)
          .put("popup", r.popup)
          .put("x", r.x)
          .put("y", r.y)
          .put("windowSeconds", r.windowSeconds);
      a.put(o);
    }
    return new JSONObject().put("version", 1).put("rules", a).toString(2);
  }

  public void save(List<Rule> rules) throws JSONException {
    prefs.edit().putString("rules", encode(rules)).apply();
  }

  public void record(String pkg, String rule, String result, boolean success) {
    try {
      JSONArray old = new JSONArray(prefs.getString("log", "[]"));
      JSONArray next = new JSONArray();
      next.put(
          new JSONObject()
              .put("time", System.currentTimeMillis())
              .put("package", pkg)
              .put("rule", rule)
              .put("result", result));
      for (int i = 0; i < Math.min(99, old.length()); i++) next.put(old.get(i));
      SharedPreferences.Editor e = prefs.edit().putString("log", next.toString());
      if (success) e.putLong("count", prefs.getLong("count", 0) + 1);
      e.apply();
    } catch (JSONException ignored) {
      /* bounded local metadata only */
    }
  }
}

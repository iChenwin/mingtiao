package org.mingtiao.app;

import java.util.Locale;
import java.util.regex.Pattern;

/** No scripts, remote commands or arbitrary regular expressions. */
public final class Rule {
  public String name = "自定义规则", pkg = "", activity = "", trigger = "", target = "";
  public String action = "click";
  public boolean enabled = true, popup = false;
  public double x = 0.9, y = 0.08;
  public int windowSeconds = 8;

  public void validate() {
    if (pkg.length() > 200 || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
      throw new IllegalArgumentException("规则需要完整应用包名");
    if (name.isBlank()
        || name.length() > 80
        || activity.length() > 200
        || trigger.length() > 120
        || target.length() > 120) throw new IllegalArgumentException("规则字段为空或过长");
    if (!action.equals("click") && !action.equals("tap") && !action.equals("back"))
      throw new IllegalArgumentException("仅支持 click / tap / back");
    if (windowSeconds < 1
        || windowSeconds > 15
        || !Double.isFinite(x)
        || !Double.isFinite(y)
        || x < 0
        || x > 1
        || y < 0
        || y > 1) throw new IllegalArgumentException("时间需在 1–15 秒，坐标需在 0–1");
    if (action.equals("click") && target.isBlank()) throw new IllegalArgumentException("点击规则需要目标");
    if ((popup || !action.equals("click")) && trigger.isBlank())
      throw new IllegalArgumentException("弹窗、坐标和返回规则必须有触发条件");
  }

  public static boolean matches(String selector, String text, String description, String id) {
    if (selector == null || selector.isEmpty()) return false;
    if (selector.startsWith("id:")) return selector.substring(3).equals(id);
    if (selector.startsWith("contains:")) {
      String value = selector.substring(9);
      return !value.isEmpty() && (safe(text).contains(value) || safe(description).contains(value));
    }
    String value = selector.startsWith("text:") ? selector.substring(5) : selector;
    return !value.isEmpty() && (value.equals(text) || value.equals(description));
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static final Pattern SKIP =
      Pattern.compile(
          "(?:跳过(?:广告)?|skip(?:\\s+ad)?)(?:\\s*[（(]?\\d{1,2}\\s*[s秒]?[）)]?)?|\\d{1,2}\\s*[s秒]?\\s*(?:跳过|skip)",
          Pattern.CASE_INSENSITIVE);

  public static boolean isSkip(String s) {
    return s != null
        && s.length() <= 24
        && SKIP.matcher(s.trim().toLowerCase(Locale.ROOT)).matches();
  }
}

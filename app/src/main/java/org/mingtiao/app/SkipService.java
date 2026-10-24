package org.mingtiao.app;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.*;
import java.util.*;

public final class SkipService extends AccessibilityService
    implements SharedPreferences.OnSharedPreferenceChangeListener {
  private Store store;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable rescan =
      new Runnable() {
        @Override
        public void run() {
          if (store == null
              || foreground.isEmpty()
              || actions >= 3
              || !store.prefs.getBoolean("running", false)
              || !store.apps().contains(foreground)) return;
          long elapsed = SystemClock.elapsedRealtime() - started;
          boolean livePopup = false;
          for (Rule r : store.rules())
            if (r.enabled && r.popup && r.pkg.equals(foreground)) livePopup = true;
          if (elapsed > 15000 && !livePopup) return;
          AccessibilityEvent event =
              AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
          event.setPackageName(foreground);
          try {
            onAccessibilityEvent(event);
          } finally {
            event.recycle();
          }
          handler.postDelayed(this, 400);
        }
      };
  private String foreground = "", activity = "";
  private long started, lastScan, lastAction;
  private int actions;
  private boolean pending;
  private final Set<String> fired = new HashSet<>();

  @Override
  protected void onServiceConnected() {
    store = new Store(this);
    store.prefs.registerOnSharedPreferenceChangeListener(this);
    reset();
  }

  private void reset() {
    handler.removeCallbacks(rescan);
    foreground = "";
    activity = "";
    started = 0;
    actions = 0;
    fired.clear();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences p, String key) {
    if (key.equals("running") || key.equals("apps") || key.equals("rules") || key.equals("preview"))
      reset();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (store == null || event.getPackageName() == null) return;
    String pkg = event.getPackageName().toString();
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && !pkg.equals(foreground)) {
      foreground = pkg;
      activity = "";
      started = SystemClock.elapsedRealtime();
      actions = 0;
      fired.clear();
      handler.removeCallbacks(rescan);
      handler.postDelayed(rescan, 400);
    }
    if (!store.prefs.getBoolean("running", false)
        || !store.apps().contains(pkg)
        || protectedPackage(pkg)
        || !pkg.equals(foreground)) return;
    KeyguardManager lock = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (lock.isKeyguardLocked()) {
      reset();
      return;
    }
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && event.getClassName() != null) {
      String cls = event.getClassName().toString();
      if (!cls.startsWith("android.widget.") && !cls.startsWith("android.view.")) activity = cls;
    }
    long now = SystemClock.elapsedRealtime();
    if (pending || now - lastScan < 180 || now - lastAction < 900 || actions >= 3) return;
    lastScan = now;
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return;
    List<AccessibilityNodeInfo> nodes = new ArrayList<>();
    try {
      if (root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) return;
      collect(root, nodes, 0);
      for (Rule r : store.rules()) {
        String key = r.name + "|" + r.action + "|" + r.trigger + "|" + r.target;
        if (!r.enabled
            || !r.pkg.equals(pkg)
            || fired.contains(key)
            || (!r.activity.isEmpty() && !r.activity.equals(activity))) continue;
        if (!r.popup && now - started > r.windowSeconds * 1000L) continue;
        if (!r.trigger.isEmpty() && find(nodes, r.trigger) == null) continue;
        AccessibilityNodeInfo target = r.action.equals("click") ? find(nodes, r.target) : null;
        if (r.action.equals("click") && target == null) continue;
        execute(pkg, key, r.name, r.action, target, r.x, r.y, root);
        return;
      }
      if (now - started <= 8000 && !fired.contains("builtin")) {
        for (AccessibilityNodeInfo node : nodes) {
          if (usable(node)
              && (Rule.isSkip(str(node.getText()))
                  || Rule.isSkip(str(node.getContentDescription())))) {
            execute(pkg, "builtin", "内置跳过", "click", node, 0, 0, root);
            return;
          }
        }
      }
    } catch (IllegalStateException ignored) {
      /* window disappeared */
    } finally {
      for (AccessibilityNodeInfo n : nodes) if (n != root) n.recycle();
      root.recycle();
    }
  }

  public static boolean protectedPackage(String pkg) {
    return pkg.equals("org.mingtiao.app")
        || pkg.equals("android")
        || pkg.startsWith("com.android.")
        || pkg.startsWith("com.google.android.permissioncontroller")
        || pkg.contains(".permissioncontroller")
        || pkg.contains(".packageinstaller");
  }

  private String str(CharSequence s) {
    return s == null ? "" : s.toString();
  }

  private boolean usable(AccessibilityNodeInfo n) {
    return n.isVisibleToUser() && n.isEnabled() && !n.isPassword() && !n.isEditable();
  }

  private void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int depth) {
    if (out.size() >= 400 || depth > 24) {
      if (depth > 0) n.recycle();
      return;
    }
    out.add(n);
    if (n.isPassword() || n.isEditable()) return;
    for (int i = 0; i < Math.min(n.getChildCount(), 80) && out.size() < 400; i++) {
      AccessibilityNodeInfo c = n.getChild(i);
      if (c != null) collect(c, out, depth + 1);
    }
  }

  private AccessibilityNodeInfo find(List<AccessibilityNodeInfo> nodes, String selector) {
    for (AccessibilityNodeInfo n : nodes)
      if (usable(n)
          && Rule.matches(
              selector,
              str(n.getText()),
              str(n.getContentDescription()),
              n.getViewIdResourceName())) return n;
    return null;
  }

  private boolean click(AccessibilityNodeInfo n) {
    AccessibilityNodeInfo current = n;
    try {
      for (int i = 0; i < 3 && current != null; i++) {
        if (!usable(current)) return false;
        if (current.isClickable()) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        AccessibilityNodeInfo parent = current.getParent();
        if (current != n) current.recycle();
        current = parent;
      }
      return false;
    } finally {
      if (current != null && current != n) current.recycle();
    }
  }

  private void execute(
      String pkg,
      String key,
      String label,
      String action,
      AccessibilityNodeInfo node,
      double x,
      double y,
      AccessibilityNodeInfo root) {
    lastAction = SystemClock.elapsedRealtime();
    if (store.prefs.getBoolean("preview", true)) {
      fired.add(key);
      store.record(pkg, label, "预览命中 · 未操作", false);
      return;
    }
    // Never store nodes or schedule delayed clicks. Each action uses the current foreground root.
    actions++;
    if (action.equals("tap")) {
      Rect bounds = new Rect();
      root.getBoundsInScreen(bounds);
      if (bounds.isEmpty()) return;
      Path path = new Path();
      path.moveTo(
          bounds.left + (float) (x * (bounds.width() - 1)),
          bounds.top + (float) (y * (bounds.height() - 1)));
      pending = true;
      boolean accepted =
          dispatchGesture(
              new GestureDescription.Builder()
                  .addStroke(new GestureDescription.StrokeDescription(path, 0, 40))
                  .build(),
              new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription g) {
                  pending = false;
                  fired.add(key);
                  store.record(pkg, label, "手势完成（不代表广告已关闭）", true);
                }

                @Override
                public void onCancelled(GestureDescription g) {
                  pending = false;
                  store.record(pkg, label, "手势取消", false);
                }
              },
              null);
      if (!accepted) {
        pending = false;
        store.record(pkg, label, "手势被系统拒绝", false);
      }
    } else {
      boolean ok = action.equals("back") ? performGlobalAction(GLOBAL_ACTION_BACK) : click(node);
      if (ok) fired.add(key);
      store.record(pkg, label, ok ? "操作被系统接受（待观察结果）" : "操作失败", ok);
    }
  }

  @Override
  public void onInterrupt() {
    reset();
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacks(rescan);
    if (store != null) store.prefs.unregisterOnSharedPreferenceChangeListener(this);
    super.onDestroy();
  }
}

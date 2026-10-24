package org.mingtiao.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private Store store;
  private LinearLayout page, body;
  private String section = "首页";

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new Store(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    render();
  }

  private int dp(int n) {
    return (int) (getResources().getDisplayMetrics().density * n);
  }

  private TextView text(String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(Color.rgb(28, 47, 42));
    v.setPadding(0, dp(8), 0, dp(8));
    body.addView(v);
    return v;
  }

  private void title(String value) {
    text(value, 28).setTypeface(null, Typeface.BOLD);
  }

  private void button(String label, Runnable click) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    body.addView(b, new LinearLayout.LayoutParams(-1, -2));
    b.setOnClickListener(v -> click.run());
  }

  private void toggle(String name, boolean checked, java.util.function.Consumer<Boolean> change) {
    Switch s = new Switch(this);
    s.setText(name);
    s.setTextSize(16);
    s.setPadding(0, dp(12), 0, dp(12));
    s.setChecked(checked);
    body.addView(s, new LinearLayout.LayoutParams(-1, -2));
    s.setOnCheckedChangeListener((b, c) -> change.accept(c));
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private void render() {
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(20), 0, dp(20), 0);
    page.setBackgroundColor(Color.rgb(245, 247, 244));
    page.setFitsSystemWindows(true);
    setContentView(page);
    LinearLayout nav = new LinearLayout(this);
    page.addView(nav);
    for (String name : new String[] {"首页", "应用", "规则", "记录"}) {
      Button b = new Button(this);
      b.setText(name);
      nav.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
      b.setOnClickListener(
          v -> {
            section = name;
            render();
          });
    }
    ScrollView scroll = new ScrollView(this);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(0, dp(10), 0, dp(24));
    scroll.addView(body);
    switch (section) {
      case "应用":
        apps();
        break;
      case "规则":
        rules();
        break;
      case "记录":
        logs();
        break;
      default:
        home();
    }
  }

  private boolean serviceEnabled() {
    android.view.accessibility.AccessibilityManager m =
        (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
    for (android.accessibilityservice.AccessibilityServiceInfo i :
        m.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
      if (i.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) return true;
    return false;
  }

  private void home() {
    title("明跳");
    text("让跳过广告这件事，清楚可见。", 16);
    text(
        "无障碍服务："
            + (serviceEnabled() ? "已启用" : "未启用")
            + "\n授权应用："
            + store.apps().size()
            + " 个\n系统接受的操作："
            + store.prefs.getLong("count", 0)
            + " 次",
        18);
    toggle(
        "自动跳过总开关",
        store.prefs.getBoolean("running", false),
        on -> {
          if (on && !store.prefs.getBoolean("consent", false)) {
            consent();
            render();
          } else store.prefs.edit().putBoolean("running", on).apply();
        });
    toggle(
        "预览模式 · 命中后只记记录",
        store.prefs.getBoolean("preview", true),
        on -> store.prefs.edit().putBoolean("preview", on).apply());
    button("了解权限并打开无障碍设置", () -> consent());
    button(
        "立即暂停",
        () -> {
          store.prefs.edit().putBoolean("running", false).apply();
          render();
        });
    text(
        "开始使用\n1. 阅读权限说明并启用无障碍服务\n2. 在「应用」中选择允许处理的应用\n3. 开启总开关，先用预览模式检查命中\n4. 确认后关闭预览模式，重新进入目标应用",
        16);
    button(
        "隐私与安全边界",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("权限不是沙箱")
                .setMessage(
                    "Android 无障碍能读取屏幕控件并代为点击，开源也不能消除平台授予的能力。\n\n"
                        + "本应用不申请"
                        + " INTERNET、不截图、不读取输入框、不记录界面文本；仅对已授权应用读取当前控件树；其他应用事件只用于识别前台切换。系统设置、权限安装界面和本应用不执行规则。\n\n"
                        + "每次进入应用，内置匹配仅运行 8 秒；每次前台会话最多 3 次操作，间隔至少"
                        + " 900ms。自定义弹窗规则可在会话内持续匹配，也受总上限约束。日志仅本地保存最近 100"
                        + " 条时间、包名、规则名和结果，统计并不证明广告真正关闭。\n\n"
                        + "不要求 Root、设备管理、VPN、悬浮窗或后台保活权限。请仅导入自己审阅过的规则。首次侧载若受系统限制，请按系统设置手动授权；本应用不自动处理权限弹窗。")
                .setPositiveButton("明白", null)
                .show());
    text("v0.1.0 · 独立开源实现 · Android 8+\n不包含李跳跳原代码、图标或 APK。", 13);
  }

  private void consent() {
    new AlertDialog.Builder(this)
        .setTitle("启用前请了解")
        .setMessage(
            "明跳使用无障碍读取你授权应用的界面控件，并点击跳过按钮。自定义规则也可执行坐标点击或返回。规则可能误点，你可以随时暂停。\n\n"
                + "无网络权限、无云端规则执行、无屏幕文本存储。预览模式默认开启。\n\n"
                + "点击「同意并打开设置」仅表示知情，仍需要你在 Android 系统设置中手动启用服务。")
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "同意并打开设置",
            (d, w) -> {
              store.prefs.edit().putBoolean("consent", true).apply();
              startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            })
        .show();
  }

  private void apps() {
    title("按应用授权");
    text("默认全部关闭。只有你启用的应用才会读取控件、匹配规则。", 15);
    EditText search = new EditText(this);
    search.setHint("搜索应用名或包名");
    body.addView(search);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    body.addView(list);
    List<ResolveInfo> all =
        getPackageManager()
            .queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
    all.sort(Comparator.comparing(i -> i.loadLabel(getPackageManager()).toString()));
    Runnable fill =
        () -> {
          list.removeAllViews();
          Set<String> seen = new HashSet<>();
          String q = search.getText().toString().toLowerCase(Locale.ROOT);
          for (ResolveInfo info : all) {
            String pkg = info.activityInfo.packageName;
            String label = info.loadLabel(getPackageManager()).toString();
            if (!seen.add(pkg)
                || SkipService.protectedPackage(pkg)
                || !(label + pkg).toLowerCase(Locale.ROOT).contains(q)) continue;
            Switch s = new Switch(this);
            s.setText(label + "\n" + pkg);
            s.setPadding(0, dp(10), 0, dp(10));
            s.setChecked(store.apps().contains(pkg));
            list.addView(s, new LinearLayout.LayoutParams(-1, -2));
            s.setOnCheckedChangeListener((b, on) -> store.allow(pkg, on));
          }
        };
    fill.run();
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            fill.run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
  }

  private void rules() {
    title("本地规则");
    text("内置：精确识别「跳过」「跳过广告」「Skip ad」及倒计时变体。自定义规则优先。\n导入规则不会自动授权应用。", 15);
    button("添加规则", () -> editRule(-1, new Rule()));
    button(
        "导入 JSON 文件",
        () -> {
          Intent i =
              new Intent(Intent.ACTION_OPEN_DOCUMENT)
                  .setType("*/*")
                  .addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 10);
        });
    button(
        "导出 JSON 文件",
        () -> {
          try {
            Store.encode(store.rules());
            Intent i =
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "mingtiao-rules.json");
            startActivityForResult(i, 11);
          } catch (Exception e) {
            toast(e.getMessage());
          }
        });
    List<Rule> rs = store.rules();
    for (int i = 0; i < rs.size(); i++) {
      final int index = i;
      Rule r = rs.get(i);
      toggle(
          r.name + "\n" + r.pkg + " · " + r.action + (r.popup ? " · 弹窗" : " · 开屏"),
          r.enabled,
          on -> {
            try {
              List<Rule> fresh = store.rules();
              fresh.get(index).enabled = on;
              store.save(fresh);
            } catch (Exception e) {
              toast(e.getMessage());
            }
          });
      button("编辑 / 删除：" + r.name, () -> editRule(index, r));
    }
  }

  private EditText field(LinearLayout box, String hint, String value) {
    TextView label = new TextView(this);
    label.setText(hint);
    box.addView(label);
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setText(value);
    box.addView(e);
    return e;
  }

  private void editRule(int index, Rule r) {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20), dp(10), dp(20), dp(10));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    EditText name = field(box, "规则名", r.name),
        pkg = field(box, "应用包名", r.pkg),
        activity = field(box, "Activity 完整类名（可留空）", r.activity),
        trigger = field(box, "触发条件（text:文字 / id:完整资源ID / contains:文字）", r.trigger),
        target = field(box, "点击目标（同上语法）", r.target),
        action = field(box, "动作：click 控件 / tap 坐标 / back 返回", r.action),
        x = field(box, "X 比例 0–1（tap 使用）", String.valueOf(r.x)),
        y = field(box, "Y 比例 0–1（tap 使用）", String.valueOf(r.y)),
        window = field(box, "开屏检测秒数 1–15", String.valueOf(r.windowSeconds));
    CheckBox popup = new CheckBox(this);
    popup.setText("弹窗规则（不限开屏时段，需触发条件）");
    popup.setChecked(r.popup);
    box.addView(popup);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(index < 0 ? "添加规则" : "编辑规则")
            .setView(scroll)
            .setNegativeButton("取消", null)
            .setNeutralButton(
                index < 0 ? "规则说明" : "删除",
                (d, w) -> {
                  if (index >= 0)
                    new AlertDialog.Builder(this)
                        .setMessage("删除此规则？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton(
                            "删除",
                            (a, b) -> {
                              try {
                                List<Rule> rs = store.rules();
                                rs.remove(index);
                                store.save(rs);
                                render();
                              } catch (Exception e) {
                                toast(e.getMessage());
                              }
                            })
                        .show();
                  else toast("使用完整资源ID；无需正则表达式。坐标与返回动作必须有可见触发条件。");
                })
            .setPositiveButton("保存", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    b -> {
                      try {
                        Rule n = new Rule();
                        n.name = name.getText().toString().trim();
                        n.pkg = pkg.getText().toString().trim();
                        n.activity = activity.getText().toString().trim();
                        n.trigger = trigger.getText().toString().trim();
                        n.target = target.getText().toString().trim();
                        n.action = action.getText().toString().trim();
                        n.x = Double.parseDouble(x.getText().toString());
                        n.y = Double.parseDouble(y.getText().toString());
                        n.windowSeconds = Integer.parseInt(window.getText().toString());
                        n.popup = popup.isChecked();
                        n.enabled = r.enabled;
                        n.validate();
                        if (SkipService.protectedPackage(n.pkg))
                          throw new IllegalArgumentException("不能为系统界面或本应用配置规则");
                        List<Rule> rs = store.rules();
                        if (index < 0) {
                          if (rs.size() >= 200) throw new IllegalArgumentException("最多 200 条规则");
                          rs.add(n);
                        } else rs.set(index, n);
                        store.save(rs);
                        dialog.dismiss();
                        render();
                      } catch (Exception e) {
                        toast("无法保存：" + e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void logs() {
    title("操作记录");
    text("仅保存本地操作元数据，最多 100 条。预览命中不计入操作次数。", 15);
    button(
        "清空记录和统计",
        () -> {
          store.prefs.edit().remove("log").remove("count").apply();
          render();
        });
    try {
      JSONArray a = new JSONArray(store.prefs.getString("log", "[]"));
      if (a.length() == 0) text("暂无记录", 16);
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        text(
            DateFormat.getDateTimeInstance().format(new Date(o.getLong("time")))
                + "\n"
                + o.getString("package")
                + "\n"
                + o.getString("rule")
                + " · "
                + o.getString("result"),
            14);
      }
    } catch (JSONException e) {
      toast("记录读取失败");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    try {
      if (request == 10) {
        String input;
        try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
          if (stream == null) throw new IOException("无法打开文件");
          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          byte[] chunk = new byte[4096];
          int size;
          while ((size = stream.read(chunk)) != -1) {
            if (buffer.size() + size > 262144) throw new IOException("文件超过 256 KiB");
            buffer.write(chunk, 0, size);
          }
          input = buffer.toString(StandardCharsets.UTF_8.name());
        }
        List<Rule> incoming = Store.parse(input);
        for (Rule r : incoming)
          if (SkipService.protectedPackage(r.pkg)) throw new IllegalArgumentException("含受保护包名");
        new AlertDialog.Builder(this)
            .setTitle("导入 " + incoming.size() + " 条规则？")
            .setMessage("将替换现有自定义规则，并暂停自动操作、开启预览。规则不会自动授权应用。请先检查规则内容。")
            .setNegativeButton("取消", null)
            .setPositiveButton(
                "替换并预览",
                (d, w) -> {
                  try {
                    store
                        .prefs
                        .edit()
                        .putBoolean("running", false)
                        .putBoolean("preview", true)
                        .apply();
                    store.save(incoming);
                    render();
                  } catch (Exception e) {
                    toast(e.getMessage());
                  }
                })
            .show();
      } else if (request == 11) {
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          if (out == null) throw new IOException("无法写入文件");
          out.write(Store.encode(store.rules()).getBytes(StandardCharsets.UTF_8));
        }
        toast("已导出");
      }
    } catch (Exception e) {
      toast("操作失败：" + e.getMessage());
    }
  }
}

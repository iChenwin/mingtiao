package org.mingtiao.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

public class FixtureActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(64, 160, 64, 64);
    layout.setFitsSystemWindows(true);
    TextView label = new TextView(this);
    label.setText("这是广告");
    label.setTextSize(28);
    layout.addView(label);
    Button skip = new Button(this);
    skip.setText(
        getIntent().getStringExtra("label") == null
            ? "Skip ad"
            : getIntent().getStringExtra("label"));
    layout.addView(skip);
    skip.setOnClickListener(
        v -> {
          label.setText("SKIPPED");
          skip.setEnabled(false);
        });
    setContentView(layout);
  }
}

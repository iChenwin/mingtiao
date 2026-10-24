package org.mingtiao.app;

import static org.junit.Assert.*;

import org.junit.Test;

public class RuleTest {
  @Test
  public void matchesOnlyAdSkipLabels() {
    for (String s :
        new String[] {
          "跳过", "跳过广告", "跳过 5s", "跳过(3秒)", "5s 跳过", "Skip ad", "Skip 3", "skip ad (3s)"
        }) assertTrue(s, Rule.isSkip(s));
    for (String s :
        new String[] {"跳过付款验证", "跳过此步骤继续转账", "skip intro", "跳过登录", "不跳过", "立即购买", "", null})
      assertFalse(String.valueOf(s), Rule.isSkip(s));
  }

  @Test
  public void selectorsAreLiteralAndRequireExplicitContains() {
    assertTrue(Rule.matches("text:跳过", "跳过", null, null));
    assertFalse(Rule.matches("跳过", "跳过验证", null, null));
    assertTrue(Rule.matches("id:a.b:id/skip", null, null, "a.b:id/skip"));
    assertFalse(Rule.matches("id:skip", null, null, "a.b:id/skip"));
    assertTrue(Rule.matches("contains:广告", "关闭广告", null, null));
    assertFalse(Rule.matches("contains:", "任意文本", null, null));
    assertFalse(Rule.matches("", null, null, null));
  }

  private Rule base() {
    Rule r = new Rule();
    r.pkg = "org.example.demo";
    r.target = "text:跳过";
    return r;
  }

  @Test
  public void boundedValidRules() {
    base().validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void coordinateMustHaveTrigger() {
    Rule r = base();
    r.action = "tap";
    r.validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void popupMustHaveTrigger() {
    Rule r = base();
    r.popup = true;
    r.validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void cannotExecuteArbitraryCommands() {
    Rule r = base();
    r.action = "shell";
    r.validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void coordinatesCannotBeNaN() {
    Rule r = base();
    r.x = Double.NaN;
    r.validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void durationIsBounded() {
    Rule r = base();
    r.windowSeconds = 3600;
    r.validate();
  }
}

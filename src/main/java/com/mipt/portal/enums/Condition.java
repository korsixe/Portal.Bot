package com.mipt.portal.enums;

public enum Condition {
  USED("б/у"),
  NEW("Новое"),
  BROKEN("Не работает");

  private final String displayName;

  Condition(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public static void displayConditions() {
    System.out.println("Доступные состояния:");
    for (int i = 0; i < values().length; i++) {
      System.out.println((i + 1) + ". " + values()[i].getDisplayName());
    }
  }

  public static Condition getByNumber(int number) {
    if (number > 0 && number <= values().length) {
      return values()[number - 1];
    }
    return USED;
  }
}

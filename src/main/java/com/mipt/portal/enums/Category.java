package com.mipt.portal.enums;

public enum Category {
  ELECTRONICS("Электроника", 1),
  CLOTHING("Одежда и обувь", 2),
  HOME("Дом и сад", 3),
  BEAUTY("Красота и здоровье", 4),
  SPORTS("Спорт и отдых", 5),
  CHILDREN("Детские товары", 6),
  AUTO("Автотовары", 7),
  BOOKS("Книги и канцелярия", 8),
  HOBBY("Хобби и творчество", 9),
  PETS("Животные", 10),
  TUTORING("Репетиторство", 11),
  EDUCATION_SERVICES("Образовательные услуги", 12),
  HOUSEHOLD_SERVICES("Бытовые услуги", 13),
  REPAIR("Ремонт и строительство", 14),
  BEAUTY_SERVICES("Красота и уход", 15),
  TRANSPORT_SERVICES("Транспортные услуги", 16),
  IT_SERVICES("IT и компьютерные услуги", 17),
  EVENTS("Мероприятия и развлечения", 18),
  MEDICAL("Медицинские услуги", 19),
  LEGAL("Юридические услуги", 20),
  OTHER("Другое", 21);

  private final String displayName;
  private final int number;

  Category(String displayName, int number) {
    this.displayName = displayName;
    this.number = number;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getNumber() {
    return number;
  }

  public static Category fromDisplayName(String displayName) {
    for (Category category : values()) {
      if (category.displayName.equals(displayName)) {
        return category;
      }
    }
    System.err.println("Категория не найдена: " + displayName + ", используем OTHER");
    return OTHER;
  }

  public static Category getByNumber(int number) {
    if (number > 0 && number <= values().length) {
      return values()[number - 1];
    }
    return OTHER;
  }
}

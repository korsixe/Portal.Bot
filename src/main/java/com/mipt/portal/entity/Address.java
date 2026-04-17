package com.mipt.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Embeddable
public class Address {

  @Column(name = "full_address")
  private String fullAddress;

  @Column(name = "city")
  private String city;

  @Column(name = "street")
  private String street;

  @Column(name = "house_number")
  private String houseNumber;

  @Column(name = "building")
  private String building;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  public Address(String fullAddress) {
    this.fullAddress = fullAddress;
  }

  public String getYandexMapsUrl() {
    if (fullAddress != null && !fullAddress.isEmpty()) {
      return "https://maps.yandex.ru/?text=" + fullAddress.replace(" ", "+");
    }

    StringBuilder addressBuilder = new StringBuilder();
    if (city != null) addressBuilder.append(city);
    if (street != null) addressBuilder.append(", ").append(street);
    if (houseNumber != null) addressBuilder.append(", ").append(houseNumber);
    if (building != null && !building.isEmpty()) addressBuilder.append("/").append(building);

    String address = addressBuilder.toString();
    if (!address.isEmpty()) {
      return "https://maps.yandex.ru/?text=" + address.replace(" ", "+");
    }

    return "https://maps.yandex.ru/";
  }

  public String getYandexMapsUrlWithCoordinates() {
    if (latitude != null && longitude != null) {
      return String.format("https://maps.yandex.ru/?pt=%f,%f&z=17", longitude, latitude);
    }
    return getYandexMapsUrl();
  }

  public String getFormattedAddress() {
    if (fullAddress != null && !fullAddress.isEmpty()) {
      return fullAddress;
    }

    StringBuilder formatted = new StringBuilder();
    if (city != null) formatted.append(city);
    if (street != null) formatted.append(", ул. ").append(street);
    if (houseNumber != null) formatted.append(", д. ").append(houseNumber);
    if (building != null && !building.isEmpty()) formatted.append("/").append(building);

    return formatted.toString();
  }
}

package com.mipt.portal.enums;

public enum Role {
    USER("ROLE_USER", "Обычный пользователь"),
    MODERATOR("ROLE_MODERATOR", "Модератор"),
    ADMIN("ROLE_ADMIN", "Администратор");

    private final String authority;
    private final String displayName;

    Role(String authority, String displayName) {
        this.authority = authority;
        this.displayName = displayName;
    }

    public String getAuthority() {
        return authority;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

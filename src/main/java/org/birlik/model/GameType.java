package org.birlik.model;

public enum GameType {
    BATAK_IHALELI("İhaleli Batak", "4 Kişilik İhaleli Batak Oyunu"),
    BATAK_KOZ_MACA("Koz Maça Batak", "Maça Koz İhalesiz Batak"),
    KING("King (Gelecekte)", "Klasik King Iskambil Oyunu"),
    OKEY("Okey (Gelecekte)", "Klasik 4'lü Okey Oyunu");

    private final String title;
    private final String description;

    GameType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}

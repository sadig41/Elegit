package elegit;

import java.util.ArrayList;

/**
 * Created by dmusican on 3/22/16.
 */
public enum AuthMethod {
    HTTP(0, "HTTP"),
    HTTPS(1, "HTTPS"),
    SSH(2, "SSH"),
    NONE(3, "NONE");

    private final int enumValue;
    private final String enumString;

    AuthMethod(int enumValue, String enumString) {
        this.enumValue = enumValue;
        this.enumString = enumString;
    }

    public int getEnumValue() {
        return enumValue;
    }

    public static AuthMethod getEnumFromValue(int value) {
        for (AuthMethod authMethod : AuthMethod.values()) {
            if (authMethod.enumValue == value)
                return authMethod;
        }
        throw new RuntimeException("استخدمت قيمة فاسدة لانشاء منهج التحقق AuthMethod.");
    }

    public static ArrayList<String> getStrings() {
        ArrayList<String> strings = new ArrayList<>();
        for (AuthMethod authMethod : AuthMethod.values()) {
            strings.add(authMethod.enumString);
        }
        return strings;
    }

    public static AuthMethod getEnumFromString(String string) {
        for (AuthMethod authMethod : AuthMethod.values()) {
            if (authMethod.enumString.equals(string))
                return authMethod;
        }
        throw new RuntimeException("استخدمت قيمة فاسدة لانشاء منهج التحقق AuthMethod.");
    }


}

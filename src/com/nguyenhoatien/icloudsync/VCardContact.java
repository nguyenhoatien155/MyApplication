package com.nguyenhoatien.icloudsync;

import java.util.ArrayList;
import java.util.List;

public class VCardContact {

    public static class Typed {
        public String value;
        public String type;

        public Typed(String value, String type) {
            this.value = value;
            this.type = type;
        }
    }

    public String uid;
    public String displayName;
    public String given;
    public String family;
    public String middle;
    public String prefix;
    public String suffix;
    public final List<Typed> phones = new ArrayList<Typed>();
    public final List<Typed> emails = new ArrayList<Typed>();
}

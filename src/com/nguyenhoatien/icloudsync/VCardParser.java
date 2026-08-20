package com.nguyenhoatien.icloudsync;

import java.util.ArrayList;
import java.util.List;

public class VCardParser {

    private static final int STATE_NAME = 0;
    private static final int STATE_PARAMS = 1;
    private static final int STATE_PARAMS_IN_DQUOTE = 2;

    public static List<VCardContact> parse(String text) {
        List<VCardContact> out = new ArrayList<VCardContact>();
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        List<String> lines = unfold(text);

        VCardContact current = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.regionMatches(true, 0, "BEGIN:VCARD", 0, 11)) {
                current = new VCardContact();
                continue;
            }
            if (line.regionMatches(true, 0, "END:VCARD", 0, 9)) {
                if (current != null) {
                    out.add(current);
                    current = null;
                }
                continue;
            }
            if (current != null) {
                handleLine(current, line);
            }
        }
        return out;
    }

    private static List<String> unfold(String text) {
        List<String> out = new ArrayList<String>();
        StringBuilder pending = null;

        int i = 0;
        int n = text.length();
        while (i < n) {
            int end = i;
            while (end < n && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
                end++;
            }
            String raw = text.substring(i, end);

            while (end < n && (text.charAt(end) == '\r' || text.charAt(end) == '\n')) {
                end++;
            }
            i = end;

            if (raw.length() == 0) {
                continue;
            }
            char first = raw.charAt(0);
            if (first == ' ' || first == '\t') {
                if (pending != null) {
                    pending.append(raw.substring(1));
                }
                continue;
            }
            if (pending != null) {
                out.add(pending.toString());
            }
            pending = new StringBuilder(raw);
        }
        if (pending != null) {
            out.add(pending.toString());
        }
        return out;
    }

    private static void handleLine(VCardContact contact, String line) {
        String name = null;
        List<String> types = new ArrayList<String>();
        String rawValue = null;

        int state = STATE_NAME;
        int mark = 0;
        int n = line.length();

        for (int i = 0; i < n; i++) {
            char ch = line.charAt(i);

            if (state == STATE_NAME) {
                if (ch == ':') {
                    name = line.substring(mark, i);
                    rawValue = i < n - 1 ? line.substring(i + 1) : "";
                    break;
                } else if (ch == '.') {
                    mark = i + 1;
                } else if (ch == ';') {
                    name = line.substring(mark, i);
                    mark = i + 1;
                    state = STATE_PARAMS;
                }
            } else if (state == STATE_PARAMS) {
                if (ch == '"') {
                    state = STATE_PARAMS_IN_DQUOTE;
                } else if (ch == ';') {
                    readTypes(line.substring(mark, i), types);
                    mark = i + 1;
                } else if (ch == ':') {
                    readTypes(line.substring(mark, i), types);
                    rawValue = i < n - 1 ? line.substring(i + 1) : "";
                    break;
                }
            } else if (state == STATE_PARAMS_IN_DQUOTE) {
                if (ch == '"') {
                    state = STATE_PARAMS;
                }
            }
        }

        if (name == null || rawValue == null) {
            return;
        }
        store(contact, name.toUpperCase(), pickType(types), rawValue);
    }

    private static void readTypes(String param, List<String> out) {
        int eq = param.indexOf('=');
        if (eq < 0) {
            if (param.length() > 0) {
                out.add(stripQuotes(param));
            }
            return;
        }
        if (!param.regionMatches(true, 0, "TYPE", 0, 4)) {
            return;
        }
        String value = param.substring(eq + 1);
        int mark = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == ',') {
                String t = stripQuotes(value.substring(mark, i));
                if (t.length() > 0) {
                    out.add(t);
                }
                mark = i + 1;
            }
        }
    }

    private static String pickType(List<String> types) {
        String fallback = null;
        for (int i = 0; i < types.size(); i++) {
            String t = types.get(i);
            if (t.equalsIgnoreCase("pref") || t.equalsIgnoreCase("VOICE")
                    || t.equalsIgnoreCase("INTERNET")) {
                continue;
            }
            if (fallback == null) {
                fallback = t;
            }
        }
        return fallback;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static void store(VCardContact c, String name, String type, String rawValue) {
        if (name.equals("UID")) {
            c.uid = unescape(rawValue);
        } else if (name.equals("FN")) {
            c.displayName = unescape(rawValue);
        } else if (name.equals("N")) {
            List<String> parts = splitStructured(rawValue);
            c.family = get(parts, 0);
            c.given = get(parts, 1);
            c.middle = get(parts, 2);
            c.prefix = get(parts, 3);
            c.suffix = get(parts, 4);
        } else if (name.equals("TEL")) {
            String v = unescape(rawValue);
            if (v.length() > 0) {
                c.phones.add(new VCardContact.Typed(v, type));
            }
        } else if (name.equals("EMAIL")) {
            String v = unescape(rawValue);
            if (v.length() > 0) {
                c.emails.add(new VCardContact.Typed(v, type));
            }
        }
    }

    private static String get(List<String> parts, int i) {
        if (i >= parts.size()) {
            return null;
        }
        String s = parts.get(i);
        return s.length() > 0 ? s : null;
    }

    private static List<String> splitStructured(String value) {
        List<String> out = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        int n = value.length();

        for (int i = 0; i < n; i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i < n - 1) {
                sb.append(unescapeChar(value.charAt(++i)));
            } else if (ch == ';') {
                out.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(ch);
            }
        }
        out.add(sb.toString());
        return out;
    }

    private static String unescape(String text) {
        int n = text.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (ch == '\\' && i < n - 1) {
                sb.append(unescapeChar(text.charAt(++i)));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String unescapeChar(char ch) {
        if (ch == 'n' || ch == 'N') {
            return "\n";
        }
        return String.valueOf(ch);
    }
}

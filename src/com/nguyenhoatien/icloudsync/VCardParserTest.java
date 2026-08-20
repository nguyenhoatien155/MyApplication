package com.nguyenhoatien.icloudsync;

import java.util.List;

public class VCardParserTest {

    static int pass = 0;
    static int fail = 0;

    public static void main(String[] args) {
        run("Basic", VCardParserTest::testBasic);
        run("Folding", VCardParserTest::testFolding);
        run("EscapedSemicolonInName", VCardParserTest::testEscapedSemicolonInName);
        run("ItemGroupPrefix", VCardParserTest::testItemGroupPrefix);
        run("QuotedParamWithColon", VCardParserTest::testQuotedParamWithColon);
        run("TypeWithoutName", VCardParserTest::testTypeWithoutName);
        run("MultipleTypes", VCardParserTest::testMultipleTypes);
        run("EscapedNewline", VCardParserTest::testEscapedNewline);
        run("MultipleCards", VCardParserTest::testMultipleCards);
        run("EmptyValueSkipped", VCardParserTest::testEmptyValueSkipped);
        run("CrCrLf", VCardParserTest::testCrCrLf);
        run("LfOnly", VCardParserTest::testLfOnly);
        run("FoldedUrlNotCaptured", VCardParserTest::testFoldedUrlNotCaptured);
        run("VietnameseDiacritics", VCardParserTest::testVietnameseDiacritics);
        run("MultipleSameProperty", VCardParserTest::testMultipleSameProperty);
        run("UidShapes", VCardParserTest::testUidShapes);
        run("EmailShapes", VCardParserTest::testEmailShapes);
        run("NameEdgeCases", VCardParserTest::testNameEdgeCases);
        run("EscapeEdgeCases", VCardParserTest::testEscapeEdgeCases);
        run("FoldingInsideEscape", VCardParserTest::testFoldingInsideEscape);
        run("MalformedLines", VCardParserTest::testMalformedLines);
        run("CaseInsensitivity", VCardParserTest::testCaseInsensitivity);
        run("WhitespaceAndBom", VCardParserTest::testWhitespaceAndBom);
        run("MissingEndVcard", VCardParserTest::testMissingEndVcard);
        run("NestedAgentIgnored", VCardParserTest::testNestedAgentIgnored);
        run("PhotoIgnored", VCardParserTest::testPhotoIgnored);

        System.out.println("---");
        System.out.println("pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    static void testBasic() {
        VCardContact c = one("BEGIN:VCARD\r\nVERSION:3.0\r\n"
                + "UID:abc-123\r\nFN:Bob Smith\r\nN:Smith;Bob;;;\r\n"
                + "TEL;TYPE=CELL:+84901234567\r\nEMAIL;TYPE=WORK:bob@x.com\r\n"
                + "END:VCARD\r\n");
        eq("uid", "abc-123", c.uid);
        eq("fn", "Bob Smith", c.displayName);
        eq("family", "Smith", c.family);
        eq("given", "Bob", c.given);
        eq("middle null", null, c.middle);
        eq("phone", "+84901234567", c.phones.get(0).value);
        eq("phone type", "CELL", c.phones.get(0).type);
        eq("email", "bob@x.com", c.emails.get(0).value);
    }

    static void testFolding() {
        VCardContact c = one("BEGIN:VCARD\r\nFN:Very Long\r\n Name Here\r\nEND:VCARD\r\n");
        eq("folded strips one space", "Very LongName Here", c.displayName);

        VCardContact d = one("BEGIN:VCARD\r\nFN:Very Long\r\n  Name Here\r\nEND:VCARD\r\n");
        eq("folded keeps second space", "Very Long Name Here", d.displayName);

        VCardContact e = one("BEGIN:VCARD\r\nFN:Tab\r\n\tFolded\r\nEND:VCARD\r\n");
        eq("folded on tab", "TabFolded", e.displayName);

        VCardContact f = one("BEGIN:VCARD\r\nFN:A\r\n B\r\n C\r\nEND:VCARD\r\n");
        eq("folded three lines", "ABC", f.displayName);
    }

    static void testEscapedSemicolonInName() {
        VCardContact c = one("BEGIN:VCARD\r\nN:O\\;Brien;Bob;;;\r\nEND:VCARD\r\n");
        eq("escaped ; family", "O;Brien", c.family);
        eq("escaped ; given", "Bob", c.given);
    }

    static void testItemGroupPrefix() {
        VCardContact c = one("BEGIN:VCARD\r\nitem1.TEL;TYPE=HOME:+123\r\nEND:VCARD\r\n");
        eq("group stripped", "+123", c.phones.get(0).value);
        eq("group type", "HOME", c.phones.get(0).type);
    }

    static void testQuotedParamWithColon() {
        VCardContact c = one("BEGIN:VCARD\r\nTEL;TYPE=\"a:b\":+999\r\nEND:VCARD\r\n");
        eq("dquote colon value", "+999", c.phones.get(0).value);
        eq("dquote colon type", "a:b", c.phones.get(0).type);
    }

    static void testTypeWithoutName() {
        VCardContact c = one("BEGIN:VCARD\r\nTEL;CELL:+555\r\nEND:VCARD\r\n");
        eq("bare param value", "+555", c.phones.get(0).value);
        eq("bare param type", "CELL", c.phones.get(0).type);
    }

    static void testMultipleTypes() {
        VCardContact c = one("BEGIN:VCARD\r\nTEL;TYPE=WORK,VOICE:+777\r\nEND:VCARD\r\n");
        eq("comma types skip VOICE", "WORK", c.phones.get(0).type);

        // iCloud web export writes TYPE as repeated params, not comma-joined
        VCardContact d = one("BEGIN:VCARD\r\n"
                + "TEL;TYPE=CELL;TYPE=pref;TYPE=VOICE:+84 368 844 581\r\nEND:VCARD\r\n");
        eq("repeated TYPE keeps CELL", "CELL", d.phones.get(0).type);
        eq("repeated TYPE value", "+84 368 844 581", d.phones.get(0).value);

        VCardContact e = one("BEGIN:VCARD\r\nTEL;TYPE=pref;TYPE=VOICE:+1\r\nEND:VCARD\r\n");
        eq("all-noise types give null", null, e.phones.get(0).type);

        VCardContact f = one("BEGIN:VCARD\r\n"
                + "EMAIL;TYPE=INTERNET;TYPE=HOME:a@b.com\r\nEND:VCARD\r\n");
        eq("email skips INTERNET", "HOME", f.emails.get(0).type);
    }

    static void testLfOnly() {
        VCardContact c = one("BEGIN:VCARD\nFN:Lf Only\nTEL;TYPE=CELL:+1\nEND:VCARD\n");
        eq("lf only fn", "Lf Only", c.displayName);
        eq("lf only tel", "+1", c.phones.get(0).value);
    }

    static void testFoldedUrlNotCaptured() {
        VCardContact c = one("BEGIN:VCARD\nFN:X\n"
                + "item1.URL;TYPE=pref:https://a.com/#abc\n def\nitem1.X-ABLABEL:Passlink\n"
                + "END:VCARD\n");
        eq("unknown props ignored", "X", c.displayName);
        eq("no phantom phone", "0", String.valueOf(c.phones.size()));
    }

    static void testVietnameseDiacritics() {
        VCardContact c = one("BEGIN:VCARD\nN:Nguyễn;Trọng Khải;;;\n"
                + "FN:Trọng Khải Nguyễn\nEND:VCARD\n");
        eq("utf8 family", "Nguyễn", c.family);
        eq("utf8 given", "Trọng Khải", c.given);
    }

    static void testEscapedNewline() {
        VCardContact c = one("BEGIN:VCARD\r\nFN:Line1\\nLine2\r\nEND:VCARD\r\n");
        eq("escaped newline", "Line1\nLine2", c.displayName);
    }

    static void testMultipleCards() {
        List<VCardContact> l = VCardParser.parse(
                "BEGIN:VCARD\r\nFN:A\r\nEND:VCARD\r\nBEGIN:VCARD\r\nFN:B\r\nEND:VCARD\r\n");
        eq("card count", "2", String.valueOf(l.size()));
        eq("card 1", "A", l.get(0).displayName);
        eq("card 2", "B", l.get(1).displayName);
    }

    static void testEmptyValueSkipped() {
        VCardContact c = one("BEGIN:VCARD\r\nTEL;TYPE=CELL:\r\nFN:X\r\nEND:VCARD\r\n");
        eq("empty tel skipped", "0", String.valueOf(c.phones.size()));
        eq("fn after empty", "X", c.displayName);
    }

    static void testCrCrLf() {
        VCardContact c = one("BEGIN:VCARD\r\r\nFN:Weird\r\r\nEND:VCARD\r\r\n");
        eq("cr cr lf", "Weird", c.displayName);
    }

    // A real contact often carries several TEL/EMAIL lines; the file used for the
    // first pass happened to have at most one of each, so nothing covered this.
    static void testMultipleSameProperty() {
        VCardContact c = one("BEGIN:VCARD\n"
                + "FN:Multi\n"
                + "TEL;TYPE=CELL:+1\n"
                + "TEL;TYPE=HOME:+2\n"
                + "TEL;TYPE=WORK:+3\n"
                + "EMAIL;TYPE=HOME:a@x.com\n"
                + "EMAIL;TYPE=WORK:b@x.com\n"
                + "END:VCARD\n");
        eq("three phones", "3", String.valueOf(c.phones.size()));
        eq("phone 1", "+1", c.phones.get(0).value);
        eq("phone 2", "+2", c.phones.get(1).value);
        eq("phone 3", "+3", c.phones.get(2).value);
        eq("phone 2 type", "HOME", c.phones.get(1).type);
        eq("two emails", "2", String.valueOf(c.emails.size()));
        eq("email 2", "b@x.com", c.emails.get(1).value);
        eq("email 2 type", "WORK", c.emails.get(1).type);

        // item-grouped duplicates, the shape iCloud uses for labelled entries
        VCardContact d = one("BEGIN:VCARD\nFN:Grouped\n"
                + "item1.TEL;TYPE=pref:+10\nitem1.X-ABLABEL:Zalo\n"
                + "item2.TEL:+20\nitem2.X-ABLABEL:Viber\n"
                + "END:VCARD\n");
        eq("grouped phones kept", "2", String.valueOf(d.phones.size()));
        eq("grouped phone 1", "+10", d.phones.get(0).value);
        eq("grouped phone 2", "+20", d.phones.get(1).value);
    }

    // CardDAV vCards do carry UID even though the web export dropped it.
    static void testUidShapes() {
        eq("plain uid", "ABC-123",
                one("BEGIN:VCARD\nUID:ABC-123\nEND:VCARD\n").uid);
        eq("urn uid", "urn:uuid:8f1a-22",
                one("BEGIN:VCARD\nUID:urn:uuid:8f1a-22\nEND:VCARD\n").uid);
        eq("uid with param", "P-9",
                one("BEGIN:VCARD\nUID;VALUE=text:P-9\nEND:VCARD\n").uid);
        eq("missing uid stays null", null,
                one("BEGIN:VCARD\nFN:No Uid\nEND:VCARD\n").uid);
        eq("empty uid is empty not null", "",
                one("BEGIN:VCARD\nUID:\nEND:VCARD\n").uid);
    }

    static void testEmailShapes() {
        VCardContact c = one("BEGIN:VCARD\nEMAIL;TYPE=INTERNET;TYPE=pref:x@y.z\nEND:VCARD\n");
        eq("email all-noise type null", null, c.emails.get(0).type);
        eq("email value kept", "x@y.z", c.emails.get(0).value);

        eq("bare email no params", "q@r.s",
                one("BEGIN:VCARD\nEMAIL:q@r.s\nEND:VCARD\n").emails.get(0).value);

        VCardContact d = one("BEGIN:VCARD\nEMAIL;TYPE=WORK:plus+tag@sub.domain.co.uk\nEND:VCARD\n");
        eq("email with plus and subdomain", "plus+tag@sub.domain.co.uk",
                d.emails.get(0).value);
    }

    static void testNameEdgeCases() {
        VCardContact c = one("BEGIN:VCARD\nN:Doe;John;Q;Dr.;Jr.\nEND:VCARD\n");
        eq("prefix", "Dr.", c.prefix);
        eq("suffix", "Jr.", c.suffix);
        eq("middle", "Q", c.middle);

        // fewer than five components must not throw
        VCardContact d = one("BEGIN:VCARD\nN:OnlyFamily\nEND:VCARD\n");
        eq("short N family", "OnlyFamily", d.family);
        eq("short N given null", null, d.given);
        eq("short N suffix null", null, d.suffix);

        VCardContact e = one("BEGIN:VCARD\nN:;;;;\nEND:VCARD\n");
        eq("all-empty N family null", null, e.family);

        // more components than expected: extras ignored, no crash
        VCardContact f = one("BEGIN:VCARD\nN:A;B;C;D;E;F;G\nEND:VCARD\n");
        eq("long N family", "A", f.family);
        eq("long N suffix", "E", f.suffix);

        eq("FN missing stays null", null,
                one("BEGIN:VCARD\nN:X;Y;;;\nEND:VCARD\n").displayName);
    }

    static void testEscapeEdgeCases() {
        eq("escaped comma", "a,b",
                one("BEGIN:VCARD\nFN:a\\,b\nEND:VCARD\n").displayName);
        eq("escaped backslash", "a\\b",
                one("BEGIN:VCARD\nFN:a\\\\b\nEND:VCARD\n").displayName);
        eq("uppercase escaped N", "a\nb",
                one("BEGIN:VCARD\nFN:a\\Nb\nEND:VCARD\n").displayName);
        eq("unknown escape drops slash", "aQb",
                one("BEGIN:VCARD\nFN:a\\Qb\nEND:VCARD\n").displayName);
        eq("trailing lone backslash kept", "ab\\",
                one("BEGIN:VCARD\nFN:ab\\\nEND:VCARD\n").displayName);

        // escaped backslash immediately before a semicolon must not shield it
        VCardContact c = one("BEGIN:VCARD\nN:a\\\\;b;;;\nEND:VCARD\n");
        eq("backslash then real ; family", "a\\", c.family);
        eq("backslash then real ; given", "b", c.given);

        eq("colon in value kept", "http://x.com/a:b",
                one("BEGIN:VCARD\nFN:http://x.com/a:b\nEND:VCARD\n").displayName);
    }

    // A fold can split anywhere, including between a backslash and its escaped char.
    static void testFoldingInsideEscape() {
        VCardContact c = one("BEGIN:VCARD\nFN:before\\\n nafter\nEND:VCARD\n");
        eq("fold splitting an escape", "before\nafter", c.displayName);

        VCardContact d = one("BEGIN:VCARD\nTEL;TYPE=CE\n LL:+42\nEND:VCARD\n");
        eq("fold inside param name", "CELL", d.phones.get(0).type);
        eq("fold inside param value", "+42", d.phones.get(0).value);
    }

    static void testMalformedLines() {
        // no colon at all: must be skipped, not crash, and not eat the next line
        VCardContact c = one("BEGIN:VCARD\nGARBAGE NO COLON\nFN:Survivor\nEND:VCARD\n");
        eq("line without colon skipped", "Survivor", c.displayName);

        VCardContact d = one("BEGIN:VCARD\n:leading colon\nFN:Ok\nEND:VCARD\n");
        eq("empty property name skipped", "Ok", d.displayName);

        VCardContact e = one("BEGIN:VCARD\nTEL;TYPE=CELL\nFN:Ok2\nEND:VCARD\n");
        eq("params but no colon skipped", "Ok2", e.displayName);
        eq("no phone from malformed", "0", String.valueOf(e.phones.size()));

        // unterminated dquote runs to end of line: no value, no crash
        VCardContact f = one("BEGIN:VCARD\nTEL;TYPE=\"open:+1\nFN:Ok3\nEND:VCARD\n");
        eq("dangling dquote does not crash", "Ok3", f.displayName);

        eq("empty input gives no cards", "0",
                String.valueOf(VCardParser.parse("").size()));
        eq("garbage only gives no cards", "0",
                String.valueOf(VCardParser.parse("hello\nworld\n").size()));
    }

    static void testCaseInsensitivity() {
        VCardContact c = one("begin:vcard\nfn:Lower\ntel;type=cell:+9\nend:vcard\n");
        eq("lowercase fn", "Lower", c.displayName);
        eq("lowercase tel", "+9", c.phones.get(0).value);

        VCardContact d = one("BEGIN:VCARD\nFn:Mixed\nTeL;TyPe=CeLl:+8\nEND:VCARD\n");
        eq("mixed case fn", "Mixed", d.displayName);
        eq("mixed case tel", "+8", d.phones.get(0).value);

        // lowercase noise types must still be filtered
        VCardContact e = one("BEGIN:VCARD\nTEL;TYPE=voice;TYPE=cell:+7\nEND:VCARD\n");
        eq("lowercase noise filtered", "cell", e.phones.get(0).type);
    }

    static void testWhitespaceAndBom() {
        VCardContact c = one("﻿BEGIN:VCARD\nFN:Bom\nEND:VCARD\n");
        eq("leading BOM tolerated", "Bom", c.displayName);

        VCardContact d = one("BEGIN:VCARD\n\n\nFN:Blanks\n\nEND:VCARD\n");
        eq("blank lines between props", "Blanks", d.displayName);

        VCardContact e = one("BEGIN:VCARD\nFN: Padded \nEND:VCARD\n");
        eq("value whitespace preserved", " Padded ", e.displayName);
    }

    static void testMissingEndVcard() {
        // truncated stream: nothing emitted rather than a half-built contact
        eq("missing END drops card", "0",
                String.valueOf(VCardParser.parse("BEGIN:VCARD\nFN:Truncated\n").size()));

        List<VCardContact> l = VCardParser.parse(
                "BEGIN:VCARD\nFN:First\nEND:VCARD\nBEGIN:VCARD\nFN:Second\n");
        eq("complete card before truncation kept", "1", String.valueOf(l.size()));
        eq("kept card is the complete one", "First", l.get(0).displayName);
    }

    // AGENT embeds a whole vCard inside a value; we must not treat it as a new card.
    static void testNestedAgentIgnored() {
        List<VCardContact> l = VCardParser.parse("BEGIN:VCARD\nFN:Outer\n"
                + "AGENT:BEGIN\\:VCARD\\nFN\\:Inner\\nEND\\:VCARD\\n\n"
                + "END:VCARD\n");
        eq("escaped nested agent stays one card", "1", String.valueOf(l.size()));
        eq("outer name intact", "Outer", l.get(0).displayName);
    }

    static void testPhotoIgnored() {
        VCardContact c = one("BEGIN:VCARD\nFN:Photo Guy\n"
                + "PHOTO;ENCODING=b;TYPE=JPEG:/9j/4AAQSkZJRgABAQ\n AAEAYABgAAD/2wBDAA\n"
                + "TEL;TYPE=CELL:+5\nEND:VCARD\n");
        eq("photo ignored, fn intact", "Photo Guy", c.displayName);
        eq("prop after folded photo still read", "+5", c.phones.get(0).value);
        eq("photo made no phone entry", "1", String.valueOf(c.phones.size()));
    }

    interface Case { void run(); }

    // Each case is isolated: an unexpected exception fails that case only,
    // so one broken shape does not hide results for every later test.
    static void run(String label, Case c) {
        try {
            c.run();
        } catch (RuntimeException e) {
            fail++;
            System.out.println("  FAIL " + label + " threw " + e);
        }
    }

    static VCardContact one(String s) {
        List<VCardContact> l = VCardParser.parse(s);
        if (l.size() != 1) {
            fail++;
            System.out.println("  FAIL expected 1 card, got " + l.size()
                    + " for: " + show(s));
            return new VCardContact();
        }
        return l.get(0);
    }

    static void eq(String label, String want, String got) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (ok) {
            pass++;
            System.out.println("  ok   " + label);
        } else {
            fail++;
            System.out.println("  FAIL " + label + ": want=" + show(want) + " got=" + show(got));
        }
    }

    static String show(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\n", "\\n") + "\"";
    }
}

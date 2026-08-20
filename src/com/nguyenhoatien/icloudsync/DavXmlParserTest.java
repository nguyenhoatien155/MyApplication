package com.nguyenhoatien.icloudsync;

import java.util.List;

public class DavXmlParserTest {

    static int pass = 0;
    static int fail = 0;

    public static void main(String[] args) {
        run("Principal", DavXmlParserTest::testPrincipal);
        run("HomeSet", DavXmlParserTest::testHomeSet);
        run("AddressbookList", DavXmlParserTest::testAddressbookList);
        run("EtagListing", DavXmlParserTest::testEtagListing);
        run("MultigetData", DavXmlParserTest::testMultigetData);
        run("NoNamespacePrefix", DavXmlParserTest::testNoNamespacePrefix);
        run("WeakEtag", DavXmlParserTest::testWeakEtag);
        run("NotFoundStatus", DavXmlParserTest::testNotFoundStatus);
        run("EmptyAndMalformed", DavXmlParserTest::testEmptyAndMalformed);
        run("CdataAndEntities", DavXmlParserTest::testCdataAndEntities);
        run("ChunkedCharacters", DavXmlParserTest::testChunkedCharacters);

        System.out.println("---");
        System.out.println("pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    static void testPrincipal() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<multistatus xmlns=\"DAV:\">"
                + "<response><href>/</href><propstat><prop>"
                + "<current-user-principal><href>/1234567890/principal/</href>"
                + "</current-user-principal>"
                + "</prop><status>HTTP/1.1 200 OK</status></propstat></response>"
                + "</multistatus>";
        eq("principal href", "/1234567890/principal/",
                DavXmlParser.findCurrentUserPrincipal(xml));
    }

    static void testHomeSet() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
                + "<d:response><d:href>/1234567890/principal/</d:href><d:propstat><d:prop>"
                + "<c:addressbook-home-set><d:href>/1234567890/carddavhome/</d:href>"
                + "</c:addressbook-home-set>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        eq("home href", "/1234567890/carddavhome/", DavXmlParser.findAddressbookHome(xml));
    }

    static void testAddressbookList() throws Exception {
        String xml = "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
                + "<d:response><d:href>/1234567890/carddavhome/</d:href><d:propstat><d:prop>"
                + "<d:resourcetype><d:collection/></d:resourcetype>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "<d:response><d:href>/1234567890/carddavhome/card/</d:href><d:propstat><d:prop>"
                + "<d:resourcetype><d:collection/><c:addressbook/></d:resourcetype>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);
        eq("two responses", "2", String.valueOf(rs.size()));
        eq("home is not addressbook", "false", String.valueOf(rs.get(0).isAddressbook));
        eq("card is addressbook", "true", String.valueOf(rs.get(1).isAddressbook));
        eq("addressbook href", "/1234567890/carddavhome/card/", rs.get(1).href);
    }

    static void testEtagListing() throws Exception {
        String xml = "<d:multistatus xmlns:d=\"DAV:\">"
                + "<d:response><d:href>/c/card/a.vcf</d:href><d:propstat><d:prop>"
                + "<d:getetag>\"abc123\"</d:getetag>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "<d:response><d:href>/c/card/b.vcf</d:href><d:propstat><d:prop>"
                + "<d:getetag>\"def456\"</d:getetag>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);
        eq("etag count", "2", String.valueOf(rs.size()));
        eq("href 1", "/c/card/a.vcf", rs.get(0).href);
        eq("etag 1 unquoted", "abc123", rs.get(0).etag);
        eq("href 2", "/c/card/b.vcf", rs.get(1).href);
        eq("etag 2 unquoted", "def456", rs.get(1).etag);
        eq("status parsed", "200", String.valueOf(rs.get(0).status));
    }

    static void testMultigetData() throws Exception {
        String vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:x-1\r\nFN:Anh Tran\r\n"
                + "TEL;TYPE=CELL;TYPE=pref;TYPE=VOICE:+84 901 000 111\r\nEND:VCARD\r\n";
        String xml = "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
                + "<d:response><d:href>/c/card/x1.vcf</d:href><d:propstat><d:prop>"
                + "<d:getetag>\"e1\"</d:getetag>"
                + "<c:address-data>" + esc(vcard) + "</c:address-data>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);
        eq("one resource", "1", String.valueOf(rs.size()));
        eq("etag", "e1", rs.get(0).etag);

        // the whole point of the layer: data must feed VCardParser unchanged
        List<VCardContact> cs = VCardParser.parse(rs.get(0).data);
        eq("vcard parsed from dav", "1", String.valueOf(cs.size()));
        eq("vcard uid", "x-1", cs.get(0).uid);
        eq("vcard fn", "Anh Tran", cs.get(0).displayName);
        eq("vcard tel type", "CELL", cs.get(0).phones.get(0).type);
        eq("vcard tel", "+84 901 000 111", cs.get(0).phones.get(0).value);
    }

    // Some servers answer without a prefix on DAV: elements.
    static void testNoNamespacePrefix() throws Exception {
        String xml = "<multistatus xmlns=\"DAV:\" xmlns:card=\"urn:ietf:params:xml:ns:carddav\">"
                + "<response><href>/c/card/</href><propstat><prop>"
                + "<resourcetype><collection/><card:addressbook/></resourcetype>"
                + "</prop><status>HTTP/1.1 200 OK</status></propstat></response>"
                + "</multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);
        eq("unprefixed href", "/c/card/", rs.get(0).href);
        eq("unprefixed addressbook", "true", String.valueOf(rs.get(0).isAddressbook));
    }

    static void testWeakEtag() throws Exception {
        String xml = "<d:multistatus xmlns:d=\"DAV:\">"
                + "<d:response><d:href>/a.vcf</d:href><d:propstat><d:prop>"
                + "<d:getetag>W/\"weak-1\"</d:getetag>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        eq("weak etag stripped", "weak-1", DavXmlParser.parse(xml).get(0).etag);
    }

    static void testNotFoundStatus() throws Exception {
        String xml = "<d:multistatus xmlns:d=\"DAV:\">"
                + "<d:response><d:href>/gone.vcf</d:href><d:propstat><d:prop>"
                + "<d:getetag/>"
                + "</d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);
        eq("404 status parsed", "404", String.valueOf(rs.get(0).status));
        eq("404 href kept", "/gone.vcf", rs.get(0).href);
    }

    static void testEmptyAndMalformed() {
        try {
            List<DavXmlParser.Resource> rs = DavXmlParser.parse(
                    "<d:multistatus xmlns:d=\"DAV:\"/>");
            eq("empty multistatus", "0", String.valueOf(rs.size()));
        } catch (Exception e) {
            fail++;
            System.out.println("  FAIL empty multistatus threw " + e);
        }

        try {
            DavXmlParser.parse("<not-xml");
            fail++;
            System.out.println("  FAIL malformed xml should throw");
        } catch (Exception e) {
            pass++;
            System.out.println("  ok   malformed xml throws");
        }

        try {
            eq("missing principal is null", null,
                    DavXmlParser.findCurrentUserPrincipal(
                            "<d:multistatus xmlns:d=\"DAV:\"><d:response>"
                            + "<d:href>/</d:href></d:response></d:multistatus>"));
        } catch (Exception e) {
            fail++;
            System.out.println("  FAIL missing principal threw " + e);
        }
    }

    static void testCdataAndEntities() throws Exception {
        String xml = "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
                + "<d:response><d:href>/amp&amp;name.vcf</d:href><d:propstat><d:prop>"
                + "<c:address-data><![CDATA[BEGIN:VCARD\nFN:A & B <x>\nEND:VCARD\n]]>"
                + "</c:address-data>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);
        eq("entity in href decoded", "/amp&name.vcf", rs.get(0).href);
        List<VCardContact> cs = VCardParser.parse(rs.get(0).data);
        eq("cdata vcard fn", "A & B <x>", cs.get(0).displayName);
    }

    // SAX may split text across several characters() calls; a long vCard is
    // the realistic trigger, and dropping a chunk would corrupt the payload.
    static void testChunkedCharacters() throws Exception {
        StringBuilder note = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            note.append("0123456789");
        }
        String vcard = "BEGIN:VCARD\r\nFN:Long\r\nUID:" + note + "\r\nEND:VCARD\r\n";
        String xml = "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
                + "<d:response><d:href>/long.vcf</d:href><d:propstat><d:prop>"
                + "<c:address-data>" + esc(vcard) + "</c:address-data>"
                + "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                + "</d:multistatus>";
        List<DavXmlParser.Resource> rs = DavXmlParser.parse(xml);

        // XML 1.0 §2.11 makes the parser normalise CRLF to LF, so the payload
        // comes back shorter than it went in. VCardParser handles bare LF, so
        // this is lossless for our purposes -- assert on content, not length.
        eq("long data length after CRLF folding",
                String.valueOf(vcard.replace("\r\n", "\n").length()),
                String.valueOf(rs.get(0).data.length()));
        eq("no CR survives XML parsing", "true",
                String.valueOf(rs.get(0).data.indexOf('\r') < 0));

        List<VCardContact> cs = VCardParser.parse(rs.get(0).data);
        eq("long uid intact", note.toString(), cs.get(0).uid);
        eq("long card fn", "Long", cs.get(0).displayName);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    interface Case { void run() throws Exception; }

    static void run(String label, Case c) {
        try {
            c.run();
        } catch (Exception e) {
            fail++;
            System.out.println("  FAIL " + label + " threw " + e);
        }
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
        if (s.length() > 60) s = s.substring(0, 60) + "...";
        return "\"" + s.replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}

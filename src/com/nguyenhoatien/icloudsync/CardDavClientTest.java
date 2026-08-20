package com.nguyenhoatien.icloudsync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class CardDavClientTest {

    static int pass = 0;
    static int fail = 0;

    public static void main(String[] args) {
        run("CustomMethods", CardDavClientTest::testCustomMethods);
        run("ChunkedResponse", CardDavClientTest::testChunkedResponse);
        run("BasicAuthHeader", CardDavClientTest::testBasicAuthHeader);
        run("Discovery", CardDavClientTest::testDiscovery);
        run("FetchContacts", CardDavClientTest::testFetchContacts);
        run("Status403", CardDavClientTest::testStatus403);
        run("Status401", CardDavClientTest::testStatus401);
        run("MalformedXml", CardDavClientTest::testMalformedXml);
        run("MissingPrincipal", CardDavClientTest::testMissingPrincipal);

        System.out.println("---");
        System.out.println("pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    // HttpURLConnection rejects PROPFIND/REPORT outright; this is the reason
    // OkHttp is vendored at all, so assert the wire really carries them.
    static void testCustomMethods() throws Exception {
        Mock m = new Mock(ms("<response><href>/</href><propstat><prop>"
                + "<current-user-principal><href>/1/principal/</href>"
                + "</current-user-principal></prop>"
                + "<status>HTTP/1.1 200 OK</status></propstat></response>"));
        try {
            client(m).findPrincipal();
            eq("PROPFIND reached server", "PROPFIND", m.methods.get(0));

            Mock m2 = new Mock(ms(""));
            try {
                client(m2).fetchAll("/1/carddavhome/card/");
                eq("REPORT reached server", "REPORT", m2.methods.get(0));
            } finally {
                m2.stop();
            }
        } finally {
            m.stop();
        }
    }

    // iCloud answers with Transfer-Encoding: chunked and no Content-Length.
    static void testChunkedResponse() throws Exception {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            big.append("<response><href>/x").append(i).append(".vcf</href>")
                    .append("<propstat><prop><getetag>\"e").append(i).append("\"</getetag>")
                    .append("</prop><status>HTTP/1.1 200 OK</status></propstat></response>");
        }
        Mock m = new Mock(ms(big.toString()));
        m.chunked = true;
        try {
            String home = "/1/carddavhome/card/";
            List<CardDavClient.Card> cards = client(m).fetchAll(home);
            eq("chunked body fully read", "0", String.valueOf(cards.size()));
            eq("chunked transfer used", "true", String.valueOf(m.chunked));

            // parse the same payload directly to prove nothing was truncated
            List<DavXmlParser.Resource> rs = DavXmlParser.parse(ms(big.toString()));
            eq("all 400 responses present", "400", String.valueOf(rs.size()));
            eq("last etag intact", "e399", rs.get(399).etag);
        } finally {
            m.stop();
        }
    }

    static void testBasicAuthHeader() throws Exception {
        Mock m = new Mock(ms(""));
        try {
            new CardDavClient(m.url(), "user@icloud.com", "abcd-efgh-ijkl-mnop")
                    .fetchAll("/x/");
            String auth = m.headers.get(0).get("authorization");
            eq("basic auth present", "true", String.valueOf(auth != null));
            eq("basic auth encoding",
                    "Basic dXNlckBpY2xvdWQuY29tOmFiY2QtZWZnaC1pamtsLW1ub3A=", auth);
            eq("depth header sent", "1", m.headers.get(0).get("depth"));
        } finally {
            m.stop();
        }
    }

    static void testDiscovery() throws Exception {
        Mock m = new Mock(null);
        m.router = true;
        try {
            CardDavClient c = client(m);
            String p = c.findPrincipal();
            eq("principal path", "/1/principal/", p);
            eq("principal depth", "0", m.headers.get(0).get("depth"));

            String home = c.findAddressbookHome(p);
            eq("home path", "/1/carddavhome/", home);

            List<String> books = c.findAddressbooks(home);
            eq("one addressbook", "1", String.valueOf(books.size()));
            eq("addressbook path", "/1/carddavhome/card/", books.get(0));
            eq("collections depth", "1", m.headers.get(2).get("depth"));
        } finally {
            m.stop();
        }
    }

    static void testFetchContacts() throws Exception {
        Mock m = new Mock(null);
        m.router = true;
        try {
            List<VCardContact> cs = client(m).fetchContacts();
            eq("contact count", "2", String.valueOf(cs.size()));
            eq("contact 1 fn", "Anh Tran", cs.get(0).displayName);
            eq("contact 1 family", "Tran", cs.get(0).family);
            eq("contact 1 tel type", "CELL", cs.get(0).phones.get(0).type);
            eq("contact 1 tel", "+84 901 000 111", cs.get(0).phones.get(0).value);
            eq("contact 1 real uid kept", "uid-1", cs.get(0).uid);
            eq("contact 2 fn", "Bao Le", cs.get(1).displayName);
            // no UID in card 2, so href becomes the dedupe key
            eq("contact 2 uid from href", "/1/carddavhome/card/2.vcf", cs.get(1).uid);
        } finally {
            m.stop();
        }
    }

    // iCloud returns 403 for a wrong username, not 401.
    static void testStatus403() throws Exception {
        Mock m = new Mock("");
        m.status = 403;
        try {
            client(m).findPrincipal();
            fail++;
            System.out.println("  FAIL 403 should throw");
        } catch (CardDavClient.DavException e) {
            eq("403 status", "403", String.valueOf(e.status));
            eq("403 counts as auth failure", "true", String.valueOf(e.isAuthFailure()));
        } finally {
            m.stop();
        }
    }

    static void testStatus401() throws Exception {
        Mock m = new Mock("nope");
        m.status = 401;
        try {
            client(m).findPrincipal();
            fail++;
            System.out.println("  FAIL 401 should throw");
        } catch (CardDavClient.DavException e) {
            eq("401 status", "401", String.valueOf(e.status));
            eq("401 counts as auth failure", "true", String.valueOf(e.isAuthFailure()));
        } finally {
            m.stop();
        }
    }

    static void testMalformedXml() throws Exception {
        Mock m = new Mock("<not-closed");
        try {
            client(m).findPrincipal();
            fail++;
            System.out.println("  FAIL malformed xml should throw");
        } catch (CardDavClient.DavException e) {
            eq("malformed reported as status 0", "0", String.valueOf(e.status));
            eq("malformed not auth failure", "false", String.valueOf(e.isAuthFailure()));
        } finally {
            m.stop();
        }
    }

    static void testMissingPrincipal() throws Exception {
        Mock m = new Mock(ms("<response><href>/</href></response>"));
        try {
            client(m).findPrincipal();
            fail++;
            System.out.println("  FAIL missing principal should throw");
        } catch (CardDavClient.DavException e) {
            eq("missing principal throws", "0", String.valueOf(e.status));
        } finally {
            m.stop();
        }
    }

    static CardDavClient client(Mock m) {
        return new CardDavClient(m.url(), "u", "p");
    }

    static String ms(String inner) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<multistatus xmlns=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">\n"
                + inner + "\n</multistatus>\n";
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Raw-socket mock: com.sun.net.httpserver is unavailable on Android and
    // this keeps the test dependent on nothing but the vendored jars.
    static class Mock {
        final ServerSocket server;
        final Thread thread;
        final List<String> methods = new ArrayList<String>();
        final List<java.util.Map<String, String>> headers =
                new ArrayList<java.util.Map<String, String>>();
        volatile String body;
        volatile int status = 207;
        volatile boolean chunked = false;
        volatile boolean router = false;
        volatile boolean running = true;

        Mock(String body) throws IOException {
            this.body = body;
            this.server = new ServerSocket(0);
            this.thread = new Thread(new Runnable() {
                public void run() {
                    loop();
                }
            });
            this.thread.setDaemon(true);
            this.thread.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void stop() {
            running = false;
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }

        void loop() {
            while (running) {
                Socket s = null;
                try {
                    s = server.accept();
                    handle(s);
                } catch (IOException e) {
                    // socket closed on stop(); nothing to do
                } finally {
                    if (s != null) {
                        try {
                            s.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }

        void handle(Socket s) throws IOException {
            InputStream in = s.getInputStream();
            StringBuilder head = new StringBuilder();
            int prev = -1;
            while (true) {
                int b = in.read();
                if (b < 0) {
                    return;
                }
                head.append((char) b);
                int len = head.length();
                if (len >= 4 && head.charAt(len - 1) == '\n' && head.charAt(len - 2) == '\r'
                        && head.charAt(len - 3) == '\n' && head.charAt(len - 4) == '\r') {
                    break;
                }
                prev = b;
            }

            String[] lines = head.toString().split("\r\n");
            String method = lines[0].split(" ")[0];
            methods.add(method);

            java.util.Map<String, String> hs = new java.util.HashMap<String, String>();
            int contentLength = 0;
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) {
                    String k = lines[i].substring(0, c).trim().toLowerCase();
                    String v = lines[i].substring(c + 1).trim();
                    hs.put(k, v);
                    if (k.equals("content-length")) {
                        contentLength = Integer.parseInt(v);
                    }
                }
            }
            headers.add(hs);

            StringBuilder reqBody = new StringBuilder();
            for (int i = 0; i < contentLength; i++) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                reqBody.append((char) b);
            }

            String out = router ? route(reqBody.toString()) : (body == null ? "" : body);
            byte[] payload = out.getBytes("UTF-8");
            OutputStream os = s.getOutputStream();

            StringBuilder resp = new StringBuilder();
            resp.append("HTTP/1.1 ").append(status).append(" Multi-Status\r\n");
            resp.append("Content-Type: application/xml; charset=UTF-8\r\n");
            if (chunked) {
                resp.append("Transfer-Encoding: chunked\r\n");
            } else {
                resp.append("Content-Length: ").append(payload.length).append("\r\n");
            }
            resp.append("Connection: close\r\n\r\n");
            os.write(resp.toString().getBytes("UTF-8"));

            if (chunked) {
                int off = 0;
                int size = 1024;
                while (off < payload.length) {
                    int n = Math.min(size, payload.length - off);
                    os.write((Integer.toHexString(n) + "\r\n").getBytes("UTF-8"));
                    os.write(payload, off, n);
                    os.write("\r\n".getBytes("UTF-8"));
                    off += n;
                }
                os.write("0\r\n\r\n".getBytes("UTF-8"));
            } else {
                os.write(payload);
            }
            os.flush();
        }

        String route(String req) {
            if (req.contains("current-user-principal")) {
                return ms("<response><href>/</href><propstat><prop>"
                        + "<current-user-principal><href>/1/principal/</href>"
                        + "</current-user-principal></prop>"
                        + "<status>HTTP/1.1 200 OK</status></propstat></response>");
            }
            if (req.contains("addressbook-home-set")) {
                return ms("<response><href>/1/principal/</href><propstat><prop>"
                        + "<c:addressbook-home-set><href>/1/carddavhome/</href>"
                        + "</c:addressbook-home-set></prop>"
                        + "<status>HTTP/1.1 200 OK</status></propstat></response>");
            }
            if (req.contains("resourcetype")) {
                return ms("<response><href>/1/carddavhome/</href><propstat><prop>"
                        + "<resourcetype><collection/></resourcetype></prop>"
                        + "<status>HTTP/1.1 200 OK</status></propstat></response>"
                        + "<response><href>/1/carddavhome/card/</href><propstat><prop>"
                        + "<resourcetype><collection/><c:addressbook/></resourcetype></prop>"
                        + "<status>HTTP/1.1 200 OK</status></propstat></response>");
            }
            if (req.contains("addressbook-query")) {
                String v1 = "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:uid-1\r\nFN:Anh Tran\r\n"
                        + "N:Tran;Anh;;;\r\n"
                        + "TEL;TYPE=CELL;TYPE=pref;TYPE=VOICE:+84 901 000 111\r\n"
                        + "END:VCARD\r\n";
                String v2 = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Bao Le\r\nN:Le;Bao;;;\r\n"
                        + "END:VCARD\r\n";
                return ms("<response><href>/1/carddavhome/card/1.vcf</href><propstat><prop>"
                        + "<getetag>\"e1\"</getetag><c:address-data>" + esc(v1)
                        + "</c:address-data></prop>"
                        + "<status>HTTP/1.1 200 OK</status></propstat></response>"
                        + "<response><href>/1/carddavhome/card/2.vcf</href><propstat><prop>"
                        + "<getetag>\"e2\"</getetag><c:address-data>" + esc(v2)
                        + "</c:address-data></prop>"
                        + "<status>HTTP/1.1 200 OK</status></propstat></response>");
            }
            return ms("");
        }
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

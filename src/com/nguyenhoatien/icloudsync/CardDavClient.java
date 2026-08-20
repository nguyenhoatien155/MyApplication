package com.nguyenhoatien.icloudsync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CardDavClient {

    public static final String DEFAULT_ROOT = "https://contacts.icloud.com";

    public static class Card {
        public String href;
        public String etag;
        public String vcard;
    }

    public static class DavException extends IOException {
        public final int status;

        public DavException(int status, String message) {
            super(message);
            this.status = status;
        }

        public boolean isAuthFailure() {
            return status == 401 || status == 403;
        }
    }

    private static final MediaType XML =
            MediaType.parse("text/xml; charset=utf-8");

    private static final String PROP_PRINCIPAL =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<d:propfind xmlns:d=\"DAV:\"><d:prop>"
            + "<d:current-user-principal/>"
            + "</d:prop></d:propfind>";

    private static final String PROP_HOME =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
            + "<d:prop><c:addressbook-home-set/></d:prop></d:propfind>";

    private static final String PROP_COLLECTIONS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<d:propfind xmlns:d=\"DAV:\"><d:prop>"
            + "<d:resourcetype/><d:displayname/>"
            + "</d:prop></d:propfind>";

    private static final String REPORT_ALL =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<c:addressbook-query xmlns:d=\"DAV:\""
            + " xmlns:c=\"urn:ietf:params:xml:ns:carddav\">"
            + "<d:prop><d:getetag/><c:address-data/></d:prop>"
            + "<c:filter/></c:addressbook-query>";

    private final String root;
    private final String credential;
    private final OkHttpClient http;

    public CardDavClient(String root, String user, String appSpecificPassword) {
        this.root = stripTrailingSlash(root == null ? DEFAULT_ROOT : root);
        this.credential = Credentials.basic(user, appSpecificPassword);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    CardDavClient(String root, String user, String password, OkHttpClient client) {
        this.root = stripTrailingSlash(root == null ? DEFAULT_ROOT : root);
        this.credential = Credentials.basic(user, password);
        this.http = client;
    }

    public String findPrincipal() throws IOException {
        String xml = request("PROPFIND", root + "/", "0", PROP_PRINCIPAL);
        String href = principalOf(xml);
        if (href == null) {
            throw new DavException(0, "no current-user-principal in response");
        }
        return href;
    }

    public String findAddressbookHome(String principalHref) throws IOException {
        String xml = request("PROPFIND", absolute(principalHref), "0", PROP_HOME);
        String href = homeOf(xml);
        if (href == null) {
            throw new DavException(0, "no addressbook-home-set in response");
        }
        return href;
    }

    public List<String> findAddressbooks(String homeHref) throws IOException {
        String xml = request("PROPFIND", absolute(homeHref), "1", PROP_COLLECTIONS);
        List<DavXmlParser.Resource> rs = parse(xml);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < rs.size(); i++) {
            DavXmlParser.Resource r = rs.get(i);
            if (r.isAddressbook && r.href != null) {
                out.add(r.href);
            }
        }
        return out;
    }

    public List<Card> fetchAll(String addressbookHref) throws IOException {
        String xml = request("REPORT", absolute(addressbookHref), "1", REPORT_ALL);
        List<DavXmlParser.Resource> rs = parse(xml);
        List<Card> out = new ArrayList<Card>();
        for (int i = 0; i < rs.size(); i++) {
            DavXmlParser.Resource r = rs.get(i);
            if (r.data == null || r.data.trim().length() == 0) {
                continue;
            }
            if (r.status != 0 && (r.status < 200 || r.status > 299)) {
                continue;
            }
            Card c = new Card();
            c.href = r.href;
            c.etag = r.etag;
            c.vcard = r.data;
            out.add(c);
        }
        SyncLog.add("  " + out.size() + " cards with data of " + rs.size() + " responses");
        return out;
    }

    public List<VCardContact> fetchContacts() throws IOException {
        String principal = findPrincipal();
        String home = findAddressbookHome(principal);
        List<String> books = findAddressbooks(home);
        SyncLog.add("principal=" + principal);
        SyncLog.add("home=" + home);
        SyncLog.add("addressbooks=" + books.size() + " " + books);

        List<VCardContact> out = new ArrayList<VCardContact>();
        for (int i = 0; i < books.size(); i++) {
            List<Card> cards = fetchAll(books.get(i));
            for (int j = 0; j < cards.size(); j++) {
                Card card = cards.get(j);
                List<VCardContact> parsed = VCardParser.parse(card.vcard);
                for (int k = 0; k < parsed.size(); k++) {
                    VCardContact c = parsed.get(k);
                    // The iCloud web export ships no UID, so the resource href is
                    // the only stable key available for dedupe on later syncs.
                    if (c.uid == null || c.uid.length() == 0) {
                        c.uid = card.href;
                    }
                    out.add(c);
                }
            }
        }
        SyncLog.add("parsed " + out.size() + " contacts");
        return out;
    }

    private String request(String method, String url, String depth, String body)
            throws IOException {
        Request.Builder b = new Request.Builder()
                .url(url)
                .method(method, RequestBody.create(XML, body))
                .header("Authorization", credential)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("Accept", "text/xml, application/xml");
        if (depth != null) {
            b.header("Depth", depth);
        }

        SyncLog.add(method + " " + url);
        Response resp = http.newCall(b.build()).execute();
        try {
            ResponseBody rb = resp.body();
            String text = rb == null ? "" : rb.string();
            SyncLog.add("  -> HTTP " + resp.code() + ", " + text.length() + " bytes");
            // 207 Multi-Status is the normal CardDAV answer and counts as success.
            // iCloud answers a wrong username with 403, not 401.
            if (!resp.isSuccessful()) {
                throw new DavException(resp.code(), method + " " + url
                        + " failed: HTTP " + resp.code()
                        + (text.length() > 0 ? " " + firstLine(text) : ""));
            }
            return text;
        } finally {
            resp.close();
        }
    }

    private String absolute(String href) {
        if (href == null) {
            return root + "/";
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (!href.startsWith("/")) {
            href = "/" + href;
        }
        return root + href;
    }

    private static List<DavXmlParser.Resource> parse(String xml) throws IOException {
        try {
            return DavXmlParser.parse(xml);
        } catch (Exception e) {
            throw new DavException(0, "malformed DAV response: " + e);
        }
    }

    private static String principalOf(String xml) throws IOException {
        try {
            return DavXmlParser.findCurrentUserPrincipal(xml);
        } catch (Exception e) {
            throw new DavException(0, "malformed DAV response: " + e);
        }
    }

    private static String homeOf(String xml) throws IOException {
        try {
            return DavXmlParser.findAddressbookHome(xml);
        } catch (Exception e) {
            throw new DavException(0, "malformed DAV response: " + e);
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String line = nl < 0 ? s : s.substring(0, nl);
        return line.length() > 200 ? line.substring(0, 200) : line;
    }

    private static String stripTrailingSlash(String s) {
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}

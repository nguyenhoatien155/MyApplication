package com.nguyenhoatien.icloudsync;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DavXmlParser {

    public static class Resource {
        public String href;
        public String etag;
        public String data;
        public boolean isAddressbook;
        public int status;
        public String principalHref;
        public String homeHref;
    }

    private static final String NS_DAV = "DAV:";
    private static final String NS_CARDDAV = "urn:ietf:params:xml:ns:carddav";

    public static String findCurrentUserPrincipal(String xml) throws SAXException {
        List<Resource> rs = parse(xml);
        for (int i = 0; i < rs.size(); i++) {
            String v = rs.get(i).principalHref;
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static String findAddressbookHome(String xml) throws SAXException {
        List<Resource> rs = parse(xml);
        for (int i = 0; i < rs.size(); i++) {
            String v = rs.get(i).homeHref;
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static List<Resource> parse(String xml) throws SAXException {
        Handler h = new Handler();
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(true);
            SAXParser p = f.newSAXParser();
            InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            p.parse(new InputSource(in), h);
        } catch (SAXException e) {
            throw e;
        } catch (Exception e) {
            throw new SAXException(e);
        }
        return h.resources;
    }

    private static class Handler extends DefaultHandler {

        final List<Resource> resources = new ArrayList<Resource>();

        private Resource current;
        private StringBuilder text;
        private int depth;
        private int responseDepth = -1;
        private int propstatDepth = -1;
        private int hrefDepth = -1;

        private String pendingHref;
        private String pendingEtag;
        private String pendingData;
        private String pendingPrincipal;
        private String pendingHome;
        private String pendingStatus;
        private boolean sawAddressbook;
        private boolean inPrincipal;
        private boolean inHomeSet;

        @Override
        public void startElement(String uri, String local, String qName, Attributes a) {
            depth++;
            String name = local.length() > 0 ? local : stripPrefix(qName);

            if (isDav(uri) && name.equals("response")) {
                responseDepth = depth;
                pendingHref = null;
                pendingEtag = null;
                pendingData = null;
                pendingStatus = null;
                sawAddressbook = false;
                return;
            }
            if (isDav(uri) && name.equals("current-user-principal")) {
                inPrincipal = true;
                return;
            }
            if (isCardDav(uri) && name.equals("addressbook-home-set")) {
                inHomeSet = true;
                return;
            }
            if (isDav(uri) && name.equals("propstat")) {
                propstatDepth = depth;
            }
            if (isCardDav(uri) && name.equals("addressbook")) {
                sawAddressbook = true;
                return;
            }

            if (name.equals("href") || name.equals("getetag") || name.equals("address-data")
                    || name.equals("status")) {
                text = new StringBuilder();
                if (name.equals("href")) {
                    hrefDepth = depth;
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int len) {
            if (text != null) {
                text.append(ch, start, len);
            }
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            String name = local.length() > 0 ? local : stripPrefix(qName);

            if (name.equals("href") && text != null) {
                String v = text.toString().trim();
                if (inPrincipal) {
                    pendingPrincipal = v;
                } else if (inHomeSet) {
                    pendingHome = v;
                } else if (pendingHref == null) {
                    pendingHref = v;
                }
                text = null;
                hrefDepth = -1;
            } else if (name.equals("getetag") && text != null) {
                pendingEtag = text.toString().trim();
                text = null;
            } else if (name.equals("address-data") && text != null) {
                pendingData = text.toString();
                text = null;
            } else if (name.equals("status") && text != null) {
                if (pendingStatus == null) {
                    pendingStatus = text.toString().trim();
                }
                text = null;
            }

            if (isDav(uri) && name.equals("current-user-principal")) {
                inPrincipal = false;
            }
            if (isCardDav(uri) && name.equals("addressbook-home-set")) {
                inHomeSet = false;
            }

            if (isDav(uri) && name.equals("response") && depth == responseDepth) {
                Resource r = new Resource();
                r.href = pendingHref;
                r.etag = unquote(pendingEtag);
                r.data = pendingData;
                r.isAddressbook = sawAddressbook;
                r.status = parseStatus(pendingStatus);
                r.principalHref = pendingPrincipal;
                r.homeHref = pendingHome;
                resources.add(r);
                responseDepth = -1;
                pendingPrincipal = null;
                pendingHome = null;
            }
            depth--;
        }

        private static String stripPrefix(String qName) {
            int c = qName.indexOf(':');
            return c < 0 ? qName : qName.substring(c + 1);
        }

        private static boolean isDav(String uri) {
            return uri == null || uri.length() == 0 || uri.equals(NS_DAV);
        }

        private static boolean isCardDav(String uri) {
            return uri != null && uri.equals(NS_CARDDAV);
        }
    }

    private static String unquote(String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("W/")) {
            s = s.substring(2);
        }
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static int parseStatus(String status) {
        if (status == null) {
            return 0;
        }
        int sp = status.indexOf(' ');
        if (sp < 0) {
            return 0;
        }
        int end = status.indexOf(' ', sp + 1);
        String code = end < 0 ? status.substring(sp + 1) : status.substring(sp + 1, end);
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

package org.example.mail.autodetect;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Hashtable;

import org.w3c.dom.Document;

public final class DefaultAutoConfigProvider implements AutoConfigProvider {
    private static final int TIMEOUT = 2500;
    private static final String[] MOZ_URLS = {
            "https://autoconfig.%s/mail/config-v1.1.xml",
            "http://%s/.well-known/autoconfig/mail/config-v1.1.xml"
    };

    @Override
    public AutoConfigResult discover(String email) throws Exception {
        int idx = email.indexOf('@');
        if (idx < 0 || idx == email.length() - 1) return null;
        String domain = email.substring(idx + 1).toLowerCase();
        AutoConfigResult r = querySrv(domain);
        return r != null ? r : queryMozilla(domain);
    }

    private AutoConfigResult querySrv(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes at = ctx.getAttributes("_submission._tcp." + domain, new String[]{"SRV"});
            Attribute a = at.get("SRV");
            if (a == null || a.size() == 0) return null;
            String[] p = a.get(0).toString().split(" ");
            if (p.length != 4) return null;
            int port = Integer.parseInt(p[2]);
            String host = p[3].endsWith(".") ? p[3].substring(0, p[3].length() - 1) : p[3];
            return new AutoConfigResult(host, port, true);
        } catch (NamingException | NumberFormatException e) {
            return null;
        }
    }

    private AutoConfigResult queryMozilla(String domain) {
        for (String tpl : MOZ_URLS) {
            try {
                URL url = new URL(tpl.formatted(domain));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(TIMEOUT);
                c.setReadTimeout(TIMEOUT);
                if (c.getResponseCode() != 200) continue;
                try (InputStream in = c.getInputStream()) {
                    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    Document d = f.newDocumentBuilder().parse(in);
                    var nodes = d.getElementsByTagName("outgoingServer");
                    for (int i = 0; i < nodes.getLength(); i++) {
                        var n = nodes.item(i);
                        if (!"smtp".equalsIgnoreCase(n.getAttributes().getNamedItem("type").getNodeValue())) continue;
                        String host = text(n, "hostname");
                        int port = Integer.parseInt(text(n, "port"));
                        boolean ssl = "SSL".equalsIgnoreCase(text(n, "socketType"));
                        return new AutoConfigResult(host, port, ssl);
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static String text(org.w3c.dom.Node parent, String tag) {
        var list = ((org.w3c.dom.Element) parent).getElementsByTagName(tag);
        return list.getLength() == 0 ? "" : list.item(0).getTextContent();
    }
}

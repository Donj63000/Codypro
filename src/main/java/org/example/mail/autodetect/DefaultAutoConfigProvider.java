package org.example.mail.autodetect;

import javax.naming.directory.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/** Provider performing DNS SRV lookup and Mozilla autoconfig queries. */
public class DefaultAutoConfigProvider implements AutoConfigProvider {
    @Override
    public AutoConfigResult discover(String email) throws Exception {
        int at = email.indexOf('@');
        if (at < 0) return null;
        String domain = email.substring(at + 1);

        AutoConfigResult srv = querySrv(domain);
        if (srv != null) return srv;

        AutoConfigResult moz = queryMozilla(domain);
        return moz;
    }

    private AutoConfigResult querySrv(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_submission._tcp." + domain, new String[]{"SRV"});
            Attribute a = attrs.get("SRV");
            if (a != null && a.size() > 0) {
                String[] parts = a.get(0).toString().split(" ");
                if (parts.length == 4) {
                    int port = Integer.parseInt(parts[2]);
                    String host = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                    return new AutoConfigResult(host, port, true);
                }
            }
        } catch (NamingException | NumberFormatException ignore) {}
        return null;
    }

    private AutoConfigResult queryMozilla(String domain) {
        String[] urls = new String[]{
                "https://autoconfig." + domain + "/mail/config-v1.1.xml",
                "http://" + domain + "/.well-known/autoconfig/mail/config-v1.1.xml"
        };
        for (String u : urls) {
            try {
                URL url = new URL(u);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(2000);
                con.setReadTimeout(2000);
                int code = con.getResponseCode();
                if (code != 200) continue;
                try (InputStream in = con.getInputStream()) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    dbf.setXIncludeAware(false);
                    dbf.setExpandEntityReferences(false);
                    Document doc = dbf.newDocumentBuilder().parse(in);
                    var nodes = doc.getElementsByTagName("outgoingServer");
                    for (int i = 0; i < nodes.getLength(); i++) {
                        var elt = nodes.item(i);
                        if ("smtp".equals(elt.getAttributes().getNamedItem("type").getNodeValue())) {
                            String host = textContent(elt, "hostname");
                            int port = Integer.parseInt(textContent(elt, "port"));
                            String socket = textContent(elt, "socketType");
                            boolean ssl = "SSL".equalsIgnoreCase(socket);
                            return new AutoConfigResult(host, port, ssl);
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        return null;
    }

    private static String textContent(org.w3c.dom.Node parent, String tag) {
        var nodes = ((org.w3c.dom.Element) parent).getElementsByTagName(tag);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent();
    }
}

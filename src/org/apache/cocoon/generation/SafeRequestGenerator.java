/*
Copied from Cocoon's RequestGenerator and adapted to eliminiate NULL-characters.
 */
package org.apache.cocoon.generation;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Map;

import org.apache.avalon.framework.CascadingRuntimeException;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameterizable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.XMLUtils;
import org.apache.cocoon.xml.IncludeXMLConsumer;
import org.apache.excalibur.xml.sax.SAXParser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @cocoon.sitemap.component.documentation
 * Generates an XML representation of the incoming request.
 *
 * @cocoon.sitemap.component.name   request
 * @cocoon.sitemap.component.label  content
 * @cocoon.sitemap.component.logger sitemap.generator.request
 *
 * @cocoon.sitemap.component.pooling.max  16
 *
 * <p>
 * <b>Configuration options:</b>
 * <dl>
 * <dt> <i>container-encoding</i> (optional)
 * <dd> The encoding used by container. Default value is ISO-8859-1.
 * <dt> <i>form-encoding</i> (optional)
 * <dd> The supposed encoding of the request parameter. Default is null.
 * <dt> <i>generate-attributes</i> (optional)
 * <dd> If true, request attributes were also included. Default is false.
 * </dl>
 * These configuration options are supported at both declaration and use time.
 * The configuration at use time takes priority over declaration time.
 *
 */
public class SafeRequestGenerator extends RequestGenerator {

    /** The namespace prefix of this generator. */
    private final static String PREFIX = "h";
    /** The namespace URI of this generator. */
    private final static String URI = "http://apache.org/cocoon/request/2.0";

    /** The configured container encoding at declaration time. */
    private String global_container_encoding;
    /** The configured container encoding at use time. */
    private String container_encoding;

    /** The configured form encoding at declaration time. */
    private String global_form_encoding;
    /** The configured form encoding at use time. */
    private String form_encoding;

    /** The configuration for including request attributes at declaration time. */
    private boolean global_generate_attributes;
    /** The configuration for including request attributes at use time. */
    private boolean generate_attributes;

    /**
     * Generate XML data.
     */
    @Override
    public void generate()
            throws SAXException {
        final Request request = ObjectModelHelper.getRequest(objectModel);
        final AttributesImpl attr = new AttributesImpl();

        this.contentHandler.startDocument();
        this.contentHandler.startPrefixMapping(PREFIX, URI);

        attribute(attr, "target", request.getRequestURI());
        attribute(attr, "sitemap", request.getSitemapURI());
        attribute(attr, "source", (this.source != null ? this.source : ""));
        start("request", attr);

        start("requestHeaders", attr);
        Enumeration headers = request.getHeaderNames();
        if ( headers != null ) {
            while (headers.hasMoreElements()) {
                String header = (String)headers.nextElement();
                attribute(attr, "name", header);
                start("header", attr);
                data(request.getHeader(header));
                end("header");
            }
        }
        end("requestHeaders");

        start("requestParameters", attr);
        Enumeration parameters = request.getParameterNames();
        while (parameters.hasMoreElements()) {
            String parameter = (String)parameters.nextElement();
            attribute(attr, "name", parameter);
            start("parameter", attr);
            String values[] = request.getParameterValues(parameter);
            if (values != null) {
                for (int x = 0; x < values.length; x++) {
                    start("value", attr);
                    if (form_encoding != null) {
                        try {
                            String safeValue = String.valueOf(values[x]).replaceAll("[\\p{Cntrl}]", "");
//                            this.getLogger().info("safeValue= " + safeValue);
//                            System.out.println("safeValue=");
//                            System.out.println(safeValue);

                            data(safeValue, container_encoding, form_encoding);
                        } catch (UnsupportedEncodingException uee) {
                            throw new CascadingRuntimeException("The suggested encoding is not supported.", uee);
                        }
                    } else if (parameter.startsWith("xml:")) {
                        try {
                            String safeValue = String.valueOf(values[x]).replaceAll("[\\p{Cntrl}]", "");
//                            this.getLogger().info("safeValue= " + safeValue);
//                            System.out.println("safeValue=");
//                            System.out.println(safeValue);

                            parse(safeValue);
                        } catch (Exception e) {
                            throw new CascadingRuntimeException("Could not parse the xml parameter", e);
                        }
                    } else {
                        String safeValue = String.valueOf(values[x]).replaceAll("[\\p{Cntrl}]", "");
//                        this.getLogger().info("safeValue= " + safeValue);
//                        System.out.println("safeValue=");
//                        System.out.println(safeValue);

                        data(safeValue);
                    }
                    end("value");
                }
            }
            end("parameter");
        }
        end("requestParameters");

        if (generate_attributes) {
            start("requestAttributes", attr);
            Enumeration attributes = request.getAttributeNames();
            while (attributes.hasMoreElements()) {
                String attribute = (String)attributes.nextElement();
                attribute(attr, "name", attribute);
                start("attribute", attr);
                Object value = request.getAttribute(attribute);
                if (value != null) {
                    start("value", attr);
                    String safeValue = String.valueOf(value).replaceAll("[\\p{Cntrl}]", "");
//                    this.getLogger().info("safeValue= " + safeValue);
//                    System.out.println("safeValue=");
//                    System.out.println(safeValue);
                    XMLUtils.valueOf(this.contentHandler, safeValue);
                    end("value");
                }
                end("attribute");
            }
            end("requestAttributes");
        }

        this.start("configurationParameters", attr);
        String[] confparams = super.parameters.getNames();
        for (int i = 0; i < confparams.length; i++) {
            attribute(attr, "name", confparams[i]);
            start("parameter", attr);
            data(super.parameters.getParameter(confparams[i], ""));
            end("parameter");
        }
        end("configurationParameters");

        start("remoteUser", attr);
        if (request.getRemoteUser() != null) {
            data(request.getRemoteUser());
        }
        end("remoteUser");

        end("request");

        this.contentHandler.endPrefixMapping(PREFIX);
        this.contentHandler.endDocument();
    }

    private void attribute(AttributesImpl attr, String name, String value) {
        attr.addAttribute("", name, name, "CDATA", String.valueOf(value).replaceAll("[\\p{Cntrl}]", ""));
    }

    private void start(String name, AttributesImpl attr)
            throws SAXException {
        super.contentHandler.startElement(URI, name, PREFIX + ":" + name, attr);
        attr.clear();
    }

    private void end(String name)
            throws SAXException {
        super.contentHandler.endElement(URI, name, PREFIX + ":" + name);
    }

    private void data(String data)
            throws SAXException {
        super.contentHandler.characters(String.valueOf(data).replaceAll("[\\p{Cntrl}]", "").toCharArray(), 0, data.length());
    }

    private void data(String data, String container_encoding, String form_encoding)
            throws SAXException, UnsupportedEncodingException {
        this.data(new String(String.valueOf(data).replaceAll("[\\p{Cntrl}]", "").getBytes(container_encoding), form_encoding));
    }

    private void parse(String data)
            throws Exception {
        SAXParser parser = null;
        try {
            parser = (SAXParser) manager.lookup(SAXParser.ROLE);
            InputSource is = new InputSource(new StringReader(String.valueOf(data).replaceAll("[\\p{Cntrl}]", "")));
            parser.parse(is, new IncludeXMLConsumer(super.xmlConsumer));
        } finally {
            manager.release(parser);
        }
    }
}

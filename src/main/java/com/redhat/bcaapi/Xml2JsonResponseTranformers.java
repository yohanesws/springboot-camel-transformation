package com.redhat.bcaapi;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.Exchange;
import org.apache.cxf.helpers.MapNamespaceContext;
import org.apache.cxf.jaxrs.ext.xml.XMLSource;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.soap.Node;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Xml2JsonResponseTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Get partial JSON message
    private String xmlPath = null;

    // Map of XML NameSpace
    private Map<String, String> namespaceMap = null;

    public Xml2JsonResponseTranformers() {

    }

    public Xml2JsonResponseTranformers(String xmlPath, Map namespaceMap) {
        this.xmlPath = xmlPath;
        this.namespaceMap = namespaceMap;
    }


    @Override
    public void process(Exchange exchange) throws Exception {

        String contentType = (String) exchange.getIn().getHeader(Exchange.CONTENT_TYPE);
        Integer httpResponseCode = (Integer) exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE);

        logger.debug("Process HTTP Response, messageId: " + exchange.getIn().getMessageId());

        // Print Camel message headers
        if (logger.isDebugEnabled()) {
            String header = "Message header: ";
            for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                header = header +  "\n  " + entry.getKey() + ": " + entry.getValue();
            }
            logger.debug(header);
        }

        if ( httpResponseCode == httpResponseCode.intValue() && contentType.contains("application/xml")) {

            String xmlString = exchange.getIn().getBody(String.class);
            logger.debug("xml: \n" + xmlString);

            String newXml = null;
            if (xmlPath != null) {

                // Extract partial XML using XPath
                InputStream is = new ByteArrayInputStream(xmlString.getBytes());
                //XMLSource xp = new XMLSource(is);

                XPathFactory factory = XPathFactory.newInstance();
                XPath xPath = factory.newXPath();
                NamespaceContext nsContext = new MapNamespaceContext(namespaceMap);
                xPath.setNamespaceContext(nsContext);
                NodeList nodeList = (NodeList) xPath.evaluate(xmlPath,
                        new InputSource(is),
                        XPathConstants.NODESET);

                newXml = nodeListToString(nodeList);

                logger.debug("Partial xml will be processed: \n" + newXml);
            } else {
                newXml = xmlString;
            }

            // Set header and body
            String jsonString = XML.toJSONObject(newXml).toString();
            logger.debug("Body transformed: " + jsonString);
            exchange.getIn().setBody(jsonString);

        }
    }

    private static String nodeListToString(NodeList nodes) throws TransformerException {
        DOMSource source = new DOMSource();
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        for (int i = 0; i < nodes.getLength(); ++i) {
            source.setNode(nodes.item(i));
            transformer.transform(source, result);
        }

        return writer.toString();
    }

}


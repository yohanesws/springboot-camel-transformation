package com.redhat.bcaapi;

import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.Exchange;
import org.apache.cxf.helpers.MapNamespaceContext;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
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
import java.util.Map;

public class Json2XmlReRequestTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Get partial JSON message
    private String jsonPath = null;

    // Map of XML NameSpace
    private Map<String, String> namespaceMap = null;

    private Boolean soapMessage = false;

    private String operationSchema;
    private String operationSchemaReference;

    private String soapPrefix = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:va=\"http://esb.bca.com/VA\">\n" +
            "\t<soapenv:Header/>\n" +
            "\t<soapenv:Body>";

    private String soapSuffix = "</soapenv:Body>\n" +
            "</soapenv:Envelope>";

    public Json2XmlReRequestTranformers() {

    }

    public Json2XmlReRequestTranformers(String jsonPath, Map namespaceMap) {
        this.jsonPath = jsonPath;
        this.namespaceMap = namespaceMap;
    }

    public Json2XmlReRequestTranformers(Boolean soapMessage,String operationSchema, String operationSchemaReference) {
        this.soapMessage =  soapMessage;
        this.operationSchema = operationSchema;
        this.operationSchemaReference = operationSchemaReference;
    }


    @Override
    public void process(Exchange exchange) throws Exception {

        String contentType = (String) exchange.getIn().getHeader(Exchange.CONTENT_TYPE);
        String httpMethod = (String) exchange.getIn().getHeader(Exchange.HTTP_METHOD);

        logger.debug("Process HTTP Request, messageId: " + exchange.getIn().getMessageId());

        // If DEBUG enabled -> Print Camel message headers
        if (logger.isDebugEnabled()) {
            String header = "Message header: ";
            for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                header = header +  "\n  " + entry.getKey() + ": " + entry.getValue();
            }
            logger.debug(header);
        }

        // We will only process HTTP Request with body contain JSON message
        if ( "POST".equals(httpMethod) && contentType.contains("application/json") ) {

            Gson gson = new Gson();
            String jsonString = exchange.getIn().getBody(String.class);

            Map<String,Object> map = null;
            if (jsonPath == null) {
                // Use this if the whole JSON need to be transform
                map = gson.fromJson(jsonString, Map.class);
            } else {
                // Extract partial JSON using JsonPath
                map = JsonPath.parse(jsonString).read(jsonPath);
                logger.debug("Partial json will be processed: \n" + gson.toJson(map));
            }

            JSONObject jsonObject = new JSONObject(map);

            // Set header and body
            String xmlString = XML.toString(jsonObject);
            logger.debug("Body transformed: " + xmlString);
            if (soapMessage){
                if(operationSchema!=null || operationSchema != ""){
                    xmlString =  soapPrefix+"<"+operationSchema + " "+ operationSchemaReference+">"+xmlString+"</"+operationSchema+">"+soapSuffix;
                }else{
                    xmlString =  soapPrefix+xmlString+soapSuffix;
                }
            }
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml;UTF-8");
            exchange.getIn().setBody(xmlString);

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


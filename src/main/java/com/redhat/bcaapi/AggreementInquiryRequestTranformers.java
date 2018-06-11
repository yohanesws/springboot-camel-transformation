package com.redhat.bcaapi;

import com.google.gson.Gson;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.util.Map;

public class AggreementInquiryRequestTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void process(Exchange exchange) throws Exception {

        String contentType = (String) exchange.getIn().getHeader(Exchange.CONTENT_TYPE);
        String httpMethod = (String) exchange.getIn().getHeader(Exchange.HTTP_METHOD);
        String userAgent = (String) exchange.getIn().getHeader("User-Agent");

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
        if ( "POST".equals(httpMethod) && "application/json".equalsIgnoreCase(contentType)) {

            String urlParams = null;
            Gson gson = new Gson();
            String jsonString = exchange.getIn().getBody(String.class);

            // Use this if the whole JSON need to be transform
            Map<String,Object>   map = gson.fromJson(jsonString, Map.class);
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (urlParams == null) {
                    urlParams = entry.getKey() + "=" + URLEncoder.encode((String)entry.getValue(), "UTF-8");
                } else {
                    urlParams = urlParams + "&" + entry.getKey() + "=" + URLEncoder.encode((String)entry.getValue(), "UTF-8");
                }
            }

            // Set header and body
            String newJsonString = gson.toJson(map);
            logger.debug("Body transformed: " + "");
            logger.debug("URL transformed: " + urlParams);
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, urlParams);
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            exchange.getIn().removeHeader(Exchange.CONTENT_TYPE);
            exchange.getIn().setBody("");

        }
    }
}

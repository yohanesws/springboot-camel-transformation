package com.redhat.bcaapi;

import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BcaLoginJsonRequestTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());



    public BcaLoginJsonRequestTranformers() {

    }


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

            Gson gson = new Gson();
            String jsonString = exchange.getIn().getBody(String.class);

            // Use this if the whole JSON need to be transform
            Map<String,Object>   map = gson.fromJson(jsonString, Map.class);
            map.put("ClientID", getClientID());
            map.put("UserAgent", userAgent);
            map.put("ClientIDAPI", getClientIDAPI());

            // Set header and body
            String newJsonString = gson.toJson(map);
            logger.debug("Body transformed: " + newJsonString);
            exchange.getIn().setBody(newJsonString);

        }
    }

    private String getClientIDAPI() {
        //TODO: getClientIDAPI
        return "8f28b265-e02d-4b20-9b05-65eff37461f2";
    }

    private String getClientID() {
        //TODO: getClientID
        return "74897794A8B04C0BA6EA49C234C9F20C";
    }

}


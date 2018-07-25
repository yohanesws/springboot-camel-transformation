package com.redhat.bcaapi;

import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonRequestTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Get partial JSON message
    private String jsonPath = null;

    // Transformation needed
    private boolean needTransform = true;

    public JsonRequestTranformers() {

    }

    public JsonRequestTranformers(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    public JsonRequestTranformers(boolean needTransform) {
        this.needTransform = needTransform;
    }

    public JsonRequestTranformers(String jsonPath, boolean needTransform) {
        this.jsonPath = jsonPath;
        this.needTransform = needTransform;
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
        if ( "POST".equals(httpMethod) && contentType.contains("json") ) {

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


            // New map to store transformed JSON
            Map<String, Object> resultMap = null;
            if (needTransform) {
                // Transform JSON based
                resultMap = new HashMap();
                transform(map, null, resultMap);
            } else {
                resultMap = map;
            }

            // Set header and body
            String newJsonString = gson.toJson(resultMap);
            logger.debug("Body transformed: " + newJsonString);
            exchange.getIn().setBody(newJsonString);

        }
    }

    private void transform(Map<String, Object> map, Object o, Map<String, Object> resultMap) {
        //TODO: Nothing to implement as of now
        resultMap = map;
    }


}


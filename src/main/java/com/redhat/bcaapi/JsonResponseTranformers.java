package com.redhat.bcaapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonResponseTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Get partial JSON message
    private String jsonPath = null;

    // Transformation needed
    private boolean needTransform = true;

    public JsonResponseTranformers() {

    }

    public JsonResponseTranformers(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    public JsonResponseTranformers(boolean needTransform) {
        this.needTransform = needTransform;
    }

    public JsonResponseTranformers(String jsonPath, boolean needTransform) {
        this.jsonPath = jsonPath;
        this.needTransform = needTransform;
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

        if (  contentType.contains("json") ) {

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


    void transform(Map<String, Object> inputMap, String key, Map<String, Object> resultMap) {
        for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
            String newkey = "";
            if (key == null) {
                newkey = entry.getKey();
            } else {
                newkey = key + "-" + entry.getKey();
            }

            if (entry.getValue() instanceof Map) {
                transform((Map)entry.getValue(), newkey, resultMap);
            } else if (entry.getValue() instanceof List) {
                List arr = (List) entry.getValue();
                resultMap.put(key + "-NumberOf" + entry.getKey() , String.valueOf(arr.size()));
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i) instanceof Map) {
                        transform((Map)arr.get(i), newkey + String.valueOf(i+1), resultMap);
                    } else if (arr.get(i) instanceof String){
                        resultMap.put(newkey + String.valueOf(i+1), (String)arr.get(i));
                    } else if (arr.get(i) instanceof List) {
                        //TODO: List can consist of List too
                    }
                }

            } else if (entry.getValue() instanceof  String
                    || entry.getValue() instanceof  Double
                    || entry.getValue() instanceof  Integer
                    || entry.getValue() instanceof  Long
                    || entry.getValue() instanceof  Boolean
                    || entry.getValue() instanceof  Float
                    || entry.getValue() instanceof  Character) {
                resultMap.put(newkey, String.valueOf(entry.getValue()));
            } else {
                new RuntimeException("Expect type primitive, Map or List, but found " + entry.getValue().getClass().getSimpleName() );
            }

        }
    }
}


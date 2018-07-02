package com.redhat.bcaapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.Map;

public class AggreementUpdateRequestTranformers implements org.apache.camel.Processor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void process(Exchange exchange) throws Exception {


        String contentType = (String) exchange.getIn().getHeader(Exchange.CONTENT_TYPE);
        String httpMethod = (String) exchange.getIn().getHeader(Exchange.HTTP_METHOD);
        String userAgent = (String) exchange.getIn().getHeader("User-Agent");

        logger.debug("Process HTTP Request, messageId: " + exchange.getIn().getMessageId());

        // If DEBUG enabled -> Print Camel message headers
        if (logger.isDebugEnabled()) {
            String header = "\n--> Message header: ";
            for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                header = header +  "\n  " + entry.getKey() + ": " + entry.getValue();
            }
            logger.debug(header);
        }

        // We will only process HTTP Request with body contain JSON message
        // PUT for Update operation is allowed here
        if ( "PUT".equals(httpMethod) && "application/json".equalsIgnoreCase(contentType)) {

            String jsonString = exchange.getIn().getBody(String.class);
            logger.debug("\n--> Body:\n" + jsonString);

//            Gson gson = new Gson();
//            JsonReader reader = new JsonReader(new StringReader(jsonString));
//            reader.setLenient(true);
//            Map<String,Object>   map = gson.fromJson(reader, Map.class);
//
//
//            jsonString = new GsonBuilder().create().toJson(map);
//            logger.debug("\n\t--> New enriched JSON: " + jsonString);

            // Set header and body
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
            exchange.getIn().removeHeader(Exchange.CONTENT_TYPE);
            exchange.getIn().setBody(jsonString);

        }
    }
}

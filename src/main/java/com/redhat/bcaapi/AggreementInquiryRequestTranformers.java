package com.redhat.bcaapi;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.net.URLEncoder;
import java.util.Map;

public class AggreementInquiryRequestTranformers implements org.apache.camel.Processor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void process(Exchange exchange) throws Exception {

        String branchCode = "";

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
        if ( "POST".equals(httpMethod) && "application/json".equalsIgnoreCase(contentType)) {

            logger.debug("\n--> Body:\n" + exchange.getIn().getBody(String.class));

            String urlParams = null;
            Gson gson = new Gson();
            String jsonString = exchange.getIn().getBody(String.class);


            // Change the JSON format to URL query params e.g.
            // From this:
            //    {
            //        "BranchCode": "0008",
            //        "InputDate": "2018-02-02",
            //        "InputTime": "09:33:36",
            //        "PageNumber": "1",
            //        "RowsPerPage": "5",
            //        "Status": "01"
            //    }
            //
            // Into this:
            //    http://10.20.200.140:9405/or-trx-agreement/0008?Status=01&PageNumber=1&RowsPerPage=5&InputDate=2018-02-02&InputTime=09:33:36
            //
            JsonReader reader = new JsonReader(new StringReader(jsonString));
            reader.setLenient(true);
            Map<String,Object>   map = gson.fromJson(reader, Map.class);
            for (Map.Entry<String, Object> entry : map.entrySet()) {

                if ( entry.getKey().equals("BranchCode") ){
                    branchCode = URLEncoder.encode((String)entry.getValue(), "UTF-8");
                } else {
                    if (urlParams == null) {
                        urlParams = entry.getKey() + "=" + URLEncoder.encode((String)entry.getValue(), "UTF-8");
                    } else {
                        urlParams = urlParams + "&" + entry.getKey() + "=" + URLEncoder.encode((String)entry.getValue(), "UTF-8");
                    }
                }

            }

            // Set header and body
            logger.debug("URL transformed: " + urlParams);
            exchange.getIn().setHeader("BrachCode", branchCode);
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, urlParams);
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            exchange.getIn().removeHeader(Exchange.CONTENT_TYPE);
            exchange.getIn().setBody("");

        }
    }
}

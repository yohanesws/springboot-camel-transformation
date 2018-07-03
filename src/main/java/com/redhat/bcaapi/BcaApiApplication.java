package com.redhat.bcaapi;

import com.google.common.collect.ImmutableMap;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@SpringBootApplication
public class BcaApiApplication {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

	public static void main(String[] args) {
		SpringApplication.run(BcaApiApplication.class, args);
	}

    @Value("${server.port}")
    String serverPort;

    @Value("${bca-backend.api.path}")
    String contextPath;

    @Bean
    ServletRegistrationBean servletRegistrationBean() {
        ServletRegistrationBean servlet = new ServletRegistrationBean
                (new CamelHttpTransportServlet(), contextPath + "/*");
        servlet.setName("CamelServlet");
        return servlet;
    }

    @Component
    class RestApi extends RouteBuilder {

        @Override
        public void configure() {

            CamelContext context = new DefaultCamelContext();

            // General error handler
            onException(Exception.class)
                .handled(true)
                // use HTTP status 500 when we had a server side error
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple(" { messageId: ${id}, timestamp: \"${date:now:dd/MMM/yyyy:HH:mm:ss Z}\" description: \"Report this messageId for escalation\""))
                .log("messageId: ${id}, timestamp: \"${date:now:dd/MMM/yyyy:HH:mm:ss Z}\", message: \"${exception.message}\"\n ${exception.stacktrace}");

            // FutureBranch-Passbooks Headers
            from("servlet:///passbooks/headers")
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .to("{{NewBranch_passbookHeaders_Endpoint}}?throwExceptionOnFailure=true")
                    .process(new JsonResponseTranformers("$.OutputSchema", false))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // BCA Login
            from("servlet:///auth/Login")
                .process(new BcaLoginJsonRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .to("{{BCA_Login_Endpoint}}?throwExceptionOnFailure=true")
                    .process(new JsonResponseTranformers("$.OutputSchema.OutputMessage", false))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

             // EAI-DB CallAppCode
            from("servlet:///ESBDB/ESBDBRestService")
                .process(new JsonRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .to("{{eaiDatabaseEP}}?throwExceptionOnFailure=true")
                    .process(new JsonResponseTranformers("$.OutputSchema"))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // eForm-InquiryParentReferenceNumber
            // API path    : /eform/inquiry/E01CS6WW60
            // Backend path: /eform/E01CS6WW60
            from("servlet:///eform/inquiry?matchOnUriPrefix=true")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // Get ReferenceNumber in HTTP request URL to be used when calling the backend with different URL format
                        HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
                        Matcher m = Pattern.compile("\\S+/eform/inquiry/([A-Za-z0-9\\-\\.]+)?(.*)$").matcher(req.getRequestURI());
                        if (m.matches()) {
                            logger.debug("\n\t--> ReferenceNumber: " + m.group(1));
                            exchange.getIn().setHeader("ReferenceNumber", m.group(1));
                        }

                        //Change ClientID header name
                        exchange.getIn().setHeader("ClientID", req.getHeader("X-BCA-ClientID"));
                    }
                })
                .process(new JsonRequestTranformers())
                .doTry()
                    // similar to adding param bridgeEndpoint=true in uri
                    .removeHeaders("CamelHttp*")

                    // Set the backend URL
                    .setHeader(Exchange.HTTP_URI, simple("{{eformEP}}/${header.ReferenceNumber}?throwExceptionOnFailure=true"))

                    // this will be overide by value of Exchange.HTTP_URI header
                    .to("http://dummyhost")

                    .process(new JsonResponseTranformers("$.OutputSchema"))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // eForm-InquiryChildReferenceNumber
            // API path    : /eForm/inquiry/ChildReferenceNumber/11ZYMJ64C
            // Backend path: /eform/base-ref-num/11ZYMJ64C
            from("servlet:///eForm/inquiry/ChildReferenceNumber?matchOnUriPrefix=true")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // Get ReferenceNumber in HTTP request URL to be used when calling the backend with different URL format
                        HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
                        Matcher m = Pattern.compile("\\S+/eform/inquiry/([A-Za-z0-9\\-\\.]+)?(.*)$").matcher(req.getRequestURI());
                        if (m.matches()) {
                            logger.debug("\n\t--> ReferenceNumber: " + m.group(1));
                            exchange.getIn().setHeader("ReferenceNumber", m.group(1));
                        }

                        //Change ClientID header name
                        exchange.getIn().setHeader("ClientID", req.getHeader("X-BCA-ClientID"));
                    }
                })
                .process(new JsonRequestTranformers())
                .doTry()
                    // similar to adding param bridgeEndpoint=true in uri
                    .removeHeaders("CamelHttp*")

                    // Set the backend URL
                    .setHeader(Exchange.HTTP_URI, simple("{{eForm_InquiryChildReferenceNumber_Endpoint}}/${header.ReferenceNumber}?throwExceptionOnFailure=true"))

                    // this will be overide by value of Exchange.HTTP_URI header
                    .to("http://dummyhost")

                    .process(new JsonResponseTranformers("$.OutputSchema"))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();


            // OR Transaction Agreement-Save and Update
            // API path: POST|PUT /or-trx-agreement
            // Backend path: POST|PUT /or-trx-agreement
            from("servlet:///or-trx-agreement")
                .doTry()
                    .choice()
                    .when().simple("${header.CamelHttpMethod} == 'POST'")
                        .process(new AggreementSaveRequestTranformers())
                        //Move HTTP query info to new exchange header name since all CamelHttp exchange header will be removed.
                        .setHeader("NewHttpQuery", simple("${header.CamelHttpQuery}"))
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        .setHeader(Exchange.HTTP_METHOD, simple("POST"))
                        .to("{{ORTransaction_AgreementSave_Endpoint}}?throwExceptionOnFailure=true")
                    .when().simple("${header.CamelHttpMethod} == 'PUT'")
                        .process(new AggreementUpdateRequestTranformers())
                        .setHeader("NewHttpQuery", simple("${header.CamelHttpQuery}"))
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        .setHeader(Exchange.HTTP_METHOD, simple("PUT"))
                        .to("{{ORTransaction_AgreementUpdate_Endpoint}}?throwExceptionOnFailure=true")
                    .endChoice()
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // OR Transaction Agreement-Inquiry
            // API path: POST /or-trx-agreement/inquiry/0008?Status=03 <-- TODO: Ask BCA. It is weird API design because BranchCode and Status is duplicated (in URL and JSON message)
            // Backend path: GET /or-trx-agreement/0008?Status=01&PageNumber=1&RowsPerPage=5&InputDate=2018-02-02&InputTime=09:33:36
            from("servlet:///or-trx-agreement/inquiry?matchOnUriPrefix=true")
                .process(new AggreementInquiryRequestTranformers())
                //Move HTTP query info to new exchange header name since all CamelHttp exchange header will be removed.
                .setHeader("NewHttpQuery", simple("${header.CamelHttpQuery}"))
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
                    .setHeader(Exchange.HTTP_QUERY, simple("${header.NewHttpQuery}"))
                    .setHeader(Exchange.HTTP_URI, simple("{{ORTransaction_AgreementInquiry_Endpoint}}/{header.BranchCode}?throwExceptionOnFailure=true"))
                    .to("http://dummyhost")
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // OR Transaction Agreement-Detail-Inquiry <-- TODO: Ask BCA, no detail sample for this operation
            // API path: GET /or-trx-agreement/detail/BN012345
            // Backend path: TODO: Ask BCA
            from("servlet:///or-trx-agreement/detail?matchOnUriPrefix=true&httpMethodRestrict=GET")
                // This is similar to param "httpMethodRestrict=GET" but it will still response 200 (OK) to client
                //.filter(header("Exchange.HTTP_METHOD").isEqualTo("GET"))
                .process(new AggreementInquiryRequestTranformers())
                //Move HTTP query info to new exchange header name since all CamelHttp exchange header will be removed.
                .setHeader("NewHttpQuery", simple("${header.CamelHttpQuery}"))
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader(Exchange.HTTP_METHOD, simple("GET"))
                    .setHeader(Exchange.HTTP_QUERY, simple("${header.NewHttpQuery}"))
                    .setHeader(Exchange.HTTP_URI, simple("{{ORTransaction_AgreementInquiry_Endpoint}}/{header.BranchCode}?throwExceptionOnFailure=true"))
                    .to("http://dummyhost")
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();


            // VA-Bill Presentment
            // API path: TODO: <- Ask BCA
            // Backend path: /VA/VAPortTypeBndPort
            from("servlet:///VA/VAPortTypeBndPort")
                .process(new Json2XmlReRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader("SOAPAction", constant("http://esb.bca.com/VA/BillPresentment"))
                    .to("{{VA_BillPresentment_Endpoint}}?throwExceptionOnFailure=true")
                    .process(new Xml2JsonResponseTranformers(
                            "/soapenv:Envelope/soapenv:Body/va:BillPresentmentResponse/OutputSchema/*",
                            ImmutableMap.of(
                                    "soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
                                    "va", "http://esb.bca.com/VA"
                            )))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // VA-Inquiry Payment Details
            // API path: TODO: <- Ask BCA
            // Backend path: /VA/VAPortTypeBndPort
            from("servlet:///VA/VAPortTypeBndPortZZZ")
                .process(new Json2XmlReRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader("SOAPAction", constant("http://esb.bca.com/VA/InquiryPayment"))
                    .to("{{VA_InquiryPaymentDetail_Endpoint}}?throwExceptionOnFailure=true")
                    .process(new Xml2JsonResponseTranformers(
                            "/soapenv:Envelope/soapenv:Body/va:InquiryPaymentResponse/OutputSchema/*",
                            ImmutableMap.of(
                                    "soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
                                    "va", "http://esb.bca.com/VA"
                            )))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();


            // VA-Payment Status
            // API path: TODO: <- Ask BCA
            // Backend path: /VA/VAPortTypeBndPort
            from("servlet:///VA/VAPortTypeBndPortYYY")
                .process(new Json2XmlReRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader("SOAPAction", constant("http://esb.bca.com/VA/InquiryPaymentByBranch"))
                    .to("{{VA_InquiryPaymentStatus_Endpoint}}?throwExceptionOnFailure=true")
                    .process(new Xml2JsonResponseTranformers(
                            "/soapenv:Envelope/soapenv:Body/va:InquiryPaymentByBranchResponse/OutputSchema/*",
                            ImmutableMap.of(
                                    "soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
                                    "va", "http://esb.bca.com/VA"
                            )))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // VA-Payment
            // API path: TODO: <- Ask BCA
            // Backend path: /VA/VAPortTypeBndPort
            from("servlet:///VA/VAPortTypeBndPortXXX")
                .process(new Json2XmlReRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader("SOAPAction", constant("http://esb.bca.com/VA/Payment"))
                    .to("{{VA_Payment_Endpoint}}?throwExceptionOnFailure=true")
                    .process(new Xml2JsonResponseTranformers(
                            "/soapenv:Envelope/soapenv:Body/WL5:PaymentRequest/OutputSchema/*",
                            ImmutableMap.of(
                                    "soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
                                    "va", "http://esb.bca.com/VA"
                            )))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // VA-Cancel Payment
            // API path: TODO: <- Ask BCA
            // Backend path: /VA/VAPortTypeBndPort
            from("servlet:///VA/VAPortTypeBndPortWWW")
                .process(new Json2XmlReRequestTranformers())
                .doTry()
                    .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                    .setHeader("SOAPAction", constant("http://esb.bca.com/VA/CancelPaymentByBranch"))
                    .to("{{VA_CancelPayment_Enpoint}}?throwExceptionOnFailure=true")
                    .process(new Xml2JsonResponseTranformers(
                            "/soapenv:Envelope/soapenv:Body/WL5:CancelPaymentByBranchRequest/OutputSchema/*",
                            ImmutableMap.of(
                                    "soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
                                    "va", "http://esb.bca.com/VA"
                            )))
                .endDoTry()
                .doCatch(HttpOperationFailedException.class)
                    .choice()
                        .when().simple("${exception.statusCode} == 404")
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .setBody(simple("${exception.message}"))
                        .otherwise()
                            .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                            .setBody(simple(""))
                    .endChoice()
                .end();

            // For testing only
            from("servlet:///proxy?matchOnUriPrefix=true")
                    .to("direct:backendSystem");

            // For testing only
            from("direct:backendSystem")
                    .log("Request received.")
                    .process(new JsonRequestTranformers())
                    .doTry()
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        .to("http://www.mocky.io/v2/5b0598473200000b2aebf991?throwExceptionOnFailure=true")
                        .process(new JsonResponseTranformers("$.OutputSchema"))
                    .endDoTry()
                    .doCatch(HttpOperationFailedException.class)
                        .choice()
                            .when().simple("${exception.statusCode} == 404")
                                .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                                .setBody(simple("${exception.message}"))
                            .otherwise()
                                .log("Error calling backend, backend statusCode: ${exception.statusCode}, ${exception.message}\n ${exception.stacktrace}")
                                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                                .setBody(simple(""))
                        .endChoice()
                    .end();

            from("servlet:///health?matchOnUriPrefix=true").process(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
                    exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "text/plain" + "; charset=UTF-8");
                    exchange.getOut().setHeader("Path", path);
                    exchange.getOut().setBody("SUCCESS");
                }
            });


        }
    }
}



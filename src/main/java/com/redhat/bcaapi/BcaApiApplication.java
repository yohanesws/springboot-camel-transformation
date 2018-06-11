package com.redhat.bcaapi;

import com.google.common.collect.ImmutableMap;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


@SpringBootApplication
public class BcaApiApplication {

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

            // Passbooks Headers
            from("servlet:///passbooks/headers")
                    .doTry()
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        .to("{{passbookHeadersEP}}?throwExceptionOnFailure=true")
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
                        .to("{{bcaLoginEP}}?throwExceptionOnFailure=true")
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

            // Agreement Inquiry
            from("servlet:///or-trx-agreement")
                    .process(new AggreementInquiryRequestTranformers())
                    .setHeader("NewHttpQuery", simple("${header.CamelHttpQuery}"))
                    .doTry()
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        .setHeader(Exchange.HTTP_METHOD, simple("GET"))
                        .setHeader(Exchange.HTTP_QUERY, simple("${header.NewHttpQuery}"))
                        .to("{{bcaLoginEP}}?throwExceptionOnFailure=true")
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

              from("servlet:///vaBillPresent")
                    .process(new Json2XmlReRequestTranformers())
                    .doTry()
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        .to("{{vaBillPresentmentEP}}?throwExceptionOnFailure=true")
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

            from("servlet:///proxy?matchOnUriPrefix=true")
                    .to("direct:backendSystem");

            from( "direct:backendSystem")
                    .log("Request received.")
                    .process(new JsonRequestTranformers())
                    .doTry()
                        .removeHeaders("CamelHttp*") // similar to adding param bridgeEndpoint=true in uri
                        //.to("http://www.mocky.io/v2/5b0580543200000b2aebf958")
                        //.to("http://www.mocky.io/v2/5b056e29320000ea1cebf8cc")
                        .to("http://www.mocky.io/v2/5b0598473200000b2aebf991?throwExceptionOnFailure=true")
                        //.to("https://httpstat.us/500?throwExceptionOnFailure=true")
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



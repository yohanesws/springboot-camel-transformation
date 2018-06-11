Run `mvn spring-boot:run`

Test URL:
```
curl -kv     http://localhost:8080/camel/...
```

Example:
```
cd curl-resources
curl -kv --header "Content-Type: application/json"   --request POST -H "X-XYZ-ClientID: 3FE00D67763F443582A2E97F27E7E8E1" -d @agreementInquiry.json http://localhost:8080/camel/or-trx-agreement

```


This project is trying to inline with Red Hat Fuse 7.0 components.
https://access.redhat.com/articles/348423
```
Red Hat Fuse 7.0
The following community components have been integrated into Red Hat Fuse 7.0.

Component               Version[1]
----------------------- ---------------
Spring Boot	            1.5.12.RELEASE
Apache Karaf	        4.2.0
Karaf Maven Plugin	    4.2.0
Apache Camel	        2.21.0
Wildfly Camel	        5.1.0
Apache CXF	            3.1.11
Hawtio	                2.0.0
Hibernate	            5.1.10
Fabric8	                3.0.11
Fabric8 Maven Plugin	3.5.33
```
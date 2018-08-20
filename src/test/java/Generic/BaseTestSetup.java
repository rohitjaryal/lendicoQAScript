package Generic;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Parameters;


public class BaseTestSetup {

    protected ResponseSpecification responseSpecification;
    protected RequestSpecification requestSpecification;

    @Parameters({"domainName"})
    @BeforeSuite
    public void setUp(String domainName) {
        RestAssured.baseURI = domainName;
        ResponseSpecBuilder builder = new ResponseSpecBuilder();
        builder.expectStatusCode(200);
        builder.expectContentType(ContentType.JSON);
        responseSpecification= builder.build();
        RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
        requestSpecBuilder.setBaseUri(domainName);
        requestSpecification = requestSpecBuilder.build();
        requestSpecification.log().all();
    }

    @AfterSuite
    public void tearDown() {

    }
}

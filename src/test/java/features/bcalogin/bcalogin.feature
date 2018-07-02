Feature: Testing BCA-Login REST API

  Background:
    * url baseUrl
    * configure logPrettyRequest = true
    * configure logPrettyResponse = true
    * def user = 'someone1234'
    * def password = 'AABBCCDDEEFFGG'


  Scenario: Testing valid GET endpoint
    Given url 'http://localhost:8080/camel/health'
    When method GET
    Then status 200

  Scenario: Test BCA Login
    Given url baseUrl + '/auth/Login'
    And request read('bcaLogin.json')
    When method POST
    Then status == 200
    And match response = {"AuthCode":"[0-9A-Z]{32}","ExpiredDate":"#notnull"}
    And print 'response: ', response


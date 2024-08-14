/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.oauth.endpoint.user.impl;

import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.oauth.endpoint.util.EndpointUtil;
import org.wso2.carbon.identity.oauth.user.UserInfoEndpointException;
import org.wso2.carbon.identity.oauth2.OAuth2TokenValidationService;
import org.wso2.carbon.identity.oauth2.dto.OAuth2TokenValidationResponseDTO;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

@Listeners(MockitoTestNGListener.class)
public class UserInfoISAccessTokenValidatorTest {

    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private OAuth2TokenValidationService oAuth2TokenValidationService;
    private UserInforRequestDefaultValidator userInforRequestDefaultValidator;
    private UserInfoISAccessTokenValidator userInfoISAccessTokenValidator;
    private final String token = "ZWx1c3VhcmlvOnlsYWNsYXZl";
    private final String basicAuthHeader = "Bearer " + token;
    private static String contentTypeHeaderValue = "application/x-www-form-urlencoded";

    @BeforeClass
    public void setup() {

        userInforRequestDefaultValidator = new UserInforRequestDefaultValidator();
        userInfoISAccessTokenValidator = new UserInfoISAccessTokenValidator();
    }

    @Test
    public void testValidateToken() throws Exception {

        prepareHttpServletRequest(basicAuthHeader, null);
        assertEquals(basicAuthHeader.split(" ")[1], userInforRequestDefaultValidator.validateRequest
                (httpServletRequest));
    }

    @DataProvider
    public Object[][] getInvalidAuthorizations() {

        return new Object[][]{
                {token, null},
                {"Bearer", null},
                {null, "application/text"},
                {null, ""},
        };
    }

    @Test(dataProvider = "getInvalidAuthorizations", expectedExceptions = UserInfoEndpointException.class)
    public void testValidateTokenInvalidAuthorization(String authorization, String contentType) throws Exception {

        prepareHttpServletRequest(authorization, contentType);
        userInforRequestDefaultValidator.validateRequest(httpServletRequest);
    }

    @DataProvider
    public Object[][] requestBodyWithNonASCII() {

        return new Object[][]{
                {contentTypeHeaderValue, "access_token=" + "¥" + token, token},
                {contentTypeHeaderValue, "access_token=" + "§" + token +
                        "&someOtherParam=value", token},
                {contentTypeHeaderValue, "otherParam=value2©&access_token=" + token +
                        "&someOtherParam=value", token},
        };
    }

    @DataProvider
    public Object[][] getTokens() {

        return new Object[][] {
                {"48544572-a796-3d42-a571-505bc609acd8"},
        };
    }

    @Test(dataProvider = "getTokens", expectedExceptions = UserInfoEndpointException.class)
    public void testTokenValidation(String accessTokenIdentifier) throws Exception {

        prepareOAuth2TokenValidationService();

        try (MockedStatic<EndpointUtil> endpointUtil = mockStatic(EndpointUtil.class)) {
            endpointUtil.when(EndpointUtil::getOAuth2TokenValidationService).thenReturn(oAuth2TokenValidationService);

            OAuth2TokenValidationResponseDTO responseDTO = userInfoISAccessTokenValidator
                    .validateToken(accessTokenIdentifier);
            assertEquals(responseDTO.getAuthorizationContextToken().getTokenString(), accessTokenIdentifier);
        }
    }

    @Test(expectedExceptions = UserInfoEndpointException.class)
    public void testValidateTokenWithWrongInputStream() throws Exception {

        prepareHttpServletRequest(null, contentTypeHeaderValue);

        when(httpServletRequest.getInputStream()).thenThrow(new IOException());

        userInforRequestDefaultValidator.validateRequest(httpServletRequest);
    }

    private void prepareOAuth2TokenValidationService() {

        when(oAuth2TokenValidationService.validate(any()))
                .thenReturn(new OAuth2TokenValidationResponseDTO());
    }

    private void prepareHttpServletRequest(String authorization, String contentType) {

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        lenient().when(httpServletRequest.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentType);
    }

    @DataProvider
    public Object[][] requestBody() {

        return new Object[][]{{contentTypeHeaderValue, "", null}, {contentTypeHeaderValue, null, null},
                {contentTypeHeaderValue, "access_token=" + token, token},
                {contentTypeHeaderValue, "access_token=" + token + "&someOtherParam=value", token},
                {contentTypeHeaderValue, "otherParam=value2&access_token=" + token + "&someOtherParam=value", token}};
    }

    @Test(dataProvider = "requestBody")
    public void testValidateTokenWithRequestBodySuccess(String contentType, String requestBody, String expected)
            throws Exception {

        String token = testValidateTokenWithRequestBody(contentType, requestBody, true);
        assertEquals(token, expected, "Expected token did not receive");
    }

    private String testValidateTokenWithRequestBody(String contentType, String requestBody, boolean mockScanner)
            throws Exception {

        prepareHttpServletRequest(null, contentType);
        if (mockScanner) {
            ServletInputStream inputStream = new ServletInputStream() {
                private InputStream stream =
                        new ByteArrayInputStream(requestBody == null ? "".getBytes() : requestBody.getBytes());

                @Override
                public int read() throws IOException {

                    return stream.read();
                }
            };
            doReturn(inputStream).when(httpServletRequest).getInputStream();
        } else {
            when(httpServletRequest.getInputStream()).thenThrow(new IOException());
        }
        return userInforRequestDefaultValidator.validateRequest(httpServletRequest);
    }
}

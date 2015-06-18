/*******************************************************************************
 *     Cloud Foundry 
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUserProvisioning;
import org.cloudfoundry.identity.uaa.scim.endpoints.PasswordResetEndpoints;
import org.cloudfoundry.identity.uaa.scim.exception.InvalidPasswordException;
import org.cloudfoundry.identity.uaa.scim.exception.ScimResourceNotFoundException;
import org.cloudfoundry.identity.uaa.scim.validate.PasswordValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class UaaChangePasswordServiceTest {
    private UaaChangePasswordService subject;

    private ScimUserProvisioning scimUserProvisioning;
    private PasswordValidator passwordValidator;

    @Before
    public void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        scimUserProvisioning = mock(ScimUserProvisioning.class);
        passwordValidator = mock(PasswordValidator.class);
        subject = new UaaChangePasswordService(scimUserProvisioning, passwordValidator);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test(expected = BadCredentialsException.class)
    public void testChangePasswordWithNoCurrentPasswordOrUsername() throws Exception {
        subject.changePassword(null, null, "newPassword");
    }

    @Test(expected = InvalidPasswordException.class)
    public void testChangePasswordWithInvalidNewPassword() throws Exception {
        when(passwordValidator.validate("invPawd")).thenThrow(new InvalidPasswordException(""));
        subject.changePassword("username", "currentPassword", "invPawd");
    }

    @Test(expected = ScimResourceNotFoundException.class)
    public void testChangePasswordWithUserNotFound() {
        List<ScimUser> results = Collections.emptyList();
        when(passwordValidator.validate("validPassword")).thenReturn(null);
        when(scimUserProvisioning.query(anyString())).thenReturn(results);
        subject.changePassword("username", "currentPassword", "validPassword");
        verify(passwordValidator).validate("validPassword");
        verify(scimUserProvisioning).query(anyString());
    }

    @Test
    public void testChangePassword() {
        ScimUser.Email email = new ScimUser.Email();
        email.setValue("username@test.com");
        ScimUser user = new ScimUser("id", "username", "givenName", "familyName");
        user.setEmails(Collections.singletonList(email));
        List<ScimUser> results = Collections.singletonList(user);
        when(passwordValidator.validate("validPassword")).thenReturn(null);
        when(scimUserProvisioning.query(anyString())).thenReturn(results);
        subject.changePassword("username", "currentPassword", "validPassword");
        verify(passwordValidator).validate("validPassword");
        verify(scimUserProvisioning).query(anyString());
        verify(scimUserProvisioning).changePassword("username", "currentPassword", "validPassword");
    }
}

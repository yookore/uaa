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
package org.cloudfoundry.identity.uaa.scim.endpoints;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.codestore.ExpiringCode;
import org.cloudfoundry.identity.uaa.codestore.ExpiringCodeStore;
import org.cloudfoundry.identity.uaa.error.ConvertingExceptionView;
import org.cloudfoundry.identity.uaa.error.ExceptionReport;
import org.cloudfoundry.identity.uaa.password.event.PasswordChangeEvent;
import org.cloudfoundry.identity.uaa.password.event.PasswordChangeFailureEvent;
import org.cloudfoundry.identity.uaa.password.event.ResetPasswordRequestEvent;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUserProvisioning;
import org.cloudfoundry.identity.uaa.scim.exception.InvalidPasswordException;
import org.cloudfoundry.identity.uaa.scim.exception.ScimException;
import org.cloudfoundry.identity.uaa.scim.exception.ScimResourceNotFoundException;
import org.cloudfoundry.identity.uaa.scim.validate.PasswordValidator;
import org.cloudfoundry.identity.uaa.user.UaaUser;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.util.JsonUtils.JsonUtilException;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.View;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Controller
public class PasswordResetEndpoints implements ApplicationEventPublisherAware {

    public static final int PASSWORD_RESET_LIFETIME = 30 * 60 * 1000;
    private final ScimUserProvisioning scimUserProvisioning;
    private final ExpiringCodeStore expiringCodeStore;
    private ApplicationEventPublisher publisher;
    private PasswordValidator passwordValidator;
    private HttpMessageConverter<?>[] messageConverters = new RestTemplate().getMessageConverters().toArray(new HttpMessageConverter<?>[0]);

    public PasswordResetEndpoints(ScimUserProvisioning scimUserProvisioning, ExpiringCodeStore expiringCodeStore, PasswordValidator passwordValidator) {
        this.scimUserProvisioning = scimUserProvisioning;
        this.expiringCodeStore = expiringCodeStore;
        this.passwordValidator = passwordValidator;
    }

    public void setMessageConverters(HttpMessageConverter<?>[] messageConverters) {
        this.messageConverters = messageConverters;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @RequestMapping(value = "/password_resets", method = RequestMethod.POST)
    public ResponseEntity<Map<String,String>> resetPassword(@RequestBody String email) throws IOException {
        String jsonEmail = JsonUtils.writeValueAsString(email);
        Map<String,String> response = new HashMap<>();
        List<ScimUser> results = scimUserProvisioning.query("userName eq " + jsonEmail + " and origin eq \"" + Origin.UAA + "\"");
        if (results.isEmpty()) {
            results = scimUserProvisioning.query("userName eq " + jsonEmail);
            if (results.isEmpty()) {
                return new ResponseEntity<>(NOT_FOUND);
            } else {
                response.put("user_id", results.get(0).getId());
                return new ResponseEntity<>(response, CONFLICT);
            }
        }
        ScimUser scimUser = results.get(0);
        PasswordChange change = new PasswordChange(scimUser.getId(), scimUser.getUserName());
        String code = expiringCodeStore.generateCode(JsonUtils.writeValueAsString(change), new Timestamp(System.currentTimeMillis() + PASSWORD_RESET_LIFETIME)).getCode();
        publish(new ResetPasswordRequestEvent(email, code, SecurityContextHolder.getContext().getAuthentication()));
        response.put("code", code);
        response.put("user_id", scimUser.getId());
        return new ResponseEntity<>(response, CREATED);
    }

    @RequestMapping(value = "/password_change", method = RequestMethod.POST)
    public ResponseEntity<Map<String,String>> changePassword(@RequestBody PasswordChange passwordChange) {
        passwordValidator.validate(passwordChange.getNewPassword());
        ResponseEntity<Map<String,String>> responseEntity;
        if (isCodeAuthenticatedChange(passwordChange)) {
            responseEntity = changePasswordCodeAuthenticated(passwordChange);
        } else {
            responseEntity = new ResponseEntity<>(BAD_REQUEST);
        }
        return responseEntity;
    }

    private boolean isCodeAuthenticatedChange(PasswordChange passwordChange) {
        return passwordChange.getCode() != null && passwordChange.getCurrentPassword() == null && passwordChange.getUsername() == null;
    }

    protected ResponseEntity<Map<String,String>> changePasswordCodeAuthenticated(PasswordChange passwordChange) {
        ExpiringCode expiringCode = expiringCodeStore.retrieveCode(passwordChange.getCode());
        if (expiringCode == null) {
            return new ResponseEntity<>(BAD_REQUEST);
        }
        String userId;
        String userName = null;
        try {
            PasswordChange change = JsonUtils.readValue(expiringCode.getData(), PasswordChange.class);
            userId = change.getUserId();
            userName = change.getUsername();
        } catch (JsonUtilException x) {
            userId = expiringCode.getData();
        }
        ScimUser user = scimUserProvisioning.retrieve(userId);
        Map<String,String> userInfo = new HashMap<>();
        try {
            if (isUserModified(user, expiringCode.getExpiresAt(), userName)) {
                return new ResponseEntity<>(BAD_REQUEST);
            }
            if (!user.isVerified()) {
                scimUserProvisioning.verifyUser(userId, -1);
            }
            scimUserProvisioning.changePassword(userId, null, passwordChange.getNewPassword());
            publish(new PasswordChangeEvent("Password changed", getUaaUser(user), SecurityContextHolder.getContext().getAuthentication()));
            userInfo.put("user_id", user.getId());
            userInfo.put("username", user.getUserName());
            userInfo.put("email", user.getPrimaryEmail());
            return new ResponseEntity<>(userInfo, OK);
        } catch (BadCredentialsException x) {
            publish(new PasswordChangeFailureEvent(x.getMessage(), getUaaUser(user), SecurityContextHolder.getContext().getAuthentication()));
            return new ResponseEntity<>(UNAUTHORIZED);
        } catch (ScimResourceNotFoundException x) {
            publish(new PasswordChangeFailureEvent(x.getMessage(), getUaaUser(user), SecurityContextHolder.getContext().getAuthentication()));
            return new ResponseEntity<>(NOT_FOUND);
        } catch (Exception x) {
            publish(new PasswordChangeFailureEvent(x.getMessage(), getUaaUser(user), SecurityContextHolder.getContext().getAuthentication()));
            return new ResponseEntity<>(INTERNAL_SERVER_ERROR);
        }
    }

    protected boolean isUserModified(ScimUser user, Timestamp expiresAt, String userName) {
        if (userName!=null) {
            return ! userName.equals(user.getUserName());
        }
        //left over from when all we stored in the code was the user ID
        //here we will check the timestamp
        //TODO - REMOVE THIS IN FUTURE RELEASE, ALL LINKS HAVE BEEN EXPIRED (except test created ones)
        long codeCreated = expiresAt.getTime() - PASSWORD_RESET_LIFETIME;
        long userModified = user.getMeta().getLastModified().getTime();
        return (userModified > codeCreated);
    }

    @Deprecated
    protected UaaUser getUaaUser(ScimUser scimUser) {
        Date today = new Date();
        return new UaaUser(scimUser.getId(), scimUser.getUserName(), "N/A", scimUser.getPrimaryEmail(), null,
            scimUser.getGivenName(),
            scimUser.getFamilyName(), today, today,
            scimUser.getOrigin(), scimUser.getExternalId(), scimUser.isVerified(), scimUser.getZoneId(), scimUser.getSalt(),
            scimUser.getPasswordLastModified());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public View handleException(InvalidPasswordException t) throws ScimException {
        return new ConvertingExceptionView(new ResponseEntity<>(new ExceptionReport(
                t, false), UNPROCESSABLE_ENTITY),
                messageConverters);
    }

    public static class PasswordChange {
        public PasswordChange() {}

        public PasswordChange(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("username")
        private String username;

        @JsonProperty("code")
        private String code;

        @JsonProperty("current_password")
        private String currentPassword;

        @JsonProperty("new_password")
        private String newPassword;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    protected void publish(ApplicationEvent event) {
        if (publisher!=null) {
            publisher.publishEvent(event);
        }
    }
}

package org.cloudfoundry.identity.uaa.test;

import org.cloudfoundry.identity.uaa.config.PasswordPolicy;
import org.cloudfoundry.identity.uaa.scim.validate.PasswordPolicyResolver;
import org.cloudfoundry.identity.uaa.scim.validate.UaaPasswordPolicyResolver;
import org.cloudfoundry.identity.uaa.scim.validate.UaaPasswordPolicyValidator;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*******************************************************************************
 * Cloud Foundry
 * Copyright (c) [2009-2015] Pivotal Software, Inc. All Rights Reserved.
 * <p/>
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 * <p/>
 * This product includes a number of subcomponents with
 * separate copyright notices and license terms. Your use of these
 * subcomponents is subject to the terms and conditions of the
 * subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
abstract public class RunWithRealPasswordPolicy {
    private final ApplicationContext applicationContext;

    public RunWithRealPasswordPolicy(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void run() throws Exception {
        UaaPasswordPolicyValidator validator = applicationContext.getBean(UaaPasswordPolicyValidator.class);
        PasswordPolicyResolver resolver = mock(PasswordPolicyResolver.class);
        when(resolver.resolve()).thenReturn(new PasswordPolicy(6, 128, 1, 1, 1, 0, null, 0));
        try {
            validator.setPasswordPolicyResolver(resolver);
            methodToRun();
        } finally {
            validator.setPasswordPolicyResolver(applicationContext.getBean(UaaPasswordPolicyResolver.class));
        }
    }

    public abstract void methodToRun() throws Exception;
}


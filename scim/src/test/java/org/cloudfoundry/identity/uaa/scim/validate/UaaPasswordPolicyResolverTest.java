package org.cloudfoundry.identity.uaa.scim.validate;

import junit.framework.Assert;
import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.config.PasswordPolicy;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityProvider;
import org.cloudfoundry.identity.uaa.zone.IdentityProviderProvisioning;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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

@RunWith(MockitoJUnitRunner.class)
public class UaaPasswordPolicyResolverTest {

    @Mock
    private IdentityProviderProvisioning provisioning;
    private PasswordPolicyResolver resolver;

    @Before
    public void setUp() {
        PasswordPolicy passwordPolicy = new PasswordPolicy(19, 28, 1, 1, 1, 1, null, 6);
        IdentityZoneHolder.set(IdentityZone.getUaa());

        IdentityProvider internalIDP = new IdentityProvider();
        Map<String, Object> config = new HashMap<>();
        config.put(PasswordPolicy.PASSWORD_POLICY_FIELD, JsonUtils.convertValue(passwordPolicy, Map.class));
        internalIDP.setConfig(JsonUtils.writeValueAsString(config));

        when(provisioning.retrieveByOrigin(Origin.UAA, IdentityZone.getUaa().getId()))
                .thenReturn(internalIDP);

        resolver = new UaaPasswordPolicyResolver(provisioning);
    }

    @Test
    public void testResolve() {
        PasswordPolicy actualResolver = resolver.resolve();
        assertEquals(19, actualResolver.getMinLength());
        assertEquals(28, actualResolver.getMaxLength());
    }
}
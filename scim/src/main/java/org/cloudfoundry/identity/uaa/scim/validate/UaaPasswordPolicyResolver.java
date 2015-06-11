package org.cloudfoundry.identity.uaa.scim.validate;

import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.config.PasswordPolicy;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityProvider;
import org.cloudfoundry.identity.uaa.zone.IdentityProviderProvisioning;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;

import java.util.Map;

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
public class UaaPasswordPolicyResolver implements PasswordPolicyResolver {

    private final IdentityProviderProvisioning provisioning;

    public UaaPasswordPolicyResolver(IdentityProviderProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public PasswordPolicy resolve() {
        IdentityProvider idp = provisioning.retrieveByOrigin(Origin.UAA, IdentityZoneHolder.get().getId());
        if (idp==null || idp.getConfig()==null) {
            //no config stored
            return null;
        }

        Map<String, Object> configMap = JsonUtils.readValue(idp.getConfig(), Map.class);
        Object policyObject = configMap.get(PasswordPolicy.PASSWORD_POLICY_FIELD);
        if (policyObject==null) {
            //no policy stored
            return null;
        }

        PasswordPolicy policy = JsonUtils.convertValue(policyObject, PasswordPolicy.class);
        if (policy==null) {
            //no policy stored
            return null;
        }
        return policy;
    }

}

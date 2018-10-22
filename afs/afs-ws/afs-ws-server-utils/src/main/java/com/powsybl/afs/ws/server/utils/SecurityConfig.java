/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.afs.ws.server.utils;

import com.powsybl.commons.Versionable;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.config.VersionConfig;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SecurityConfig implements Versionable {

    private static final String CONFIG_MODULE_NAME = "security";

    private static final long DEFAULT_TOKEN_VALIDITY = 3600L; // minutes
    private static final boolean DEFAULT_SKIP_TOKEN_VALIDITY_CHECK = true;

    private static final String DEFAULT_CONFIG_VERSION = "1.0";

    private VersionConfig version = new VersionConfig(DEFAULT_CONFIG_VERSION);

    private long tokenValidity;

    private boolean skipTokenValidityCheck;

    private static long checkTokenValidity(long tokenValidity) {
        if (tokenValidity < 1) {
            throw new IllegalArgumentException("Invalid token validity");
        }
        return tokenValidity;
    }

    public static SecurityConfig load(PlatformConfig platformConfig) {
        Objects.requireNonNull(platformConfig);
        return platformConfig.getOptionalModuleConfig(CONFIG_MODULE_NAME)
                .map(config ->
                        config.getOptionalStringProperty("version")
                                .map(v -> new SecurityConfig(new VersionConfig(v), config.getOptionalLongProperty("token-validity")
                                        .orElse(DEFAULT_TOKEN_VALIDITY),
                                        config.getOptionalBooleanProperty("skip-token-validity-check")
                                                .orElse(DEFAULT_SKIP_TOKEN_VALIDITY_CHECK)))
                                .orElseGet(() -> new SecurityConfig(config.getOptionalLongProperty("token-validity")
                                        .orElse(DEFAULT_TOKEN_VALIDITY),
                                        config.getOptionalBooleanProperty("skip-token-validity-check")
                                                .orElse(DEFAULT_SKIP_TOKEN_VALIDITY_CHECK))))
                .orElseGet(() -> new SecurityConfig(DEFAULT_TOKEN_VALIDITY, DEFAULT_SKIP_TOKEN_VALIDITY_CHECK));
    }

    public SecurityConfig(long tokenValidity, boolean skipTokenValidityCheck) {
        this.tokenValidity = checkTokenValidity(tokenValidity);
        this.skipTokenValidityCheck = skipTokenValidityCheck;
    }

    public SecurityConfig(VersionConfig version, long tokenValidity, boolean skipTokenValidityCheck) {
        this(tokenValidity, skipTokenValidityCheck);
        this.version = version;
    }

    public long getTokenValidity() {
        return tokenValidity;
    }

    public void setTokenValidity(long tokenValidity) {
        this.tokenValidity = checkTokenValidity(tokenValidity);
    }

    public boolean isSkipTokenValidityCheck() {
        return skipTokenValidityCheck;
    }

    public void setSkipTokenValidityCheck(boolean skipTokenValidityCheck) {
        this.skipTokenValidityCheck = skipTokenValidityCheck;
    }

    @Override
    public String getName() {
        return CONFIG_MODULE_NAME;
    }

    @Override
    public String getVersion() {
        return this.version.toString();
    }
}

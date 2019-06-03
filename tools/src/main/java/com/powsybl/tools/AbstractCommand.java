/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.tools;

/**
 * @author Miora Ralambotiana <miora.ralambotiana at rte-france.com>
 */
public abstract class AbstractCommand implements Command {

    private final String name;
    private final String theme;
    private final String description;

    protected AbstractCommand(String name, String theme, String description) {
        this.name = name;
        this.theme = theme;
        this.description = description;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTheme() {
        return theme;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUsageFooter() {
        return null;
    }
}

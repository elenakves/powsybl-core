/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.action.dsl;

public class DeltaTapDataTest {

    private int iniTapPosition;
    private int expectedTapPosition;
    private int deltaTap;
    private String testName;

    public DeltaTapDataTest() {
    }

    public DeltaTapDataTest(int iniTapPosition, int expectedTapPosition, int deltaTap, String testName) {
        this.iniTapPosition = iniTapPosition;
        this.expectedTapPosition = expectedTapPosition;
        this.deltaTap = deltaTap;
        this.testName = testName;
    }

    public int getIniTapPosition() {
        return iniTapPosition;
    }

    public int getExpectedTapPosition() {
        return expectedTapPosition;
    }

    public int getDeltaTap() {
        return deltaTap;
    }

    public String getTestName() {
        return testName;
    }

    public void setIniTapPosition(int iniTapPosition) {
        this.iniTapPosition = iniTapPosition;
    }

    public void setExpectedTapPosition(int expectedTapPosition) {
        this.expectedTapPosition = expectedTapPosition;
    }

    public void setDeltaTap(int deltaTap) {
        this.deltaTap = deltaTap;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}

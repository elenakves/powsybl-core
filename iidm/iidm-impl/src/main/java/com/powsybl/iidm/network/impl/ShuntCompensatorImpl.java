/**
 * Copyright (c) 2016, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.impl;

import com.powsybl.commons.util.trove.TBooleanArrayList;
import com.powsybl.iidm.network.ConnectableType;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.impl.util.Ref;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class ShuntCompensatorImpl extends AbstractConnectable<ShuntCompensator> implements ShuntCompensator {

    private final Ref<? extends VariantManagerHolder> network;

    /* susceptance per section */
    private double bPerSection;

    /* the maximum number of section */
    private int maximumSectionCount;

    /* the regulating terminal */
    private TerminalExt regulatingTerminal;

    // attributes depending on the variant

    /* the current number of section switched on */
    private final TIntArrayList currentSectionCount;

    /* the regulating status */
    private final TBooleanArrayList regulating;

    /* the target voltage value */
    private final TDoubleArrayList targetV;

    /* the target deadband */
    private final TDoubleArrayList targetDeadband;

    ShuntCompensatorImpl(Ref<? extends VariantManagerHolder> network,
                         String id, String name, double bPerSection, int maximumSectionCount,
                         int currentSectionCount, TerminalExt regulatingTerminal, boolean regulating,
                         double targetV, double targetDeadband) {
        super(id, name);
        this.network = network;
        this.bPerSection = bPerSection;
        this.maximumSectionCount = maximumSectionCount;
        this.regulatingTerminal = regulatingTerminal;
        int variantArraySize = network.get().getVariantManager().getVariantArraySize();
        this.currentSectionCount = new TIntArrayList(variantArraySize);
        this.regulating = new TBooleanArrayList(variantArraySize);
        this.targetV = new TDoubleArrayList(variantArraySize);
        this.targetDeadband = new TDoubleArrayList(variantArraySize);
        for (int i = 0; i < variantArraySize; i++) {
            this.currentSectionCount.add(currentSectionCount);
            this.regulating.add(regulating);
            this.targetV.add(targetV);
            this.targetDeadband.add(targetDeadband);
        }
    }

    @Override
    public ConnectableType getType() {
        return ConnectableType.SHUNT_COMPENSATOR;
    }

    @Override
    public TerminalExt getTerminal() {
        return terminals.get(0);
    }

    @Override
    public double getbPerSection() {
        return bPerSection;
    }

    @Override
    public ShuntCompensatorImpl setbPerSection(double bPerSection) {
        ValidationUtil.checkbPerSection(this, bPerSection);
        double oldValue = this.bPerSection;
        this.bPerSection = bPerSection;
        notifyUpdate("bPerSection", oldValue, bPerSection);
        return this;
    }

    @Override
    public int getMaximumSectionCount() {
        return maximumSectionCount;
    }

    @Override
    public ShuntCompensatorImpl setMaximumSectionCount(int maximumSectionCount) {
        ValidationUtil.checkSections(this, getCurrentSectionCount(), maximumSectionCount);
        int oldValue = this.maximumSectionCount;
        this.maximumSectionCount = maximumSectionCount;
        notifyUpdate("maximumSectionCount", oldValue, maximumSectionCount);
        return this;
    }

    @Override
    public int getCurrentSectionCount() {
        return currentSectionCount.get(network.get().getVariantIndex());
    }

    @Override
    public ShuntCompensatorImpl setCurrentSectionCount(int currentSectionCount) {
        ValidationUtil.checkSections(this, currentSectionCount, maximumSectionCount);
        int variantIndex = network.get().getVariantIndex();
        int oldValue = this.currentSectionCount.set(variantIndex, currentSectionCount);
        String variantId = network.get().getVariantManager().getVariantId(variantIndex);
        notifyUpdate("currentSectionCount", variantId, oldValue, currentSectionCount);
        return this;
    }

    @Override
    public double getCurrentB() {
        return bPerSection * getCurrentSectionCount();
    }

    @Override
    public double getMaximumB() {
        return bPerSection * maximumSectionCount;
    }

    @Override
    public TerminalExt getRegulatingTerminal() {
        return regulatingTerminal;
    }

    @Override
    public ShuntCompensatorImpl setRegulatingTerminal(Terminal regulatingTerminal) {
        ValidationUtil.checkRegulatingTerminal(this, (TerminalExt) regulatingTerminal, getNetwork());
        Terminal oldValue = this.regulatingTerminal;
        this.regulatingTerminal = regulatingTerminal != null ? (TerminalExt) regulatingTerminal : getTerminal();
        notifyUpdate("regulatingTerminal", oldValue, regulatingTerminal);
        return this;
    }

    @Override
    public boolean isRegulating() {
        return regulating.get(network.get().getVariantIndex());
    }

    @Override
    public ShuntCompensatorImpl setRegulating(boolean regulating) {
        int variantIndex = network.get().getVariantIndex();
        ValidationUtil.checkVoltageControl(this, regulating, targetV.get(variantIndex));
        boolean oldValue = this.regulating.set(variantIndex, regulating);
        String variantId = network.get().getVariantManager().getVariantId(variantIndex);
        notifyUpdate("regulating", variantId, oldValue, regulating);
        return this;
    }

    @Override
    public double getTargetV() {
        return targetV.get(network.get().getVariantIndex());
    }

    @Override
    public ShuntCompensatorImpl setTargetV(double targetV) {
        int variantIndex = network.get().getVariantIndex();
        ValidationUtil.checkVoltageControl(this, regulating.get(variantIndex), targetV);
        double oldValue = this.targetV.set(variantIndex, targetV);
        String variantId = network.get().getVariantManager().getVariantId(variantIndex);
        notifyUpdate("targetV", variantId, oldValue, targetV);
        return this;
    }

    @Override
    public double getTargetDeadband() {
        return targetDeadband.get(network.get().getVariantIndex());
    }

    @Override
    public ShuntCompensatorImpl setTargetDeadband(double targetDeadband) {
        double realTargetDeadband = Double.isNaN(targetDeadband) ? 0 : targetDeadband;
        ValidationUtil.checkTargetDeadband(this, realTargetDeadband);
        int variantIndex = network.get().getVariantIndex();
        double oldValue = this.targetDeadband.set(variantIndex, realTargetDeadband);
        String variantId = network.get().getVariantManager().getVariantId(variantIndex);
        notifyUpdate("targetDeadband", variantId, oldValue, realTargetDeadband);
        return this;
    }

    @Override
    public void extendVariantArraySize(int initVariantArraySize, int number, int sourceIndex) {
        super.extendVariantArraySize(initVariantArraySize, number, sourceIndex);
        currentSectionCount.ensureCapacity(currentSectionCount.size() + number);
        regulating.ensureCapacity(regulating.size() + number);
        targetV.ensureCapacity(targetV.size() + number);
        targetDeadband.ensureCapacity(targetDeadband.size() + number);
        for (int i = 0; i < number; i++) {
            currentSectionCount.add(currentSectionCount.get(sourceIndex));
            regulating.add(regulating.get(sourceIndex));
            targetV.add(targetV.get(sourceIndex));
            targetDeadband.add(targetDeadband.get(sourceIndex));
        }
    }

    @Override
    public void reduceVariantArraySize(int number) {
        super.reduceVariantArraySize(number);
        currentSectionCount.remove(currentSectionCount.size() - number, number);
        regulating.remove(regulating.size() - number, number);
        targetV.remove(targetV.size() - number, number);
        targetDeadband.remove(targetDeadband.size() - number, number);
    }

    @Override
    public void deleteVariantArrayElement(int index) {
        super.deleteVariantArrayElement(index);
        // nothing to do
    }

    @Override
    public void allocateVariantArrayElement(int[] indexes, final int sourceIndex) {
        super.allocateVariantArrayElement(indexes, sourceIndex);
        for (int index : indexes) {
            currentSectionCount.set(index, currentSectionCount.get(sourceIndex));
            regulating.set(index, regulating.get(sourceIndex));
            targetV.set(index, targetV.get(sourceIndex));
            targetDeadband.set(index, targetDeadband.get(sourceIndex));
        }
    }

    @Override
    protected String getTypeDescription() {
        return "Shunt compensator";
    }

}

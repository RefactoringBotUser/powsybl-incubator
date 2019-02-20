/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.cgmes.dl.conversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.powsybl.cgmes.iidm.Networks;
import com.powsybl.cgmes.iidm.extensions.dl.CouplingDeviseDiagramData;
import com.powsybl.cgmes.iidm.extensions.dl.DiagramTerminal;
import com.powsybl.cgmes.iidm.extensions.dl.InjectionDiagramData;
import com.powsybl.cgmes.iidm.extensions.dl.LineDiagramData;
import com.powsybl.cgmes.iidm.extensions.dl.NodeDiagramData;
import com.powsybl.cgmes.iidm.extensions.dl.ThreeWindingsTransformerDiagramData;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 *
 * @author Massimo Ferraro <massimo.ferraro@techrain.eu>
 */
public class CgmesDLImporterTest extends AbstractCgmesDLTest {

    private CgmesDLModel cgmesDLModel;

    @Before
    public void setUp() {
        super.setUp();
        cgmesDLModel = Mockito.mock(CgmesDLModel.class);
        Mockito.when(cgmesDLModel.getTerminalsDiagramData()).thenReturn(terminalsPropertyBags);
        Mockito.when(cgmesDLModel.getBusesDiagramData()).thenReturn(busesPropertyBags);
        Mockito.when(cgmesDLModel.getBusbarsDiagramData()).thenReturn(busbarsPropertyBags);
        Mockito.when(cgmesDLModel.getLinesDiagramData()).thenReturn(linesPropertyBags);
        Mockito.when(cgmesDLModel.getGeneratorsDiagramData()).thenReturn(generatorsPropertyBags);
        Mockito.when(cgmesDLModel.getLoadsDiagramData()).thenReturn(loadsPropertyBags);
        Mockito.when(cgmesDLModel.getShuntsDiagramData()).thenReturn(shuntsPropertyBags);
        Mockito.when(cgmesDLModel.getSwitchesDiagramData()).thenReturn(switchesPropertyBags);
        Mockito.when(cgmesDLModel.getTransformersDiagramData()).thenReturn(tranformersPropertyBags);
        Mockito.when(cgmesDLModel.getHvdcLinesDiagramData()).thenReturn(hvdcLinesPropertyBags);
        Mockito.when(cgmesDLModel.getSvcsDiagramData()).thenReturn(svcsPropertyBags);
    }

    private <T> void checkDiagramData(NodeDiagramData<?> diagramData) {
        assertNotNull(diagramData);
        assertEquals(1, diagramData.getPoint1().getSeq(), 0);
        assertEquals(20, diagramData.getPoint1().getX(), 0);
        assertEquals(5, diagramData.getPoint1().getY(), 0);
        assertEquals(2, diagramData.getPoint2().getSeq(), 0);
        assertEquals(20, diagramData.getPoint2().getX(), 0);
        assertEquals(40, diagramData.getPoint2().getY(), 0);
    }

    @Test
    public void testBuses() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithBus(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        Bus bus = network.getVoltageLevel("VoltageLevel").getBusBreakerView().getBus("Bus");
        NodeDiagramData<Bus> busDiagramData = bus.getExtension(NodeDiagramData.class);

        checkDiagramData(busDiagramData);
    }

    @Test
    public void testBusbars() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithBusbar(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        BusbarSection busbar = network.getBusbarSection("Busbar");
        NodeDiagramData<BusbarSection> busDiagramData = busbar.getExtension(NodeDiagramData.class);

        checkDiagramData(busDiagramData);
    }

    private <T> void checkDiagramData(LineDiagramData<?> diagramData) {
        assertNotNull(diagramData);
        assertEquals(1, diagramData.getPoints().get(0).getSeq(), 0);
        assertEquals(20, diagramData.getPoints().get(0).getX(), 0);
        assertEquals(5, diagramData.getPoints().get(0).getY(), 0);
        assertEquals(2, diagramData.getPoints().get(1).getSeq(), 0);
        assertEquals(20, diagramData.getPoints().get(1).getX(), 0);
        assertEquals(40, diagramData.getPoints().get(1).getY(), 0);
        assertEquals(1, diagramData.getFirstPoint().getSeq(), 0);
        assertEquals(20, diagramData.getFirstPoint().getX(), 0);
        assertEquals(5, diagramData.getFirstPoint().getY(), 0);
        assertEquals(2, diagramData.getLastPoint().getSeq(), 0);
        assertEquals(20, diagramData.getLastPoint().getX(), 0);
        assertEquals(40, diagramData.getLastPoint().getY(), 0);
    }

    @Test
    public void testLines() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithLine(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        Line line = network.getLine("Line");
        LineDiagramData<Line> lineDiagramData = line.getExtension(LineDiagramData.class);

        checkDiagramData(lineDiagramData);
    }

    @Test
    public void testDanglingLines() {
        Mockito.when(cgmesDLModel.getLinesDiagramData()).thenReturn(danglingLinesPropertyBags);
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithDanglingLine(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        DanglingLine danglingLine = network.getDanglingLine("DanglingLine");
        LineDiagramData<DanglingLine> danglingLineDiagramData = danglingLine.getExtension(LineDiagramData.class);

        checkDiagramData(danglingLineDiagramData);
    }

    @Test
    public void testHvdcLines() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithHvdcLine(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        HvdcLine hdcvLine = network.getHvdcLine("HvdcLine");
        LineDiagramData<HvdcLine> hvdcLineDiagramData = hdcvLine.getExtension(LineDiagramData.class);

        checkDiagramData(hvdcLineDiagramData);
    }

    private <T> void checkDiagramData(InjectionDiagramData<?> diagramData) {
        assertNotNull(diagramData);
        assertEquals(0, diagramData.getPoint().getSeq(), 0);
        assertEquals(10, diagramData.getPoint().getX(), 0);
        assertEquals(10, diagramData.getPoint().getY(), 0);
        assertEquals(90, diagramData.getRotation(), 0);
        assertEquals(1, diagramData.getTerminalPoints().get(0).getSeq(), 0);
        assertEquals(2, diagramData.getTerminalPoints().get(0).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints().get(0).getY(), 0);
        assertEquals(2, diagramData.getTerminalPoints().get(1).getSeq(), 0);
        assertEquals(6, diagramData.getTerminalPoints().get(1).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints().get(1).getY(), 0);
    }

    @Test
    public void testGenerators() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithGenerator(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        Generator generator = network.getGenerator("Generator");
        InjectionDiagramData<Generator> generatorDiagramData = generator.getExtension(InjectionDiagramData.class);

        checkDiagramData(generatorDiagramData);
    }

    @Test
    public void testLoads() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithLoad(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        Load load = network.getLoad("Load");
        InjectionDiagramData<Load> loadDiagramData = load.getExtension(InjectionDiagramData.class);

        checkDiagramData(loadDiagramData);
    }

    @Test
    public void testShunts() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithShuntCompensator(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        ShuntCompensator shunt = network.getShuntCompensator("Shunt");
        InjectionDiagramData<ShuntCompensator> shuntDiagramData = shunt.getExtension(InjectionDiagramData.class);

        checkDiagramData(shuntDiagramData);
    }

    @Test
    public void testSvcs() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithStaticVarCompensator(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        StaticVarCompensator svc = network.getStaticVarCompensator("Svc");
        InjectionDiagramData<StaticVarCompensator> svcDiagramData = svc.getExtension(InjectionDiagramData.class);

        checkDiagramData(svcDiagramData);
    }

    private <T> void checkDiagramData(CouplingDeviseDiagramData<?> diagramData) {
        assertNotNull(diagramData);
        assertEquals(0, diagramData.getPoint().getSeq(), 0);
        assertEquals(10, diagramData.getPoint().getX(), 0);
        assertEquals(10, diagramData.getPoint().getY(), 0);
        assertEquals(90, diagramData.getRotation(), 0);
        assertEquals(1, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(0).getSeq(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(0).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(0).getY(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(1).getSeq(), 0);
        assertEquals(6, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(1).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(1).getY(), 0);
        assertEquals(1, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(0).getSeq(), 0);
        assertEquals(14, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(0).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(0).getY(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(1).getSeq(), 0);
        assertEquals(18, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(1).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(1).getY(), 0);
    }

    @Test
    public void testSwitches() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithSwitch(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        Switch sw = network.getSwitch("Switch");
        CouplingDeviseDiagramData<Switch> shuntDiagramData = sw.getExtension(CouplingDeviseDiagramData.class);

        checkDiagramData(shuntDiagramData);
    }

    @Test
    public void testTransformers() {
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithTwoWindingsTransformer(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformer("Transformer");
        CouplingDeviseDiagramData<TwoWindingsTransformer> transformerDiagramData = transformer.getExtension(CouplingDeviseDiagramData.class);

        checkDiagramData(transformerDiagramData);
    }

    private <T> void checkDiagramData(ThreeWindingsTransformerDiagramData diagramData) {
        assertNotNull(diagramData);
        assertEquals(0, diagramData.getPoint().getSeq(), 0);
        assertEquals(10, diagramData.getPoint().getX(), 0);
        assertEquals(13, diagramData.getPoint().getY(), 0);
        assertEquals(90, diagramData.getRotation(), 0);
        assertEquals(1, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(0).getSeq(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(0).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(0).getY(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(1).getSeq(), 0);
        assertEquals(6, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(1).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL1).get(1).getY(), 0);
        assertEquals(1, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(0).getSeq(), 0);
        assertEquals(14, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(0).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(0).getY(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(1).getSeq(), 0);
        assertEquals(18, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(1).getX(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL2).get(1).getY(), 0);
        assertEquals(1, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL3).get(0).getSeq(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL3).get(0).getX(), 0);
        assertEquals(16, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL3).get(0).getY(), 0);
        assertEquals(2, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL3).get(1).getSeq(), 0);
        assertEquals(10, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL3).get(1).getX(), 0);
        assertEquals(20, diagramData.getTerminalPoints(DiagramTerminal.TERMINAL3).get(1).getY(), 0);
    }

    @Test
    public void testTransformers3w() {
        Mockito.when(cgmesDLModel.getTransformersDiagramData()).thenReturn(tranformers3wPropertyBags);
        CgmesDLImporter cgmesDLImporter = new CgmesDLImporter(Networks.createNetworkWithThreeWindingsTransformer(), cgmesDLModel);
        cgmesDLImporter.importDLData();
        Network network = cgmesDLImporter.getNetworkWithDLData();
        ThreeWindingsTransformer transformer = network.getThreeWindingsTransformer("Transformer3w");
        ThreeWindingsTransformerDiagramData transformerDiagramData = transformer.getExtension(ThreeWindingsTransformerDiagramData.class);

        checkDiagramData(transformerDiagramData);
    }

}
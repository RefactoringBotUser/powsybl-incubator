/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.substationdiagram.layout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powsybl.substationdiagram.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Benoit Jeanson <benoit.jeanson at rte-france.com>
 * @author Nicolas Duchene
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */

// WE ASSUME THAT IT IS POSSIBLE TO STACK ALL CELLS AND BE ABLE TO ORGANISE THE VOLTAGELEVAL CONSITENTLY

public class PositionFree implements PositionFinder {

    @JsonIgnore
    private static final Logger LOGGER = LoggerFactory.getLogger(PositionFree.class);
    private static final Cell.Direction DEFAULTDIRECTION = Cell.Direction.TOP;

    private class Context {
        private final Graph graph;
        private final Map<BusNode, Integer> nodeToNb = new HashMap<>();
        private final Map<VerticalBusConnectionPattern, List<Cell>> vbcpToCells = new HashMap<>();
        private List<HorizontalChain> hChains;
        private final Map<BusNode, NodeBelonging> busToBelonging = new HashMap<>();
        private final List<ConnectedCluster> connectedClusters = new ArrayList<>();

        public Context(Graph graph) {
            this.graph = Objects.requireNonNull(graph);
        }
    }

    @Override
    public void buildLayout(Graph graph) {
        LOGGER.info("start BuildLayout");

        Context context = new Context(graph);

        indexBusPosition(context);
        initVbpcToCell(context);
        organizeWithInternCells(context);
//
//        newStructuralPosition(context);
//        initiateFeederPosition(context);

        graph.setMaxBusPosition();

    }

    private void indexBusPosition(Context context) {
        int i = 1;
        for (BusNode n : context.graph.getNodeBuses()
                .stream()
                .sorted(Comparator.comparing(BusNode::getId))
                .collect(Collectors.toList())) {
            context.nodeToNb.put(n, i);
            i++;
        }
    }

    private void initiateFeederPosition(Context context) {
        int i = 0;
        for (FeederNode feederNode : context.graph.getNodes().stream()
                .filter(node -> node.getType() == Node.NodeType.FEEDER)
                .map(FeederNode.class::cast)
                .sorted(Comparator.comparing(Node::getId))
                .collect(Collectors.toList())) {
            if (feederNode.getCell() != null) {
                feederNode.getCell().setDirection(DEFAULTDIRECTION);
                feederNode.setOrder(12 * i);
                i++;
            }
        }
        context.graph.getCells().forEach(Cell::orderFromFeederOrders);
    }

    private void initVbpcToCell(Context context) {
        context.graph.getCells().stream()
                .filter(cell -> cell.getType() == Cell.CellType.EXTERN)
                .forEach(cell -> addBusNodeSet(cell.getBusNodes(), cell, context));
    }

    private void addBusNodeSet(List<BusNode> busNodes, Cell cell, Context context) {
        VerticalBusConnectionPattern vbcp = new VerticalBusConnectionPattern(context, busNodes);
        VerticalBusConnectionPattern targetBcp = null;
        for (Map.Entry<VerticalBusConnectionPattern, List<Cell>> entry : context.vbcpToCells.entrySet()) {
            VerticalBusConnectionPattern vbcp1 = entry.getKey();
            List<Cell> cells = entry.getValue();
            if (vbcp.isIncludedIn(vbcp1)) {
                targetBcp = vbcp1;
            } else if (vbcp1.isIncludedIn(vbcp)) {
                context.vbcpToCells.remove(vbcp1);
                context.vbcpToCells.put(vbcp, cells);
                targetBcp = vbcp;
            }
            if (targetBcp != null) {
                break;
            }
        }
        if (targetBcp == null) {
            context.vbcpToCells.put(vbcp, new ArrayList<>());
            targetBcp = vbcp;
        }

        if (cell != null) {
            context.vbcpToCells.get(targetBcp).add(cell);
        }
    }

    private void addBusNodeSet(List<BusNode> busNodes, Context context) {
        addBusNodeSet(busNodes, null, context);
    }

    private void organizeWithInternCells(Context context) {
        List<InternCell> structuringInternCells = identifyStructuringCells(context);
        List<InternCell> candidateFlatCell = structuringInternCells.stream()
                .filter(internCell -> internCell.getBusNodes().size() == 2)
                .collect(Collectors.toList());
        context.hChains = chainNodeBusesWithFlatCells(context, candidateFlatCell);
        buildBusToBelongings(context);
        buildConnexClusters(context);
        organizeClusters(context);
    }

    private List<InternCell> identifyStructuringCells(Context context) {
        List<InternCell> structuringInternCells
                = context.graph.getCells().stream()
                .filter(cell -> cell.getType() == Cell.CellType.INTERN || cell.getType() == Cell.CellType.INTERNBOUND)
                .map(InternCell.class::cast)
                .collect(Collectors.toList());

        structuringInternCells.forEach(c -> addBusNodeSet(c.getSideBusNodes(Side.LEFT), context));
        structuringInternCells.forEach(c -> addBusNodeSet(c.getSideBusNodes(Side.RIGHT), context));

        List<InternCell> verticalCells = structuringInternCells.stream()
                .filter(internCell ->
                        new VerticalBusConnectionPattern(context, internCell.getBusNodes()).isIncludedIn(context.vbcpToCells.keySet()) != null)
                .collect(Collectors.toList());
        structuringInternCells.removeAll(verticalCells);
        return structuringInternCells;
    }

    private List<HorizontalChain> chainNodeBusesWithFlatCells(Context context, List<InternCell> flatCells) {
        Map<BusNode, List<InternCell>> bus2flatCells = new HashMap<>();
        flatCells.forEach(cell ->
                cell.getBusNodes().forEach(busNode -> {
                    bus2flatCells.putIfAbsent(busNode, new ArrayList<>());
                    bus2flatCells.get(busNode).add(cell);
                }));

        List<HorizontalChain> chains = new ArrayList<>();

        List<BusNode> busConnectedToFlatCell = bus2flatCells.keySet().stream()
                .sorted(Comparator.comparingInt(bus -> bus2flatCells.get(bus).size()))
                .collect(Collectors.toList());
        //this sorting is to ensure that in most cases (non circular chain) the first bus of a chain is connected to
        // a single flat cell and constitutes one extremity of the chain.

        Set<BusNode> remainingBus = new HashSet<>(context.graph.getNodeBuses());
        remainingBus.removeAll(busConnectedToFlatCell);

        while (!busConnectedToFlatCell.isEmpty()) {
            BusNode bus = busConnectedToFlatCell.get(0);
            HorizontalChain hChain = new HorizontalChain();
            rBuildHChain(hChain, bus, busConnectedToFlatCell, bus2flatCells);
            chains.add(hChain);
        }
        for (BusNode bus : remainingBus) {
            HorizontalChain chain = new HorizontalChain(bus);
            chains.add(chain);
        }

        return chains.stream()
                .sorted(Comparator.comparingInt(hchain -> -hchain.busNodes.size()))
                .collect(Collectors.toList());
    }

    private void rBuildHChain(HorizontalChain hChain,
                              BusNode bus,
                              List<BusNode> busConnectedToFlatCell,
                              Map<BusNode, List<InternCell>> bus2flatCells) {
        hChain.busNodes.add(bus);
        busConnectedToFlatCell.remove(bus);
        for (InternCell cell : bus2flatCells.get(bus)) {
            BusNode otherBus = cell.getBusNodes().stream().filter(busNode -> busNode != bus).findAny().orElse(null);
            if (otherBus != null && busConnectedToFlatCell.contains(otherBus)) {
                rBuildHChain(hChain, otherBus, busConnectedToFlatCell, bus2flatCells);
            }
        }

    }

    private void buildBusToBelongings(Context context) {
        context.vbcpToCells.keySet().forEach(vbcp -> {
            vbcp.busNodeSet.forEach(busNode -> {
                context.busToBelonging.putIfAbsent(busNode, new NodeBelonging(busNode));
                context.busToBelonging.get(busNode).vbcps.add(vbcp);
            });
        });
        context.hChains.forEach(hChain -> hChain.busNodes.forEach(busNode -> {
            context.busToBelonging.putIfAbsent(busNode, new NodeBelonging(busNode));
            context.busToBelonging.get(busNode).hChain = hChain;
        }));
    }

    private void buildConnexClusters(Context context) {
        List<BusNode> remainingBuses = context.graph.getNodeBuses();
        while (!remainingBuses.isEmpty()) {
            context.connectedClusters.add(new ConnectedCluster(context, remainingBuses.get(0), remainingBuses));
        }
    }

    private void organizeClusters(Context context) {
        int firstStructuralPosition = 1;
        int firstFeederOrder = 1;
        for (ConnectedCluster cc : context.connectedClusters) {
            firstStructuralPosition = cc.setStructuralPositionsAndCellOrders(firstStructuralPosition);
            firstFeederOrder = cc.setCellOrders(firstFeederOrder);
        }
    }

    private void newStructuralPosition(Context context) {
        int i = 1;
        for (VerticalBusConnectionPattern vbcp : context.vbcpToCells.keySet()) {
            int j = 1;
            for (BusNode busNode : vbcp.getBusNodeSet()) {
                if (busNode.getStructuralPosition() == null) {
                    busNode.setStructuralPosition(new Position(i, j));
                }
                j++;
            }
            i++;
        }
        for (BusNode bus : context.graph.getNodeBuses()) {
            if (bus.getStructuralPosition() == null) {
                bus.setStructuralPosition(new Position(i, 1));
                i++;
            }
        }
    }

    private class VerticalBusConnectionPattern {
        private Set<BusNode> busNodeSet;

        VerticalBusConnectionPattern(Context context, List<BusNode> busNodees) {
            busNodeSet = new TreeSet<>(Comparator.comparingInt(n -> context.nodeToNb.get(n)));
            busNodeSet.addAll(busNodees);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof VerticalBusConnectionPattern) {
                return busNodeSet.equals(((VerticalBusConnectionPattern) o).busNodeSet);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return busNodeSet.hashCode();
        }

        boolean isIncludedIn(VerticalBusConnectionPattern vbcp2) {
            Iterator<BusNode> it1 = busNodeSet.iterator();
            Iterator<BusNode> it2 = vbcp2.getBusNodeSet().iterator();
            boolean match = true;
            while (it1.hasNext() && match) {
                BusNode n1 = it1.next();
                match = false;
                while (it2.hasNext() && !match) {
                    BusNode n2 = it2.next();
                    match = n2 == n1;
                }
            }
            return match;
        }

        VerticalBusConnectionPattern isIncludedIn(Set<VerticalBusConnectionPattern> busConnectionPatterns) {
            for (VerticalBusConnectionPattern candidateBCPIncluser : busConnectionPatterns) {
                if (isIncludedIn(candidateBCPIncluser)) {
                    return candidateBCPIncluser;
                }
            }
            return null;
        }

        Set<BusNode> getBusNodeSet() {
            return busNodeSet;
        }
    }

    private class HorizontalChain {
        List<BusNode> busNodes;
        int v;

        HorizontalChain() {
            busNodes = new ArrayList<>();
            v = 0;
        }

        HorizontalChain(BusNode busNode) {
            this();
            busNodes.add(busNode);
        }

        int getPosition(BusNode bus) {
            return busNodes.indexOf(bus);
        }

        int getDelatPosition(BusNode bus1, BusNode bus2) {
            return getPosition(bus1) - getPosition(bus2);
        }

        void alignTo(HorizontalChain other) {
            List<BusNode> intersection = new ArrayList<>(other.busNodes);
            intersection.retainAll(busNodes);
            for (int i = 0; i < intersection.size(); i++) {
                for (int j = i + 1; j < intersection.size(); j++) {
                    BusNode bus1 = intersection.get(i);
                    BusNode bus2 = intersection.get(j);
                    if (getDelatPosition(bus1, bus2) * other.getDelatPosition(bus1, bus2) < 0) {
                        Collections.reverse(busNodes);
                        return;
                    }
                }
            }
        }
    }

    private class NodeBelonging {
        BusNode busNode;
        List<VerticalBusConnectionPattern> vbcps;
        HorizontalChain hChain;

        NodeBelonging(BusNode bus) {
            busNode = bus;
            vbcps = new ArrayList<>();
        }
    }

    /**
     * A connexCluster bundle busNodes that are connected through a path of HChains and VerticalBusConnectionPatterns
     */
    private class ConnectedCluster {
        Set<NodeBelonging> buses;
        List<VerticalBusConnectionPattern> vbcps;
        List<HorizontalChain> hChains;
        Context context;

        ConnectedCluster(Context context, BusNode startingNode, List<BusNode> remainingBuses) {
            this.context = context;
            buses = new HashSet<>();
            rBuild(startingNode, remainingBuses);
            vbcps = buses.stream().flatMap(bus -> bus.vbcps.stream()).distinct().collect(Collectors.toList());
            hChains = buses.stream().map(bus -> bus.hChain).distinct().collect(Collectors.toList());
            alignChains();
            sortVbcp();
            organizeHChainsVertically();
        }

        private void rBuild(BusNode startingNode, List<BusNode> remainingBuses) {
            if (remainingBuses.contains(startingNode)) {
                buses.add(context.busToBelonging.get(startingNode));
                remainingBuses.remove(startingNode);
                NodeBelonging nodeBelonging = context.busToBelonging.get(startingNode);
                List<BusNode> busToHandle = nodeBelonging.vbcps.stream()
                        .flatMap(vbcp -> vbcp.busNodeSet.stream()).collect(Collectors.toList());
                busToHandle.addAll(nodeBelonging.hChain.busNodes);
                busToHandle.forEach(busNode -> rBuild(busNode, remainingBuses));
            }
        }

        private void alignChains() {
            for (int i = 0; i < hChains.size(); i++) {
                for (int j = i + 1; j < hChains.size(); j++) {
                    hChains.get(j).alignTo(hChains.get(i));
                }
            }
        }

        private List<HorizontalChain> gethChainsFromVbcp(VerticalBusConnectionPattern vbcp) {
            return vbcp.busNodeSet.stream()
                    .map(busNode -> context.busToBelonging.get(busNode))
                    .map(nodeBelonging -> nodeBelonging.hChain).collect(Collectors.toList());
        }

        private BusNode intersectionNode(Collection<BusNode> busNodes1, Collection<BusNode> busNodes2) {
            List<BusNode> intersectionList = new ArrayList<>(busNodes1);
            intersectionList.retainAll(busNodes2);
            if (intersectionList.isEmpty()) {
                return null;
            } else {
                return intersectionList.get(0);
            }
        }

        // don't use it as a comparator : if 2 vbcp have no commonChains, the result is "equals",
        // but the to vbcp could be far from one another -> necessary to have a dedicated sorting function -> sortVbcp()
        private int compareHVbcp(VerticalBusConnectionPattern vbcp1, VerticalBusConnectionPattern vbcp2) {
            List<HorizontalChain> commonChains = gethChainsFromVbcp(vbcp1);
            commonChains.retainAll(gethChainsFromVbcp(vbcp2));
            if (commonChains.isEmpty()) {
                return 0;
            }
            for (HorizontalChain chain : commonChains) {
                int index1 = chain.getPosition(intersectionNode(chain.busNodes, vbcp1.busNodeSet));
                int index2 = chain.getPosition(intersectionNode(chain.busNodes, vbcp2.busNodeSet));
                if (index1 != -1 && index2 != -1 && index1 != index2) {
                    return index1 - index2;
                }
            }
            return 0;
        }

        private void sortVbcp() {
            if (vbcps.isEmpty()) {
                return;
            }
            List<VerticalBusConnectionPattern> remainingVbcp = new ArrayList<>(vbcps);
            List<VerticalBusConnectionPattern> sortedVbcp = new ArrayList<>();
            sortedVbcp.add(remainingVbcp.get(0));
            remainingVbcp.remove(0);
            while (!remainingVbcp.isEmpty()) {
                Iterator<VerticalBusConnectionPattern> it = remainingVbcp.iterator();
                while (it.hasNext()) {
                    VerticalBusConnectionPattern vbcp = it.next();
                    if (tryToInsertVbcp(vbcp, sortedVbcp)) {
                        remainingVbcp.remove(vbcp);
                        break;
                    }
                }
            }
            vbcps = sortedVbcp;
        }

        private boolean tryToInsertVbcp(VerticalBusConnectionPattern vbcp, List<VerticalBusConnectionPattern> sortedList) {
            for (VerticalBusConnectionPattern iterVbcp : sortedList) {
                int compare = compareHVbcp(vbcp, iterVbcp);
                if (compare != 0) {
                    int position = sortedList.indexOf(iterVbcp);
                    sortedList.add(compare < 0 ? position : position + 1, vbcp);
                    return true;
                }
            }
            return false;
        }

        private void organizeHChainsVertically() {
            for (VerticalBusConnectionPattern vbcp : vbcps) {
                Set<Integer> vBooked = new TreeSet<>(Comparator.comparingInt(Integer::intValue));
                vbcp.busNodeSet.forEach(bus -> vBooked.add(context.busToBelonging.get(bus).hChain.v));
                for (BusNode bus : vbcp.busNodeSet) {
                    HorizontalChain chain = context.busToBelonging.get(bus).hChain;
                    if (chain.v == 0) {
                        int v = firstAvailableIndex(vBooked);
                        chain.v = v;
                        vBooked.add(v);
                    }
                }
            }
        }

        private int firstAvailableIndex(Set<Integer> integerSet) {
            if (integerSet.isEmpty()
                    || integerSet.size() == 1 && integerSet.iterator().next() == 0) {
                return 1;
            }
            int h = 1;
            if (integerSet.iterator().next() == 0) {
                h = 0;
            }
            for (int i : integerSet) {
                if (i == h) {
                    h++;
                } else {
                    return h;
                }
            }
            return h;
        }

        int setStructuralPositionsAndCellOrders(int firstStructuralHPosition) {
            int maxH = firstStructuralHPosition;
            for (HorizontalChain chain : hChains) {
                int structH = firstStructuralHPosition;
                for (BusNode bus : chain.busNodes) {
                    bus.setStructuralPosition(new Position(structH, chain.v));
                    structH++;
                }
                maxH = Math.max(maxH, structH);
            }
            return maxH;
        }

        int setCellOrders(int firstFeederOrder) {
            int feederPosition = firstFeederOrder;
            int cellPos = 0;
            for (VerticalBusConnectionPattern vbcp : vbcps) {
                for (Cell cell : context.vbcpToCells.get(vbcp)) {
                    cell.setDirection(cellPos % 2 == 0 ? Cell.Direction.TOP : Cell.Direction.BOTTOM);
                    cell.setOrder(cellPos);
                    cellPos++;
                    for (FeederNode feederNode : cell.getNodes().stream()
                            .filter(n -> n.getType() == Node.NodeType.FEEDER)
                            .map(FeederNode.class::cast).collect(Collectors.toList())) {
                        feederNode.setOrder(feederPosition);
                        feederPosition++;
                    }
                }
            }
            return feederPosition;
        }
    }
}

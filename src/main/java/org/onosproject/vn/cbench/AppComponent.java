/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.vn.cbench;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.TenantId;
import org.onosproject.incubator.net.virtual.VirtualDevice;
import org.onosproject.incubator.net.virtual.VirtualNetwork;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Path;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.onosproject.net.topology.TopologyService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private static final int DEFAULT_TIMEOUT = 10;
    private static final int DEFAULT_PRIORITY = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualNetworkAdminService vnaService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore flowTableStore;

//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected FlowTableService flowTableService;

    private TopologyService topologyService;
    private PacketService packetService;
    private HostService hostService;
    private FlowRuleService flowRuleService;
    private FlowObjectiveService flowObjectiveService;
    private FlowTableService flowTableService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();


    private ApplicationId appId;
    private NodeId local;
    private NodeId master;
    private DeviceId deviceId;
    private DeviceId vdeviceId;
    private VirtualDevice virtualDevice;


    private int flowPriority = DEFAULT_PRIORITY;

    private int flowTimeout = DEFAULT_TIMEOUT;

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.vn.cbench");
        NetworkId vnetId = NetworkId.networkId(1);
        local = clusterService.getLocalNode().id();
        packetService = vnaService.get(vnetId, PacketService.class);
        flowRuleService = vnaService.get(vnetId, FlowRuleService.class);
        flowTableService = vnaService.get(vnetId, FlowTableService.class);
        Iterator<Device> devices = deviceService.getAvailableDevices().iterator();
        while (vnaService.getVirtualDevices(vnetId).iterator().hasNext()){
            vdeviceId = vnaService.getVirtualDevices(vnetId).iterator().next().id();
            break;
        }
//        virtualDevice = vnaService.getVirtualDevices(vnetId).iterator().next();
//        deviceId = virtualDevice.id();
        while (devices.hasNext()){
            deviceId = devices.next().id();
            break;
        }
        log.info("vdeviceId: {}" + "," + "deviceId: {}", vdeviceId, deviceId);
        List<Port> ports = deviceService.getPorts(deviceId);
        master = mastershipService.getMasterFor(deviceId);
        if(local.equals(master)){
            for(Port port : ports){
                deviceService.changePortState(deviceId, port.number(), true);
            }
            sendPofFlowTable(vdeviceId);

            //hostService = vnaService.get(vnetId, HostService.class);
            //flowObjectiveService = vnaService.get(vnetId, FlowObjectiveService.class);

            log.info("packetService: {}", packetService);
        }
        packetService.addProcessor(processor, PacketProcessor.director(2));
//
//        //topologyService = vnaService.get(vnetId, TopologyService.class);
//
//
        log.info("Started", appId.id());
    }

    @Deactivate
    public void deactivate() {

        //flowRuleService.removeFlowRulesById(appId);
        if(local.equals(master)){

            packetService.removeProcessor(processor);
        }

        processor = null;
        log.info("Stopped");
    }
    private int sendPofFlowTable(DeviceId deviceId) {
        int tableId=0;
        byte smallTableId;
        tableId = flowTableService.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
        OFMatch20 srcIP = new OFMatch20();
        srcIP.setFieldId((short) 1);
        srcIP.setFieldName("srcIp");
        srcIP.setOffset((short) 208);
        srcIP.setLength((short) 32);

        OFMatch20 dstIP = new OFMatch20();
        dstIP.setFieldId((short) 2);
        dstIP.setFieldName("dstIp");
        dstIP.setOffset((short) 240);
        dstIP.setLength((short) 32);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        match20List.add(srcIP);
        match20List.add(dstIP);
        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId((byte)tableId);
        ofFlowTable.setTableName("FirstEntryTable");
        ofFlowTable.setTableSize(1024);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setMatchFieldNum((byte) match20List.size());
        ofFlowTable.setCommand(null);
        ofFlowTable.setKeyLength((short) 64);
        log.info("++++ before build flowtable:" + appId);
        FlowTable flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId)
                .build();
        log.info("++++:" + flowTable.toString());
        log.info("++++ before applyFlowTables");
        flowTableService.applyFlowTables(flowTable);
        log.info("++++ send flow table successfully");
        return tableId;
    }

    private void sendPofFlowRule(DeviceId deviceId,int tableId){
        log.info("tableId : {}", tableId);
        int newFlowEntryId=flowTableService.getNewFlowEntryId(deviceId,tableId);
        log.info("++++ newFlowEntryId; {}",newFlowEntryId);
        log.info("@niubin starting building flowrule");
        int srcIp4Address= Ip4Address.valueOf("10.0.0.1").toInt();
        String srcToHex=Integer.toHexString(srcIp4Address);
        if(srcToHex.length()!=8) {
            String str=new String("0");
            srcToHex=str.concat(srcToHex);
        }
        int dstIp4Address=Ip4Address.valueOf("10.0.0.2").toInt();
        String dstToHex=Integer.toHexString(dstIp4Address);
        if(dstToHex.length()!=8) {
            String str=new String("0");
            dstToHex=str.concat(dstToHex);
        }
        TrafficSelector.Builder pbuilder = DefaultTrafficSelector.builder();
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        entryList.add(Criteria.matchOffsetLength((short) 1, (short) 208, (short) 32, srcToHex, "ffffffff"));
        entryList.add(Criteria.matchOffsetLength((short) 2, (short) 240, (short) 32, dstToHex, "ffffffff"));
        pbuilder.add(Criteria.matchOffsetLength(entryList));
        log.info("++++pbuilder: {}" + pbuilder.toString());
        TrafficTreatment.Builder ppbuilder = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<OFAction>();
        int outPort=0;
        actions.add(DefaultPofActions.output((short) 0, (short) 0, (short) 0, 2).action());
        ppbuilder.add(DefaultPofInstructions.applyActions(actions));
        log.info("++++ppbuilder: {}" + ppbuilder.toString());
        TrafficSelector selector = pbuilder.build();
        TrafficTreatment treatment = ppbuilder.build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forTable(tableId)
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(1)
                .makePermanent()
                .withCookie(newFlowEntryId)
                .build();

        log.info("++++flow rule: {}", flowRule.toString());
        flowRuleService.applyFlowRules(flowRule);

    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        List<OFAction> actions=new ArrayList<>();
        actions.add(DefaultPofActions.output((short)0, (short)0, (short)0, (int)portNumber.toLong()).action());
        context.treatmentBuilder().add(DefaultPofInstructions.applyActions(actions));
        //context.treatmentBuilder().setOutput(portNumber);
        context.send();

    }


    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            log.info("The packet is here process : packetout ready");
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }
            //log.info("====PacketProcessor gets unhandled packetIn packet");
//            InboundPacket pkt = context.inPacket();
//            Ethernet ethPkt = pkt.parsed();
//
//            if (ethPkt == null) {
//                return;
//            }


            // Otherwise forward and be done with
            //installRule(context, PortNumber.portNumber(2));
            //packetOut(context, PortNumber.portNumber(2));
            sendPofFlowRule(vdeviceId,3);
        }
    }

}

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
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.TenantId;
import org.onosproject.incubator.net.virtual.VirtualNetwork;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
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
    protected DeviceService deviceService;

    private TopologyService topologyService;
    private PacketService packetService;
    private HostService hostService;
    private FlowRuleService flowRuleService;
    private FlowObjectiveService flowObjectiveService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();


    private ApplicationId appId;
    private NodeId local;
    private NodeId master;
    private DeviceId deviceId;


    private int flowPriority = DEFAULT_PRIORITY;

    private int flowTimeout = DEFAULT_TIMEOUT;

    @Activate
    public void activate() {
        NetworkId vnetId = NetworkId.networkId(1);
        local = clusterService.getLocalNode().id();
        Iterator<Device> devices = deviceService.getAvailableDevices().iterator();
        while (devices.hasNext()){
            deviceId = devices.next().id();
        }
        master = mastershipService.getMasterFor(deviceId);
        packetService = vnaService.get(vnetId, PacketService.class);
        if(local.equals(master)){

            log.info("packetService: {}", packetService);
            //hostService = vnaService.get(vnetId, HostService.class);
            //flowRuleService = vnaService.get(vnetId, FlowRuleService.class);
            //flowObjectiveService = vnaService.get(vnetId, FlowObjectiveService.class);

            packetService.addProcessor(processor, PacketProcessor.director(2));
        }

        //topologyService = vnaService.get(vnetId, TopologyService.class);
        appId = coreService.registerApplication("org.onosproject.vn.cbench");


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




    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
//        List<OFAction> actions=new ArrayList<>();
//        actions.add(DefaultPofActions.output((short)0, (short)0, (short)0, (int)portNumber.toLong()).action());
//        context.treatmentBuilder().add(DefaultPofInstructions.applyActions(actions));
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }


    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            log.info("The packet is here process : packetout ready");
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
//            if (context.isHandled()) {
//                return;
//            }
            //log.info("====PacketProcessor gets unhandled packetIn packet");
//            InboundPacket pkt = context.inPacket();
//            Ethernet ethPkt = pkt.parsed();
//
//            if (ethPkt == null) {
//                return;
//            }


            // Otherwise forward and be done with
            //installRule(context, PortNumber.portNumber(2));
            packetOut(context, PortNumber.portNumber(2));
        }
    }

}

/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */


package org.openkilda.controller;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkBfdProperties;
import org.openkilda.model.LinkMaxBandwidth;
import org.openkilda.model.LinkParametersDto;
import org.openkilda.model.LinkProps;
import org.openkilda.model.LinkUnderMaintenanceDto;
import org.openkilda.model.SwitchFlowsInfoPerPort;
import org.openkilda.model.SwitchInfo;
import org.openkilda.model.SwitchLocation;
import org.openkilda.model.SwitchMeter;
import org.openkilda.model.SwitchProperty;
import org.openkilda.service.SwitchService;
import org.openkilda.utility.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;

import java.nio.file.AccessDeniedException;
import java.util.List;

/**
 * The Class SwitchController.
 *
 * @author sumitpal.singh
 *
 */

@RestController
@RequestMapping(value = "/api/switch")
public class SwitchController {

    @Autowired
    private SwitchService serviceSwitch;

    @Autowired
    private ActivityLogger activityLogger;

    @Autowired
    private ServerContext serverContext;

    @Autowired
    private MessageUtils messageUtil;

    /**
     * Gets the switches detail.
     *
     * @return the switches detail
     */

    @RequestMapping(value = "/list")
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_SWITCHES })
    public @ResponseBody List<SwitchInfo> getSwitchesDetail(
            @RequestParam(value = "storeConfigurationStatus", required = false)
            final boolean storeConfigurationStatus,
            @RequestParam(value = "controller", required = false)
            final boolean controller) {
        return serviceSwitch.getSwitches(storeConfigurationStatus, controller);
    }

    /**
     * Gets the switches detail.
     *
     * @return the switches detail
     */
    @RequestMapping(value = "/{switchId}")
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_SWITCHES })
    public @ResponseBody SwitchInfo getSwitchDetail(@PathVariable final String switchId,
            @RequestParam(value = "controller", required = false)
            final boolean controller) {
        return serviceSwitch.getSwitch(switchId, controller);
    }


    /**
     * Save or update switch name.
     *
     * @param switchId the switch id
     * @param switchName the switch name
     * @return the SwitchInfo
     */
    @RequestMapping(value = "/name/{switchId}", method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_SWITCH_UPDATE_NAME})
    public @ResponseBody SwitchInfo saveOrUpdateSwitchName(@PathVariable final String switchId,
            @RequestBody final String switchName) {
        if (StringUtil.isNullOrEmpty(switchName)) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("switch_name"));
        }
        activityLogger.log(ActivityType.UPDATE_SWITCH_NAME, switchId);
        return serviceSwitch.saveOrUpdateSwitchName(switchId, switchName);
    }

    /**
     * Gets the links detail.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the links detail
     */
    @RequestMapping(value = "/links", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_ISL })
    public @ResponseBody List<IslLinkInfo> getLinksDetail(@RequestParam(value = "src_switch",
            required = false) final String srcSwitch, @RequestParam(value = "src_port",
            required = false) final String srcPort, @RequestParam(value = "dst_switch",
            required = false) final String dstSwitch, @RequestParam(value = "dst_port",
            required = false) final String dstPort) {
        return serviceSwitch.getIslLinks(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Delete Isl.
     *
     * @param linkParametersDto
     *            the link parameters
     * @return the IslLinkInfo
     */
    @RequestMapping(value = "/links", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.ISL_DELETE_LINK })
    public @ResponseBody List<IslLinkInfo> deleteIsl(@RequestBody final LinkParametersDto linkParametersDto) {
        Long userId = null;
        if (serverContext.getRequestContext() != null) {
            userId = serverContext.getRequestContext().getUserId();
        }
        activityLogger.log(ActivityType.DELETE_ISL, linkParametersDto.toString());
        return serviceSwitch.deleteLink(linkParametersDto, userId);
    }

    /**
     * Updates the links under-maintenance status.
     *
     * @param linkUnderMaintenanceDto the isl maintenance dto
     * @return the isl link info
     */
    @RequestMapping(path = "/links/under-maintenance", method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.ISL_UPDATE_MAINTENANCE})
    public @ResponseBody List<IslLinkInfo> updateLinkUnderMaintenance(
            @RequestBody final LinkUnderMaintenanceDto linkUnderMaintenanceDto) {
        activityLogger.log(ActivityType.ISL_MAINTENANCE, linkUnderMaintenanceDto.toString());
        return serviceSwitch.updateLinkMaintenanceStatus(linkUnderMaintenanceDto);
    }

    /**
     * Gets the link props.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the link props
     */
    @RequestMapping(path = "/link/props", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody LinkProps getLinkProps(
            @RequestParam(value = "src_switch", required = true) final String srcSwitch,
            @RequestParam(value = "src_port", required = true) final String srcPort, @RequestParam(
                    value = "dst_switch", required = true) final String dstSwitch, @RequestParam(
                    value = "dst_port", required = true) final String dstPort) {
        return serviceSwitch.getLinkProps(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Updates the link max bandwidth.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the link max bandwidth
     */
    @RequestMapping(path = "/link/bandwidth", method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.ISL_UPDATE_BANDWIDTH})
    public @ResponseBody LinkMaxBandwidth updateMaxBandwidth(
            @RequestParam(value = "src_switch", required = true) final String srcSwitch,
            @RequestParam(value = "src_port", required = true) final String srcPort, @RequestParam(
                    value = "dst_switch", required = true) final String dstSwitch, @RequestParam(
                    value = "dst_port", required = true) final String dstPort,
                    @RequestBody LinkMaxBandwidth linkMaxBandwidth) {
        activityLogger.log(ActivityType.UPDATE_ISL_BANDWIDTH, linkMaxBandwidth.toString());
        return serviceSwitch.updateLinkBandwidth(srcSwitch, srcPort, dstSwitch, dstPort, linkMaxBandwidth);
    }

    /**
     * Update isl bfd-flag.
     *
     * @param linkParametersDto
     *            the link parameters
     * @return the IslLinkInfo
     */
    @RequestMapping(path = "/link/enable-bfd", method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.ISL_UPDATE_BFD_FLAG})
    public @ResponseBody List<IslLinkInfo> updateEnableBfdFlag(@RequestBody LinkParametersDto linkParametersDto) {
        activityLogger.log(ActivityType.UPDATE_ISL_BFD_FLAG, linkParametersDto.toString());
        return serviceSwitch.updateLinkBfdFlag(linkParametersDto);
    }

    /**
     * Get Link Props.
     *
     * @param keys the link properties
     * @return the link properties string
     */
    @RequestMapping(path = "/link/props", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String updateLinkProps(@RequestBody final List<LinkProps> keys) {
        LinkProps props = (keys != null && !keys.isEmpty()) ? keys.get(0) : null;
        String key =
                props != null ? "Src_SW_" + props.getSrcSwitch() + "\nSrc_PORT_"
                        + props.getSrcPort() + "\nDst_SW_" + props.getDstSwitch() + "\nDst_PORT_"
                        + props.getDstPort() + "\nCost_" + props.getProperty("cost") : "";
        activityLogger.log(ActivityType.ISL_UPDATE_COST, key);
        return serviceSwitch.updateLinkProps(keys);
    }

    /**
     * Get Switch Rules.
     *
     * @param switchId the switch id
     * @return the switch rules
     */
    @RequestMapping(path = "/{switchId}/rules", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_PERMISSION_RULES})
    public @ResponseBody String getSwitchRules(@PathVariable final String switchId) {
        activityLogger.log(ActivityType.SWITCH_RULES, switchId);
        return serviceSwitch.getSwitchRules(switchId);
    }

    /**
     * Configure switch port.
     *
     * @param configuration the configuration
     * @param switchId the switch id
     * @param port the port
     * @return the configuredPort
     */
    @RequestMapping(path = "/{switchId}/{port}/config", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_PORT_CONFIG})
    public @ResponseBody ConfiguredPort configureSwitchPort(
            @RequestBody final PortConfiguration configuration,
            @PathVariable final String switchId, @PathVariable final String port) {
        activityLogger.log(ActivityType.CONFIGURE_SWITCH_PORT, "SW_" + switchId + ", P_" + port);
        return serviceSwitch.configurePort(switchId, port, configuration);
    }

    /**
     * Gets Port flows.
     *
     * @param switchId the switch id
     * @param port the port
     * @return the customers detail
     * @throws AccessDeniedException the access denied exception
     */
    @RequestMapping(path = "/{switchId}/flows", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody ResponseEntity<List<?>> getPortFlows(@PathVariable final String switchId,
            @RequestParam(value = "port", required = false) final String port,
            @RequestParam(value = "inventory", required = false) final boolean inventory) throws AccessDeniedException {
        return serviceSwitch.getPortFlows(switchId, port, inventory);
    }

    /**
     * Gets flows by ports.
     *
     * @param switchId the switch id
     * @param portIds the ports list
     * @return the customers detail
     */
    @RequestMapping(path = "/{switchId}/flows-by-port", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody ResponseEntity<SwitchFlowsInfoPerPort> getFlowsByPort(
            @PathVariable final String switchId,
            @RequestParam(value = "ports", required = false) List<Integer> portIds) {
        return serviceSwitch.getFlowsByPorts(switchId, portIds);
    }

    /**
     * Gets Isl flows.
     *
     * @param srcSwitch the source switch
     * @param srcPort the source port
     * @param dstSwitch the destination switch
     * @param dstPort the destination port
     * @return isl flows exists in the system.
     */
    @RequestMapping(value = "/links/flows", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<FlowInfo> getIslFlows(@RequestParam(name = "src_switch",
            required = true) final String srcSwitch, @RequestParam(name = "src_port",
            required = true) final String srcPort, @RequestParam(name = "dst_switch",
            required = true) final String dstSwitch, @RequestParam(name = "dst_port",
            required = true) final String dstPort) {
        return serviceSwitch.getIslFlows(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Gets the meters detail.
     *
     * @return the meters detail
     */
    @RequestMapping(value = "/meters/{switchId}")
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_SWITCH_METERS})
    public @ResponseBody SwitchMeter getSwitchMeters(@PathVariable final String switchId) {
        return serviceSwitch.getMeters(switchId);
    }

    /**
     * Switch under maintenance.
     *
     * @param switchId the switch id
     * @param switchInfo the switch info
     * @return the SwitchInfo
     */
    @RequestMapping(value = "/under-maintenance/{switchId}", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_SWITCH_MAINTENANCE})
    public @ResponseBody SwitchInfo updateSwitchMaintenanceStatus(
            @PathVariable("switchId") final String switchId,
            @RequestBody final SwitchInfo switchInfo) {
        activityLogger.log(ActivityType.SWITCH_MAINTENANCE, switchId);
        return serviceSwitch.updateMaintenanceStatus(switchId, switchInfo);
    }

    /**
     * Delete Switch.
     *
     * @param switchId the switch id
     * @param force the force delete
     * @return the SwitchInfo
     */
    @RequestMapping(value = "/{switchId}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.SW_SWITCH_DELETE })
    public @ResponseBody SwitchInfo deleteSwitch(@PathVariable final String switchId,
            @RequestParam(name = "force", required = false) boolean force) {
        activityLogger.log(ActivityType.DELETE_SWITCH, switchId);
        return serviceSwitch.deleteSwitch(switchId, force);
    }

    /**
     * Updates the switch port properties.
     *
     * @return the SwitchProperty
     */
    @RequestMapping(value = "/{switchId}/ports/{port}/properties", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.SW_UPDATE_PORT_PROPERTIES })
    public @ResponseBody SwitchProperty updateSwitchPortProperty(@PathVariable final String switchId,
            @PathVariable final String port, @RequestBody SwitchProperty switchProperty) {
        activityLogger.log(ActivityType.UPDATE_SW_PORT_PROPERTIES, switchId);
        return serviceSwitch.updateSwitchPortProperty(switchId, port, switchProperty);
    }

    /**
     * Gets the switch port properties.
     *
     * @return the SwitchProperty
     *
     */
    @RequestMapping(value = "/{switchId}/ports/{port}/properties", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody SwitchProperty getSwitchPortProperty(@PathVariable final String switchId,
            @PathVariable final String port) {
        return serviceSwitch.getSwitchPortProperty(switchId, port);
    }


    /**
     * Updates switch location.
     *
     * @param switchId the switch id
     * @param switchLocation the switch location
     * @return the SwitchInfo
     */
    @RequestMapping(path = "/location/{switchId}", method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.SW_SWITCH_LOCATION_UPDATE})
    public @ResponseBody SwitchInfo updateSwitchLocation(@PathVariable final String switchId,
            @RequestBody final SwitchLocation switchLocation) {
        activityLogger.log(ActivityType.UPDATE_SWITCH_LOCATION, switchId);
        return serviceSwitch.updateSwitchLocation(switchId, switchLocation);
    }

    /**
     * Gets the link BFD properties.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the link Bfd properties
     */
    @RequestMapping(value = "/links/bfd", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody LinkBfdProperties readBfdProperties(@RequestParam(value = "src_switch",
            required = false) final String srcSwitch, @RequestParam(value = "src_port",
            required = false) final String srcPort, @RequestParam(value = "dst_switch",
            required = false) final String dstSwitch, @RequestParam(value = "dst_port",
            required = false) final String dstPort) {
        return serviceSwitch.getLinkBfdProperties(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Updates the link BFD properties.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the link Bfd properties
     */
    @RequestMapping(value = "/links/bfd", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = IConstants.Permission.ISL_UPDATE_BFD_PROPERTIES)
    public @ResponseBody LinkBfdProperties updateBfdProperties(@RequestParam(value = "src_switch",
            required = true) final String srcSwitch, @RequestParam(value = "src_port",
            required = true) final String srcPort, @RequestParam(value = "dst_switch",
            required = true) final String dstSwitch, @RequestParam(value = "dst_port",
            required = true) final String dstPort, @RequestBody(required = true) BfdProperties properties) {
        activityLogger.log(ActivityType.UPDATE_ISL_BFD_PROPERTIES, "Src_SW_" + srcSwitch + "\nSrc_PORT_"
                + srcPort + "\nDst_SW_" + dstSwitch + "\nDst_PORT_"
                + dstPort + "\nProperties_" + properties);
        return serviceSwitch.updateLinkBfdProperties(srcSwitch, srcPort, dstSwitch, dstPort, properties);
    }

    /**
     * Delete link BFD.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     */
    @RequestMapping(value = "/links/bfd", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = IConstants.Permission.ISL_DELETE_BFD)
    public @ResponseBody String deleteLinkBfd(@RequestParam(value = "src_switch",
            required = true) final String srcSwitch, @RequestParam(value = "src_port",
            required = true) final String srcPort, @RequestParam(value = "dst_switch",
            required = true) final String dstSwitch, @RequestParam(value = "dst_port",
            required = true) final String dstPort) {
        activityLogger.log(ActivityType.DELETE_ISL_BFD, "Src_SW_" + srcSwitch + "\nSrc_PORT_"
                + srcPort + "\nDst_SW_" + dstSwitch + "\nDst_PORT_" + dstPort);
        return serviceSwitch.deleteLinkBfd(srcSwitch, srcPort, dstSwitch, dstPort);
    }
}

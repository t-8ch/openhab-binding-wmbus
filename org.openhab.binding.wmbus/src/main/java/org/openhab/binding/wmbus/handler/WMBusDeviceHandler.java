/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.wmbus.handler;

import static org.openhab.binding.wmbus.WMBusBindingConstants.PROPERTY_DEVICE_ID;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.wmbus.WMBusDevice;
import org.openhab.binding.wmbus.internal.WMBusException;
import org.openmuc.jmbus.DataRecord;
import org.openmuc.jmbus.DecodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WMBusDeviceHandler} class defines abstract WMBusDeviceHandler
 *
 * @author Hanno - Felix Wagner - Initial contribution
 * @author Łukasz Dywicki - Added possibility to customize message parsing.
 */

public abstract class WMBusDeviceHandler<T extends WMBusDevice> extends BaseThingHandler
        implements WMBusMessageListener {
    private final Logger logger = LoggerFactory.getLogger(WMBusDeviceHandler.class);
    protected String deviceId;
    private WMBusBridgeHandler bridgeHandler;
    protected T wmbusDevice;

    public WMBusDeviceHandler(Thing thing) {
        super(thing);
        logger.debug("Created new handler for thing {}", thing.getUID());
    }

    // entry point - gets device here
    @Override
    public void onNewWMBusDevice(WMBusAdapter adapter, WMBusDevice wmBusDevice) {
        logger.trace("onNewWMBusDevice(): is it me?");
        if (wmBusDevice.getDeviceId().equals(deviceId)) {
            logger.trace("onNewWMBusDevice(): yes it's me");
            logger.trace("onNewWMBusDevice(): updating status to online");
            updateStatus(ThingStatus.ONLINE);
            logger.trace("onNewWMBusDevice(): calling onChangedWMBusDevice()");
            onChangedWMBusDevice(adapter, wmBusDevice);
        }
        logger.trace("onNewWMBusDevice(): no");
    }

    @Override
    public void onChangedWMBusDevice(WMBusAdapter adapter, WMBusDevice receivedDevice) {
        logger.trace("onChangedWMBusDevice(): is it me?");
        if (receivedDevice.getDeviceId().equals(deviceId)) {
            logger.trace("onChangedWMBusDevice(): yes");
            // in between the good messages, there are messages with invalid values -> filter these out
            if (!checkMessage(receivedDevice)) {
                logger.trace("onChangedWMBusDevice(): this is a malformed message, ignoring this message");
            } else {
                try {
                    wmbusDevice = parseDevice(receivedDevice);
                    if (wmbusDevice != null) {
                        logger.trace("onChangedWMBusDevice(): inform all channels to refresh");
                        triggerRefresh();
                    }
                } catch (DecodingException e) {
                    // FIXME decoding exception should not be necessary over time
                    logger.debug("onChangedWMBusDevice(): could not decode frame", e);
                }
            }
        } else {
            logger.trace("onChangedWMBusDevice(): no");
        }
        logger.trace("onChangedWMBusDevice(): return");
    }

    protected void triggerRefresh() {
        for (Channel curChan : getThing().getChannels()) {
            handleCommand(curChan.getUID(), RefreshType.REFRESH);
        }
    }

    protected State convertRecordData(DataRecord record) {
        switch (record.getDataValueType()) {
            case LONG:
            case DOUBLE:
                return new DecimalType(record.getScaledDataValue());
            case DATE:
                return convertDate(record.getDataValue());
            case STRING:
            case BCD:
            case NONE:
                return new StringType(record.getDataValue().toString());
        }
        return null;
    }

    protected DateTimeType convertDate(Object input) {
        if (input instanceof Date) {
            Date date = (Date) input;
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            zonedDateTime.truncatedTo(ChronoUnit.SECONDS); // throw away millisecond value to avoid, eg. _previous_date
                                                           // changed from 2018-02-28T00:00:00.353+0100 to
                                                           // 2018-02-28T00:00:00.159+0100
            return new DateTimeType(zonedDateTime);
        }
        return null;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing handler.");
        updateStatus(ThingStatus.UNKNOWN);

        Configuration config = getConfig();
        deviceId = (String) config.getProperties().get(PROPERTY_DEVICE_ID);
        try {
            wmbusDevice = getDevice();
            if (wmbusDevice != null) {
                initialize(wmbusDevice);
            }
        } catch (WMBusException e) {
            logger.error("Could not obtain Wireless M-Bus device information", e);
        }
    }

    protected void initialize(T device) {
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        logger.debug("Disposing handler.");
        this.deviceId = null;
        this.wmbusDevice = null;
    }

    protected synchronized WMBusBridgeHandler getBridgeHandler() throws WMBusException {
        logger.trace("getBridgeHandler() begin");
        if (bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof WMBusBridgeHandler) {
                bridgeHandler = (WMBusBridgeHandler) handler;
                bridgeHandler.registerWMBusMessageListener(this);
            } else {
                return null;
            }
        }
        logger.trace("getBridgeHandler() returning bridgehandler");
        return bridgeHandler;
    }

    protected T getDevice() throws WMBusException {
        logger.trace("getDevice() begin");
        WMBusBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.debug("Device handler is not linked with bridge, skipping call");
            return null;
        }

        logger.trace("Lookup known devices by identifier {}", deviceId);
        WMBusDevice device = bridgeHandler.getDeviceById(deviceId);
        if (device != null) {
            logger.trace("Found device matching given id {}, {}", deviceId, device);
            try {
                return parseDevice(device);
            } catch (DecodingException e) {
                logger.trace("Unable to parse received message {}", device, e);
                return null;
            }
        }

        logger.trace("Couldn't find device matching id {}", deviceId);
        return null;
    }

    boolean checkMessage(WMBusDevice receivedDevice) {
        return true;
    }

    @SuppressWarnings("unchecked")
    protected T parseDevice(WMBusDevice device) throws DecodingException {
        device.decode();
        return (T) device;
    }

}

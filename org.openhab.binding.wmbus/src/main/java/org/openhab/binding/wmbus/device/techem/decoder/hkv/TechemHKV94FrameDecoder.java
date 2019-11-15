/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmbus.device.techem.decoder.hkv;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.openhab.binding.wmbus.WMBusDevice;
import org.openhab.binding.wmbus.device.techem.Record;
import org.openhab.binding.wmbus.device.techem.TechemBindingConstants;
import org.openhab.binding.wmbus.device.techem.TechemHeatCostAllocator;
import org.openmuc.jmbus.SecondaryAddress;

import tec.uom.se.quantity.Quantities;

public class TechemHKV94FrameDecoder extends AbstractTechemHKVFrameDecoder {

    public TechemHKV94FrameDecoder() {
        super(TechemBindingConstants._68TCH148255_8, true);
    }

    @Override
    protected TechemHeatCostAllocator decode(WMBusDevice device, SecondaryAddress address, byte[] buffer) {
        int offset = address.asByteArray().length + 2;
        int coding = buffer[offset] & 0xFF;

        if (coding == 0xA2) {
            LocalDateTime lastReading = parseLastDate(buffer, offset + 2);
            float lastValue = parseBigEndianInt(buffer, offset + 4);
            LocalDateTime currentDate = parseCurrentDate(buffer, offset + 6);
            float currentValue = parseBigEndianInt(buffer, offset + 9);

            List<Record<?>> records = new ArrayList<>();
            records.add(new Record<>(Record.Type.CURRENT_VOLUME, currentValue));
            records.add(new Record<>(Record.Type.CURRENT_READING_DATE, currentDate));
            records.add(new Record<>(Record.Type.PAST_VOLUME, lastValue));
            records.add(new Record<>(Record.Type.PAST_READING_DATE, lastReading));
            records.add(new Record<>(Record.Type.RSSI, device.getOriginalMessage().getRssi()));

            if (reportsTemperature) {
                float temp1 = parseTemperature(buffer, offset + 11);
                float temp2 = parseTemperature(buffer, offset + 13);
                records.add(new Record<>(Record.Type.ROOM_TEMPERATURE, Quantities.getQuantity(temp1, SIUnits.CELSIUS)));
                records.add(
                        new Record<>(Record.Type.RADIATOR_TEMPERATURE, Quantities.getQuantity(temp2, SIUnits.CELSIUS)));
            }

            return new TechemHeatCostAllocator(device.getOriginalMessage(), device.getAdapter(), records);
        }

        return null;
    }

}
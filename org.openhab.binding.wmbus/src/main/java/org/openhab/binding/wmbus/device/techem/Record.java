/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmbus.device.techem;

public class Record<T> {

    public enum Type {
        CURRENT_VOLUME,
        CURRENT_READING_DATE,
        PAST_VOLUME,
        PAST_READING_DATE,
        ROOM_TEMPERATURE,
        RADIATOR_TEMPERATURE,
        RSSI
    }

    private final Type type;
    private final T value;

    public Record(Type type, T value) {
        this.type = type;
        this.value = value;
    }

    public final Type getType() {
        return this.type;
    }

    public final T getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return "Record [" + type + ", " + value + "]";
    }
}

/**
 * cdp4j - Chrome DevTools Protocol for Java
 * Copyright © 2017, 2018 WebFolder OÜ (support@webfolder.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.webfolder.cdp.event.network;

import io.webfolder.cdp.annotation.Domain;
import io.webfolder.cdp.annotation.EventName;

/**
 * Fired when EventSource message is received
 */
@Domain("Network")
@EventName("eventSourceMessageReceived")
public class EventSourceMessageReceived {
    private String requestId;

    private Double timestamp;

    private String eventName;

    private String eventId;

    private String data;

    /**
     * Request identifier.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Request identifier.
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Timestamp.
     */
    public Double getTimestamp() {
        return timestamp;
    }

    /**
     * Timestamp.
     */
    public void setTimestamp(Double timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Message type.
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Message type.
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Message identifier.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Message identifier.
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Message content.
     */
    public String getData() {
        return data;
    }

    /**
     * Message content.
     */
    public void setData(String data) {
        this.data = data;
    }
}

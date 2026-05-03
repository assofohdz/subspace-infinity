// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.toggles;

/**
 * Whether ships are allowed to receive 'X-Radar' 0=no 1=yes 2=yes/start-with
 *
 * @author Asser Fahrenholz
 */
public class XRadarStatus {

    private final int status;

    public XRadarStatus() {
        this(0);
    }

    public XRadarStatus(final int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

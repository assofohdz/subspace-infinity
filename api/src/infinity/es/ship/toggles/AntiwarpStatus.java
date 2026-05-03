// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.toggles;

/**
 * Whether ships are allowed to receive 'Anti-Warp' 0=no 1=yes 2=yes/start-with
 *
 * @author Asser Fahrenholz
 */
public class AntiwarpStatus {

    private final int status;

    public AntiwarpStatus() {
        this(0);
    }

    public AntiwarpStatus(final int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.util;

public class InfinityRunTimeException extends RuntimeException {

    public InfinityRunTimeException(final String message) {
        super(message);
    }

    public InfinityRunTimeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public InfinityRunTimeException(final Throwable cause) {
        super(cause);
    }

    public InfinityRunTimeException() {
        super();
    }
}

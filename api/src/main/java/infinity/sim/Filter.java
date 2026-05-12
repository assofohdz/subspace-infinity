// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

/** Generic boolean filter interface. */
public interface Filter {

    /** Always-allow filter. */
    Filter DEFAULT_FILTER = new Filter() {
        @Override
        public boolean isAllowed(final Filter filter) {
            return true;
        }

        @Override
        public String toString() {
            return "DefaultFilter[]";
        }
    };

    /** Returns true if both filters allow interaction; null + cross-type behaviour is implementation-defined. */
    boolean isAllowed(Filter filter);
}

// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

/**
 * Generic boolean filter interface.
 *
 * @author AFahrenholz
 */
public interface Filter {

    /**
     * The default filter which always returns true
     */
    Filter DEFAULT_FILTER = new Filter() {
        /*
         * (non-Javadoc)
         *
         * @see org.dyn4j.collision.Filter#isAllowed(org.dyn4j.collision.Filter)
         */
        @Override
        public boolean isAllowed(final Filter filter) {
            // always return true
            return true;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "DefaultFilter[]";
        }
    };

    /**
     * Returns true if the given {@link Filter} and this {@link Filter} allow the
     * objects to interact.
     * <p>
     * If the given {@link Filter} is not the same type as this {@link Filter} its
     * up to the implementing class to specify the behavior.
     * <p>
     * In addition, if the given {@link Filter} is null its up to the implementing
     * class to specify the behavior.
     *
     * @param filter the other {@link Filter}
     * @return boolean
     */
    boolean isAllowed(Filter filter);
}

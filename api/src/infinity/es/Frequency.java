// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component holding the team frequency this entity belongs to.
 *
 * @author Asser
 */
public class Frequency implements EntityComponent {

    private final int freq;

    public Frequency() {
        this(0);
    }

    public Frequency(final int freq) {
        this.freq = freq;
    }

    public int getFrequency() {
        return freq;
    }
}

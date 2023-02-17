/*
 * $Id$
 * 
 * Copyright (c) 2021, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.ai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.simsilica.mathd.filter.*;


/**
 *  Provides the tracked AI-related stats for debug views or other
 *  debug data processing.
 *
 *  @author    Paul Speed
 */
public class MobStats {

    public static final String STAT_FRAME_TIME = "frameTime";
    public static final String STAT_ACTIVE_MOB_COUNT = "activeMobCount";
    
    private Map<String, Number> stats = new ConcurrentHashMap<>();

    public MobStats() {
        stats.put(STAT_FRAME_TIME, 0);
        stats.put(STAT_ACTIVE_MOB_COUNT, 0);    
    }

    public Number get( String name ) {
        Number num = stats.get(name);
        if( num == null ) {
            throw new IllegalArgumentException("No such stat:" + name);
        }
        return num;
    }
 
    public double getDouble( String name ) {
        return get(name).doubleValue(); 
    }

    public long getLong( String name ) {
        return get(name).longValue(); 
    }
    
    // local methods available to mob system for setting the values
    protected Stat getStat( String name ) {
        return new Stat(name, null);
    }
    
    protected Stat getStat( String name, Filterd filter ) {
        return new Stat(name, filter);
    }
    
    public class Stat {
        private String target;
        private Filterd filter;
        
        public Stat( String target, Filterd filter ) {
            this.target = target;
            this.filter = filter;
        }
        
        public void updateValue( double d ) {
            if( filter != null ) {
                filter.addValue(d);
                stats.put(target, filter.getFilteredValue());
            } else {
                stats.put(target, d);
            }
        }        
    } 
}
